(function() {
const J = window.JecWeb;

J.loadHistory = function() {
    fetch('/index.php', { method:'POST', headers:{'Content-Type':'application/x-www-form-urlencoded'}, body:'action=get_history' })
    .then(r => r.json())
    .then(data => {
        const cloud = document.getElementById('historyCloud');
        cloud.innerHTML = '';
        if (!data.length) {
            document.getElementById('historyRow').style.display = 'none';
            return;
        }
        document.getElementById('historyRow').style.display = 'flex';
        const seen = new Set();
        data.forEach(h => {
            if (!seen.has(h.query)) {
                seen.add(h.query);
                const tag = document.createElement('span');
                tag.className = 'history-tag';
                tag.textContent = h.query;
                tag.addEventListener('click', () => {
                    document.getElementById('searchInput').value = h.query;
                    J.doSearch();
                });
                cloud.appendChild(tag);
            }
        });
    });
};

J.loadPlayHistory = function() {
    if (!J.settings.enable_play_log) {
        document.getElementById('playHistoryList').textContent = 'История отключена в настройках';
        return;
    }
    fetch('/index.php', { method:'POST', headers:{'Content-Type':'application/x-www-form-urlencoded'}, body:'action=get_play_log' })
    .then(r => r.json())
    .then(data => {
        const container = document.getElementById('playHistoryList');
        container.innerHTML = '';
        if (!data.length) {
            container.innerHTML = '<div class="empty-magnets">История запусков пуста</div>';
            return;
        }
        data.forEach((entry, idx) => {
            const item = document.createElement('div');
            item.className = 'bookmark-item-compact';
            const title = entry.title || 'Без названия';
            const shortTitle = title.length > 30 ? title.substring(0, 27) + '…' : title;
            const typeLabel = { phone: '📱', tv: '📺', bookmark: '🔖' }[entry.type] || '';
            const date = new Date(entry.time * 1000).toLocaleDateString();

            item.innerHTML = `
                <div class="panel-actions-left">
                    <button class="panel-btn btn-play-phone" data-magnet="${entry.magnet || ''}" data-title="${title}" title="Повторить на телефоне">▶</button>
                    <button class="panel-btn btn-play-tv" data-magnet="${entry.magnet || ''}" data-title="${title}" title="Повторить на ТВ">📺</button>
                    <button class="panel-btn btn-add-bookmark" data-magnet="${entry.magnet || ''}" data-title="${title}" title="Добавить в закладки">🔖</button>
                    <button class="panel-btn panel-drop" data-index="${idx}" title="Удалить из истории">✕</button>
                </div>
                <span class="bookmark-title" title="${title}">${typeLabel} ${shortTitle}</span>
                <span style="font-size:0.7rem; color:var(--muted); margin-left:8px; white-space:nowrap;">${date}</span>
            `;
            container.appendChild(item);
        });

        // ▶ Повторить на телефоне
        container.querySelectorAll('.btn-play-phone').forEach(btn => {
            btn.addEventListener('click', function() {
                const m = this.dataset.magnet;
                const t = this.dataset.title || 'Без названия';
                fetch('/index.php', { method:'POST', headers:{'Content-Type':'application/x-www-form-urlencoded'}, body: `action=play_local&magnet=${encodeURIComponent(m)}&title=${encodeURIComponent(t)}` })
                .then(r => r.json()).then(d => {
                    if(d.error) toast(d.error, 'error');
                    else {
                        if (d.video_url) J.openPlayer(d.video_url);
                        else toast('Запущено на телефоне', 'success');
                        if(d.hash) addActiveHash(d.hash);
                    }
                });
            });
        });
        // 📺 Повторить на ТВ
        container.querySelectorAll('.btn-play-tv').forEach(btn => {
            btn.addEventListener('click', function() {
                const m = this.dataset.magnet;
                const t = this.dataset.title || 'Без названия';
                fetch('/index.php', { method:'POST', headers:{'Content-Type':'application/x-www-form-urlencoded'}, body: `action=play_tv&magnet=${encodeURIComponent(m)}&title=${encodeURIComponent(t)}` })
                .then(r => r.json()).then(d => {
                    if(d.error) toast(d.error, 'error');
                    else if(d.m3u) navigator.clipboard.writeText(d.m3u).then(() => toast('M3U скопирован!', 'success'));
                    if(d.hash) addActiveHash(d.hash);
                });
            });
        });
        // 🔖 Добавить в закладки
        container.querySelectorAll('.btn-add-bookmark').forEach(btn => {
            btn.addEventListener('click', function() {
                const magnet = this.dataset.magnet;
                const title = this.dataset.title || 'Без названия';
                fetch('/index.php', { method:'POST', headers:{'Content-Type':'application/x-www-form-urlencoded'}, body: `action=add_bookmark&magnet=${encodeURIComponent(magnet)}&title=${encodeURIComponent(title)}` })
                .then(r => r.json()).then(d => {
                    if(d.ok) toast('Добавлено в закладки', 'success');
                    else toast('Ошибка добавления', 'error');
                });
            });
        });
        // ✕ Удалить из истории
        container.querySelectorAll('.panel-drop').forEach(btn => {
            btn.addEventListener('click', function() {
                const idx = this.dataset.index;
                fetch('/index.php', { method:'POST', headers:{'Content-Type':'application/x-www-form-urlencoded'}, body: `action=remove_play_log_entry&index=${idx}` })
                .then(r => r.json()).then(d => { if(d.ok) J.loadPlayHistory(); });
            });
        });
    });
};

document.getElementById('historyToggle').addEventListener('click', function() {
    const cloud = document.getElementById('historyCloud');
    if (J.historyExpanded) {
        cloud.classList.add('history-collapsed');
        cloud.classList.remove('history-expanded');
        J.historyExpanded = false;
    } else {
        cloud.classList.remove('history-collapsed');
        cloud.classList.add('history-expanded');
        J.historyExpanded = true;
    }
});
})();
