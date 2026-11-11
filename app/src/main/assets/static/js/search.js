(function() {
const J = window.JecWeb;
J.allResults = [];
J.displayedResults = [];
J.renderedCount = 0;
J.isLoadingMore = false;
J.observer = null;
J.activeFilters = new Set();
J.searching = false;
J.currentOffset = 0;
J.totalResults = 0;

J.clearResults = function() {
    J.allResults = [];
    J.displayedResults = [];
    J.renderedCount = 0;
    J.currentOffset = 0;
    J.totalResults = 0;
    const res = document.getElementById('results');
    if (res) res.innerHTML = '';
    const filters = document.getElementById('filtersContainer');
    if (filters) filters.style.display = 'none';
    const histRow = document.getElementById('historyRow');
    if (histRow) histRow.style.display = 'none';
    const status = document.getElementById('status');
    if (status) status.textContent = '';
    const loadMore = document.getElementById('loadMoreBtn');
    if (loadMore) loadMore.style.display = 'none';
    J.activeFilters.clear();
    document.querySelectorAll('.filter-chip').forEach(c => c.classList.remove('active'));
};

J.doSearch = function(append) {
    const q = document.getElementById('searchInput').value.trim();
    if (q.length === 0) {
        if (J.showCatalog) J.showCatalog();
        J.clearResults();
        return;
    }
    if (J.searching) return;
    const searchBtn = document.getElementById('searchBtn');
    searchBtn.disabled = true;
    searchBtn.textContent = '⏳';
    if (!append) {
        J.clearResults();
        document.getElementById('status').textContent = 'Поиск...';
        if (J.hideCatalog) J.hideCatalog();
    }

    const selectedLi = document.querySelector('#typeSelect .select-items li.selected');
    const type = selectedLi ? selectedLi.dataset.value : '-1';
    const limit = 30;
    const offset = append ? J.currentOffset : 0;

    startBackgroundServices();

    fetch('/index.php', {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: `action=search&query=${encodeURIComponent(q)}&type=${type}&limit=${limit}&offset=${offset}`
    })
    .then(r => r.json())
    .then(data => {
        if (data && data.error) {
            document.getElementById('status').textContent = data.error;
            return;
        }
        const items = Array.isArray(data) ? data : (data.items || []);
        J.totalResults = data.total || items.length;
        if (!append) {
            J.allResults = items;
        } else {
            J.allResults = J.allResults.concat(items);
        }
        J.currentOffset = J.allResults.length;
        J.applyFilters();
        if (typeof J.loadHistory === 'function') J.loadHistory();
        document.getElementById('filtersContainer').style.display = 'flex';
        document.getElementById('historyRow').style.display = 'flex';
        document.getElementById('status').textContent = '';
        document.getElementById('loadMoreBtn').style.display = J.currentOffset < J.totalResults ? 'block' : 'none';
    })
    .catch(err => {
        console.error('Search error:', err);
        document.getElementById('status').textContent = 'Ошибка сети';
    })
    .finally(() => {
        searchBtn.disabled = false;
        searchBtn.textContent = '🔍';
        J.searching = false;
    });
};

document.getElementById('loadMoreBtn').addEventListener('click', function() {
    J.doSearch(true);
});

document.getElementById('searchInput').addEventListener('input', function() {
    if (this.value.trim() === '') {
        if (J.showCatalog) J.showCatalog();
        J.clearResults();
    }
});

// Голосовой поиск (без изменений)
(function() {
    const searchBtn = document.getElementById('searchBtn');
    const searchInput = document.getElementById('searchInput');
    let pressTimer = null;
    let isRecording = false;

    function startRecording() {
        if (!recognition) { toast('Голосовой поиск не поддерживается', 'error'); return; }
        if (isRecording) return;
        isRecording = true;
        searchBtn.classList.add('recording');
        recognition.start();
    }

    function stopRecording() {
        if (recognition && isRecording) recognition.stop();
        isRecording = false;
        searchBtn.classList.remove('recording');
    }

    searchBtn.addEventListener('mousedown', (e) => {
        e.preventDefault();
        pressTimer = setTimeout(() => { if (J.settings.voice_search) startRecording(); pressTimer = null; }, 1200);
    });
    searchBtn.addEventListener('mouseup', (e) => {
        e.preventDefault();
        if (pressTimer) { clearTimeout(pressTimer); pressTimer = null; if (!isRecording) J.doSearch(); }
        else if (isRecording) stopRecording();
    });
    searchBtn.addEventListener('mouseleave', () => { if (pressTimer) { clearTimeout(pressTimer); pressTimer = null; } if (isRecording) stopRecording(); });

    searchBtn.addEventListener('touchstart', (e) => {
        e.preventDefault();
        pressTimer = setTimeout(() => { if (J.settings.voice_search) startRecording(); pressTimer = null; }, 1200);
    }, {passive: false});
    searchBtn.addEventListener('touchend', (e) => {
        e.preventDefault();
        if (pressTimer) { clearTimeout(pressTimer); pressTimer = null; if (!isRecording) J.doSearch(); }
        else if (isRecording) stopRecording();
    });
    searchBtn.addEventListener('touchcancel', () => { if (pressTimer) { clearTimeout(pressTimer); pressTimer = null; } if (isRecording) stopRecording(); });

    searchInput.addEventListener('keypress', (e) => { if(e.key==='Enter') J.doSearch(); });

    let recognition = null;
    if ('SpeechRecognition' in window || 'webkitSpeechRecognition' in window) {
        const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
        recognition = new SpeechRecognition();
        recognition.lang = 'ru-RU';
        recognition.continuous = false;
        recognition.interimResults = false;
        recognition.onresult = function(event) {
            searchInput.value = event.results[0][0].transcript;
            stopRecording();
        };
        recognition.onerror = () => { stopRecording(); toast('Ошибка распознавания', 'error'); };
        recognition.onend = () => stopRecording();
    }
})();

document.querySelectorAll('.filter-chip').forEach(chip => {
    chip.addEventListener('click', function() {
        const filter = this.dataset.filter;
        if (J.activeFilters.has(filter)) {
            J.activeFilters.delete(filter);
            this.classList.remove('active');
        } else {
            J.activeFilters.add(filter);
            this.classList.add('active');
        }
        J.applyFilters();
    });
});

J.applyFilters = function() {
    let items = J.allResults;
    if (J.activeFilters.has('1080p')) items = items.filter(i => i.quality >= 1080);
    if (J.activeFilters.has('2025')) items = items.filter(i => {
        const year = parseInt(i.relased);
        return !isNaN(year) && year >= 2025;
    });
    if (J.activeFilters.has('small')) items = items.filter(i => i.size > 0 && i.size <= 5 * 1024 * 1024 * 1024);
    if (J.activeFilters.has('seeds50')) items = items.filter(i => (i.sid || 0) >= 50);
    J.displayedResults = items;
    J.renderedCount = 0;
    document.getElementById('results').innerHTML = '';
    J.renderBatch();
};

J.renderBatch = function() {
    if (J.renderedCount >= J.displayedResults.length) return;
    const container = document.getElementById('results');
    const end = Math.min(J.renderedCount + 50, J.displayedResults.length);
    const fragment = document.createDocumentFragment();
    for (let i = J.renderedCount; i < end; i++) {
        const item = J.displayedResults[i];
        const title = item.title || 'Без названия';
        const year = item.relased || '';
        const quality = item.quality ? item.quality + 'p' : '';
        const videotype = item.videotype ? item.videotype.toUpperCase() : '';
        const size = item.sizeName || (item.size ? (item.size/1073741824).toFixed(1) + ' GB' : '?');
        const seeds = item.sid || 0;
        const peers = item.pir || 0;
        const magnet = item.magnet || '';

        const card = document.createElement('div');
        card.className = 'result-item';

        const titleDiv = document.createElement('div');
        titleDiv.className = 'result-title';
        titleDiv.textContent = title;
        card.appendChild(titleDiv);

        const detailsDiv = document.createElement('div');
        detailsDiv.className = 'result-details';
        if (year) { const s = document.createElement('span'); s.textContent = '📅 ' + year; detailsDiv.appendChild(s); }
        if (quality) { const b = document.createElement('span'); b.className = 'quality-badge'; b.textContent = quality; detailsDiv.appendChild(b); }
        if (videotype) { const b = document.createElement('span'); b.className = 'quality-badge'; b.textContent = videotype; detailsDiv.appendChild(b); }
        card.appendChild(detailsDiv);

        const metaDiv = document.createElement('div');
        metaDiv.className = 'result-meta';
        ['📦 ' + size, '🔼 ' + seeds, '🔽 ' + peers].forEach(txt => {
            const sp = document.createElement('span'); sp.textContent = txt; metaDiv.appendChild(sp);
        });
        card.appendChild(metaDiv);

        const actionsDiv = document.createElement('div');
        actionsDiv.className = 'result-actions';

        const btnPhone = document.createElement('button');
        btnPhone.className = 'btn btn-phone'; btnPhone.textContent = '▶ Телефон';
        btnPhone.dataset.magnet = magnet; btnPhone.dataset.title = title;
        actionsDiv.appendChild(btnPhone);

        const btnTV = document.createElement('button');
        btnTV.className = 'btn btn-tv'; btnTV.textContent = '📺 ТВ';
        btnTV.dataset.magnet = magnet; btnTV.dataset.title = title;
        actionsDiv.appendChild(btnTV);

        const btnBookmark = document.createElement('button');
        btnBookmark.className = 'btn-bookmark'; btnBookmark.textContent = '🔖';
        btnBookmark.dataset.magnet = magnet; btnBookmark.dataset.title = title;
        actionsDiv.appendChild(btnBookmark);

        card.appendChild(actionsDiv);
        fragment.appendChild(card);
    }
    container.appendChild(fragment);
    J.renderedCount = end;
    J.setupObserver();
};

J.setupObserver = function() {
    if (J.observer) J.observer.disconnect();
    const sentinel = document.getElementById('sentinel');
    if (!sentinel) return;
    J.observer = new IntersectionObserver((entries) => {
        if (entries[0].isIntersecting && !J.isLoadingMore && J.renderedCount < J.displayedResults.length) {
            J.isLoadingMore = true;
            J.renderBatch();
            J.isLoadingMore = false;
        }
    }, { rootMargin: '200px' });
    J.observer.observe(sentinel);
};

document.getElementById('results').addEventListener('click', function(e) {
    const target = e.target;
    const magnet = target.dataset.magnet;
    const title = target.dataset.title || 'Без названия';
    if (!magnet) return;
    if (target.classList.contains('btn-phone')) {
        if (J.lastPhoneHash) stopHash(J.lastPhoneHash);
        fetch('/index.php', { method:'POST', headers:{'Content-Type':'application/x-www-form-urlencoded'}, body: `action=play_local&magnet=${encodeURIComponent(magnet)}&title=${encodeURIComponent(title)}` })
        .then(r => r.json()).then(d => {
            if(d.error) toast(d.error, 'error');
            else {
                if (d.hash) addActiveHash(d.hash, 'phone');
                if (d.video_url) J.openPlayer(d.video_url);
                else toast('Запущено', 'success');
            }
        });
    } else if (target.classList.contains('btn-tv')) {
        if (J.lastTVHash) stopHash(J.lastTVHash);
        fetch('/index.php', { method:'POST', headers:{'Content-Type':'application/x-www-form-urlencoded'}, body: `action=play_tv&magnet=${encodeURIComponent(magnet)}&title=${encodeURIComponent(title)}` })
        .then(r => r.json()).then(d => {
            if(d.error) toast(d.error, 'error');
            else {
                if (d.hash) addActiveHash(d.hash, 'tv');
                if(d.m3u) navigator.clipboard.writeText(d.m3u).then(() => toast('M3U скопирован!', 'success'));
            }
        });
    } else if (target.classList.contains('btn-bookmark')) {
        fetch('/index.php', { method:'POST', headers:{'Content-Type':'application/x-www-form-urlencoded'}, body: `action=add_bookmark&magnet=${encodeURIComponent(magnet)}&title=${encodeURIComponent(title)}` })
        .then(r => r.json()).then(d => { if(d.ok) toast('Добавлено в закладки', 'success'); });
    }
});
})();
