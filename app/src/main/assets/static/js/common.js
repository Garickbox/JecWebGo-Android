(function() {
window.JecWeb = window.JecWeb || {};
const J = window.JecWeb;
J.settings = {};
J.currentTab = 'search';
J.activeHashes = [];
J.lastPhoneHash = null;
J.lastTVHash = null;
J.historyExpanded = false;
J.historyLoaded = false;
J.playerFadeTimer = null;
J.magnetsTimer = null;
J.statusTimer = null;
J.ws = null;
J.pendingStatus = null;
J._authToken = sessionStorage.getItem('jecweb_auth') || '';
J.aiSoundEnabled = true;
J.aiSessionID = null;
J.aiAbortController = null;
J.aiActive = false;
J.lastRecommendationMovies = null;
J.activeDescMovieTitle = '';
J.recommendMessageShown = false;

const GENRE_LIST = [
    "Боевики", "Комедии", "Фантастика", "Ужасы", "Триллеры", "Драмы",
    "Мелодрамы", "Приключения", "Детективы", "Исторические", "Военные",
    "Спорт", "Документальные", "Семейные", "Фэнтези", "Мистика",
    "Криминал", "Вестерны", "Аниме", "Мультфильмы", "Музыкальные",
    "Биографии", "Нуар", "Постапокалипсис"
];

function randomGenres(count) {
    const shuffled = [...GENRE_LIST];
    for (let i = shuffled.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]];
    }
    return shuffled.slice(0, count);
}

const originalFetch = window.fetch;
window.fetch = function(url, options = {}) {
    if (typeof url === 'string' && url.includes('/index.php')) {
        options.headers = options.headers || {};
        if (J._authToken) {
            options.headers['X-Auth'] = J._authToken;
        }
    }
    return originalFetch(url, options);
};

window.toast = function(msg, type = 'info') {
    let container = document.querySelector('.toast-container');
    if (!container) {
        container = document.createElement('div');
        container.className = 'toast-container';
        document.body.appendChild(container);
    }
    const el = document.createElement('div');
    el.className = `toast ${type}`;
    el.textContent = msg;
    container.appendChild(el);
    setTimeout(() => { el.remove(); }, 4000);
};

window.addActiveHash = function(hash, type = 'phone') {
    if (hash && !J.activeHashes.includes(hash)) {
        J.activeHashes.push(hash);
        if (type === 'phone') J.lastPhoneHash = hash;
        else if (type === 'tv') J.lastTVHash = hash;
    }
};
window.removeActiveHash = function(hash) {
    J.activeHashes = J.activeHashes.filter(h => h !== hash);
    if (J.lastPhoneHash === hash) J.lastPhoneHash = null;
    if (J.lastTVHash === hash) J.lastTVHash = null;
};
window.stopHash = function(hash) {
    if (!hash) return;
    fetch('/index.php', {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: 'action=stop&hash=' + encodeURIComponent(hash)
    }).catch(() => {});
    removeActiveHash(hash);
};
window.addEventListener('beforeunload', () => {
    J.activeHashes.forEach(hash => {
        navigator.sendBeacon('/index.php', new URLSearchParams({ action: 'stop', hash: hash, ip: '127.0.0.1' }));
    });
});

window.applyTheme = function() {
    const theme = J.settings.theme || 'dark';
    if (theme === 'oled') {
        document.documentElement.style.setProperty('--bg', '#000');
        document.documentElement.style.setProperty('--surface', '#111');
    } else {
        document.documentElement.style.setProperty('--bg', '#000');
        document.documentElement.style.setProperty('--surface', '#1a1a1a');
    }
};

fetch('/index.php', {
    method:'POST',
    headers:{'Content-Type':'application/x-www-form-urlencoded'},
    body:'action=get_settings'
})
.then(r => r.json())
.then(data => {
    J.settings = data.settings || {};
    J.settings._csrf = data._csrf;
    J.settings.ws_port = data.ws_port;
    applyTheme();
    if (typeof J.loadHistory === 'function') J.loadHistory();
    J.historyLoaded = true;
    if (data.has_password && !J._authToken) {
        const userPass = prompt('Введите пароль JecWeb:');
        if (userPass) {
            J._authToken = userPass;
            sessionStorage.setItem('jecweb_auth', userPass);
        } else {
            toast('Пароль не введён', 'error');
        }
    }
    if (typeof J.initWS === 'function') J.initWS();
});

window.startBackgroundServices = function() {
    if (!J.historyLoaded && typeof J.loadHistory === 'function') {
        J.loadHistory();
        J.historyLoaded = true;
    }
};

document.addEventListener('DOMContentLoaded', function() {
    const select = document.getElementById('typeSelect');
    if (!select) return;
    const selected = select.querySelector('.select-selected');
    const items = select.querySelectorAll('.select-items li');
    selected.addEventListener('click', function(e) {
        e.stopPropagation();
        select.classList.toggle('open');
    });
    items.forEach(item => {
        item.addEventListener('click', function(e) {
            e.stopPropagation();
            const value = this.dataset.value;
            const text = this.textContent;
            selected.textContent = text;
            items.forEach(li => li.classList.remove('selected'));
            this.classList.add('selected');
            select.classList.remove('open');
            const q = document.getElementById('searchInput').value.trim();
            if (q.length >= 2 && typeof J.doSearch === 'function') J.doSearch();
        });
    });
    document.addEventListener('click', function(e) {
        if (!select.contains(e.target)) { select.classList.remove('open'); }
    });

    const mascot = document.getElementById('mascot');
    const aiPanel = document.getElementById('aiPanel');
    const aiModal = document.getElementById('aiModal');
    const searchInput = document.getElementById('searchInput');
    const aiTopTitle = document.getElementById('aiTopTitle');
    const aiTopDesc = document.getElementById('aiTopDesc');
    const aiDescText = document.getElementById('aiDescText');
    const descSearchBtn = document.getElementById('aiDescSearchBtn');
    const descCloseBtn = document.getElementById('aiDescCloseBtn');

    if (!mascot || !aiPanel || !aiModal || !aiTopTitle || !aiTopDesc) return;

    function setMascotIcon(icon) { mascot.textContent = icon; }
    mascot.style.userSelect = 'none';
    mascot.style.webkitUserSelect = 'none';
    mascot.style.webkitTouchCallout = 'none';

    function showDescPanel(text) {
        aiTopTitle.style.display = 'none';
        aiTopDesc.style.display = 'block';
        aiDescText.textContent = text;
    }
    function hideDescPanel() {
        aiTopTitle.style.display = 'block';
        aiTopDesc.style.display = 'none';
        J.activeDescMovieTitle = '';
    }
    descCloseBtn.addEventListener('click', hideDescPanel);
    descSearchBtn.addEventListener('click', function() {
        if (J.activeDescMovieTitle) {
            searchInput.value = J.activeDescMovieTitle;
            document.querySelector('#typeSelect .select-selected').textContent = 'Все';
            document.querySelectorAll('#typeSelect .select-items li').forEach(li => {
                li.classList.remove('selected');
                if (li.dataset.value === '-1') li.classList.add('selected');
            });
            deactivateAI();
            if (typeof J.doSearch === 'function') J.doSearch();
        }
        hideDescPanel();
    });

    function saveChatHistory(messages) { sessionStorage.setItem('ai_chat_history', JSON.stringify(messages)); }
    function loadChatHistory() { const raw = sessionStorage.getItem('ai_chat_history'); return raw ? JSON.parse(raw) : []; }
    function clearChatHistory() { sessionStorage.removeItem('ai_chat_history'); }
    function addMessageToHistory(role, text) {
        const messages = loadChatHistory();
        messages.push({ role, text });
        saveChatHistory(messages);
    }
    function restoreChatFromHistory() {
        const messages = loadChatHistory();
        const container = aiPanel.querySelector('.ai-messages');
        container.innerHTML = '';
        messages.forEach(msg => {
            const msgDiv = document.createElement('div');
            msgDiv.className = `ai-message ai-${msg.role}`;
            msgDiv.textContent = msg.text;
            container.appendChild(msgDiv);
        });
        container.scrollTop = container.scrollHeight;
    }

    function switchToSearchTab() {
        document.querySelectorAll('.tabs button').forEach(b => b.classList.remove('active'));
        document.querySelector('.tabs button[data-tab="search"]').classList.add('active');
        document.querySelectorAll('.tab-content').forEach(c => c.style.display = 'none');
        document.getElementById('tab-search').style.display = 'block';
        J.currentTab = 'search';
    }

    function activateAI() {
        J.aiActive = true;
        aiModal.classList.add('active');
        const saved = loadChatHistory();
        if (saved.length > 0) {
            restoreChatFromHistory();
        } else {
            aiPanel.querySelector('.ai-messages').innerHTML = '';
        }
        if (J.lastRecommendationMovies && J.lastRecommendationMovies.length > 0) {
            renderRecommendations(J.lastRecommendationMovies);
        } else {
            renderQuickActions();
        }
        setMascotIcon('🔄');
    }

    function deactivateAI(silent = false) {
        if (!silent && J.aiAbortController) {
            J.aiAbortController.abort();
            J.aiAbortController = null;
        }
        J.aiActive = false;
        aiModal.classList.remove('active');
        setMascotIcon('🤖');
    }

    function showAIMessage(text, role) {
        const container = aiPanel.querySelector('.ai-messages');
        const msgDiv = document.createElement('div');
        msgDiv.className = `ai-message ai-${role}`;
        msgDiv.textContent = text;
        container.appendChild(msgDiv);
        container.scrollTop = container.scrollHeight;
        addMessageToHistory(role, text);
    }

    function clearAIContext() {
        clearChatHistory();
        aiPanel.querySelector('.ai-messages').innerHTML = '';
        J.aiSessionID = null;
        J.lastRecommendationMovies = null;
        J.recommendMessageShown = false;
        hideDescPanel();
        renderQuickActions();
    }

    function renderQuickActions() {
        const chipsContainer = aiPanel.querySelector('.ai-chips');
        chipsContainer.innerHTML = '';
        const actions = [
            { icon: '🎲', label: 'Случайный' },
            { icon: '🔥', label: 'Популярное' },
            { icon: '🆕', label: 'Новинки' },
            { icon: '🎭', label: 'Жанр' }
        ];
        chipsContainer.className = 'ai-chips ai-chips-grid';
        actions.forEach(action => {
            const chip = document.createElement('span');
            chip.className = 'ai-chip ai-chip-action';
            chip.textContent = action.icon + ' ' + action.label;
            chip.addEventListener('click', () => {
                if (action.label === 'Жанр') {
                    const genres = randomGenres(10);
                    showChips(genres, false);
                } else {
                    let query = '';
                    const seed = Date.now();
                    if (action.label === 'Случайный') {
                        query = `предложи 10 случайных фильмов с высоким рейтингом, не повторяй предыдущие рекомендации (вариант ${seed})`;
                    } else if (action.label === 'Популярное') {
                        query = `популярные фильмы прямо сейчас, не повторяй уже названные (вариант ${seed})`;
                    } else if (action.label === 'Новинки') {
                        const today = new Date();
                        query = `новые фильмы ${today.getFullYear()} года`;
                    }
                    sendAIQuery(query, true);
                }
            });
            chipsContainer.appendChild(chip);
        });
    }

    function sendAIQuery(text, silent = false) {
        if (!text.trim()) return;
        if (!silent) { showAIMessage(text, 'user'); }
        if (J.aiAbortController) { J.aiAbortController.abort(); }
        J.aiAbortController = new AbortController();
        const signal = J.aiAbortController.signal;
        setMascotIcon('🔄');

        const params = new URLSearchParams({ action: 'ai_query', query: text });
        if (J.aiSessionID) params.append('session_id', J.aiSessionID);

        fetch('/index.php', {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: params.toString(),
            signal: signal
        })
        .then(r => r.json())
        .then(data => {
            if (data.session_id) J.aiSessionID = data.session_id;
            if (data.action === 'search') {
                switchToSearchTab();
                searchInput.value = data.search_query;
                // установка типа селекта
                if (data.search_type) {
                    const typeMap = { 'movie': 'Фильмы', 'serial': 'Сериалы', 'docuserial': 'Докум.', 'anime': 'Аниме' };
                    document.querySelector('#typeSelect .select-selected').textContent = typeMap[data.search_type] || 'Все';
                    document.querySelectorAll('#typeSelect .select-items li').forEach(li => {
                        li.classList.remove('selected');
                        if (li.dataset.value === data.search_type) li.classList.add('selected');
                    });
                } else {
                    document.querySelector('#typeSelect .select-selected').textContent = 'Все';
                    document.querySelectorAll('#typeSelect .select-items li').forEach(li => {
                        li.classList.remove('selected');
                        if (li.dataset.value === '-1') li.classList.add('selected');
                    });
                }
                deactivateAI();
                if (typeof J.doSearch === 'function') J.doSearch();
            } else if (data.action === 'recommend') {
                if (!J.recommendMessageShown) {
                    showAIMessage("Вот что может понравиться:", 'assistant');
                    J.recommendMessageShown = true;
                }
                J.lastRecommendationMovies = data.movies;
                renderRecommendations(data.movies);
                setMascotIcon('🤖');
            } else if (data.action === 'describe') {
                const desc = data.text || "Информация временно недоступна.";
                showDescPanel(desc);
                setMascotIcon('🤖');
            } else if (data.action === 'chips') {
                showChips(data.chips, false);
                setMascotIcon('🤖');
            } else if (data.action === 'ask') {
                showAIMessage(data.question, 'assistant');
                setMascotIcon('🤖');
            }
        })
        .catch(err => {
            if (err.name !== 'AbortError') {
                console.error('AI error:', err);
                showAIMessage('Ошибка связи. Попробуйте позже.', 'assistant');
                setMascotIcon('🤖');
            }
        });
    }

    function requestMoreRecommendations() { sendAIQuery('ещё', true); }

    function createMovieChip(movie) {
        const chip = document.createElement('span');
        chip.className = 'ai-chip ai-movie-chip';
        chip.innerHTML = `${movie.title} ★${movie.rating}`;
        let longPressTimer = null, isLongPress = false, startX = 0, startY = 0;
        function startLongPress(e) {
            isLongPress = false;
            const touch = e.touches ? e.touches[0] : e;
            startX = touch.clientX; startY = touch.clientY;
            chip.classList.add('pressed');
            let loadingShown = false;
            const loadingTimer = setTimeout(() => {
                if (chip.classList.contains('pressed')) {
                    chip.classList.add('long-press-start');
                    J.activeDescMovieTitle = movie.title;
                    showDescPanel('Загрузка описания...');
                    loadingShown = true;
                }
            }, 200);
            longPressTimer = setTimeout(() => {
                isLongPress = true;
                chip.classList.remove('pressed', 'long-press-start');
                sendAIQuery(`Опиши фильм "${movie.title}" строго в 1-2 предложениях, укажи русское название и интересный факт.`, true);
            }, 500);
            chip._loadingTimer = loadingTimer;
        }
        function cancelLongPress() {
            if (longPressTimer) { clearTimeout(longPressTimer); longPressTimer = null; }
            if (chip._loadingTimer) { clearTimeout(chip._loadingTimer); chip._loadingTimer = null; }
            chip.classList.remove('pressed', 'long-press-start');
        }
        chip.addEventListener('touchstart', (e) => { e.preventDefault(); startLongPress(e); }, {passive: false});
        chip.addEventListener('touchmove', (e) => {
            if (!longPressTimer) return;
            const touch = e.touches[0];
            if (Math.abs(touch.clientX - startX) > 10 || Math.abs(touch.clientY - startY) > 10) {
                cancelLongPress();
                hideDescPanel();
            }
        }, {passive: false});
        chip.addEventListener('touchend', () => {
            cancelLongPress();
            if (!isLongPress) {
                switchToSearchTab();
                searchInput.value = movie.title;
                document.querySelector('#typeSelect .select-selected').textContent = 'Все';
                document.querySelectorAll('#typeSelect .select-items li').forEach(li => { li.classList.remove('selected'); if (li.dataset.value === '-1') li.classList.add('selected'); });
                deactivateAI();
                if (typeof J.doSearch === 'function') J.doSearch();
            }
        });
        chip.addEventListener('touchcancel', cancelLongPress);
        chip.addEventListener('mousedown', startLongPress);
        chip.addEventListener('mousemove', (e) => {
            if (!longPressTimer) return;
            if (Math.abs(e.clientX - startX) > 10 || Math.abs(e.clientY - startY) > 10) { cancelLongPress(); hideDescPanel(); }
        });
        chip.addEventListener('mouseup', () => {
            cancelLongPress();
            if (!isLongPress) {
                switchToSearchTab();
                searchInput.value = movie.title;
                document.querySelector('#typeSelect .select-selected').textContent = 'Все';
                document.querySelectorAll('#typeSelect .select-items li').forEach(li => { li.classList.remove('selected'); if (li.dataset.value === '-1') li.classList.add('selected'); });
                deactivateAI();
                if (typeof J.doSearch === 'function') J.doSearch();
            }
        });
        chip.addEventListener('mouseleave', cancelLongPress);
        return chip;
    }

    function renderRecommendations(movies) {
        const chipsContainer = aiPanel.querySelector('.ai-chips');
        chipsContainer.innerHTML = '';
        chipsContainer.className = 'ai-chips ai-chips-list';
        movies.forEach(movie => { chipsContainer.appendChild(createMovieChip(movie)); });
        const moreChip = document.createElement('span');
        moreChip.className = 'ai-chip ai-more-chip';
        moreChip.textContent = '🔄 Ещё...';
        moreChip.addEventListener('click', requestMoreRecommendations);
        chipsContainer.appendChild(moreChip);
    }

    function showChips(chips, areTitles = false) {
        const chipsContainer = aiPanel.querySelector('.ai-chips');
        chipsContainer.innerHTML = '';
        chipsContainer.className = 'ai-chips ai-chips-grid';
        chips.forEach(chipText => {
            const chip = document.createElement('span');
            chip.className = 'ai-chip ai-chip-query';
            chip.textContent = '❓ ' + chipText;
            chip.addEventListener('click', () => {
                if (areTitles) {
                    switchToSearchTab();
                    searchInput.value = chipText;
                    deactivateAI();
                    if (typeof J.doSearch === 'function') J.doSearch();
                } else {
                    sendAIQuery(`предложи 10 фильмов в жанре "${chipText}"`, true);
                }
            });
            chipsContainer.appendChild(chip);
        });
    }

    document.getElementById('aiSendBtn').addEventListener('click', () => {
        const input = document.getElementById('aiInput');
        if (input.value.trim()) { sendAIQuery(input.value.trim()); input.value = ''; }
    });
    document.getElementById('aiInput').addEventListener('keypress', (e) => { if (e.key === 'Enter') document.getElementById('aiSendBtn').click(); });
    document.getElementById('aiMicBtn').addEventListener('click', () => {
        const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
        if (!SpeechRecognition) { alert('Голосовой ввод не поддерживается'); return; }
        const recognition = new SpeechRecognition();
        recognition.lang = 'ru-RU'; recognition.interimResults = false;
        recognition.start();
        recognition.onresult = (event) => { sendAIQuery(event.results[0][0].transcript); };
        recognition.onerror = () => alert('Ошибка распознавания');
    });
    document.getElementById('aiCloseBtn').addEventListener('click', () => deactivateAI());
    document.getElementById('aiClearBtn').addEventListener('click', () => clearAIContext());

    let mascotLongPressTimer = null;
    mascot.addEventListener('touchstart', (e) => {
        e.preventDefault();
        mascotLongPressTimer = setTimeout(() => {
            if (J.aiActive || loadChatHistory().length > 0) { clearAIContext(); }
            mascotLongPressTimer = null;
        }, 1500);
    }, {passive: false});
    mascot.addEventListener('touchend', () => {
        if (mascotLongPressTimer) { clearTimeout(mascotLongPressTimer); mascotLongPressTimer = null; if (J.aiActive) deactivateAI(); else activateAI(); }
    });
    mascot.addEventListener('touchcancel', () => { if (mascotLongPressTimer) { clearTimeout(mascotLongPressTimer); mascotLongPressTimer = null; } });
    mascot.addEventListener('click', () => { if (J.aiActive) deactivateAI(); else activateAI(); });
    setMascotIcon('🤖');
});
})();
