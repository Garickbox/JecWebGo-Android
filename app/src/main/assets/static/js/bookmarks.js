(function() {
const J = window.JecWeb;

J.loadBookmarks = function() {
    if (!J.settings.enable_bookmarks) {
        document.getElementById('bookmarksList').textContent = 'Закладки отключены в настройках';
        return;
    }
    fetch('/index.php', { method:'POST', headers:{'Content-Type':'application/x-www-form-urlencoded'}, body:'action=get_bookmarks' })
    .then(r => r.json())
    .then(data => {
        const list = document.getElementById('bookmarksList');
        list.innerHTML = '';
        if (!data.length) {
            list.innerHTML = '<div class="empty-magnets">Нет закладок</div>';
            return;
        }
        data.forEach((b, idx) => {
            const item = document.createElement('div');
            item.className = 'bookmark-item-compact';
            const title = b.title || 'Без названия';
            const shortTitle = title.length > 38 ? title.substring(0, 35) + '…' : title;
            
            item.innerHTML = `
                <div class="panel-actions-left">
                    <button class="panel-btn btn-play-phone" data-magnet="${b.magnet}" title="Смотреть на телефоне">▶</button>
                    <button class="panel-btn btn-play-tv" data-magnet="${b.magnet}" title="Отправить на ТВ">📺</button>
                    <button class="panel-btn panel-drop" data-index="${idx}" title="Удалить закладку">✕</button>
                </div>
                <span class="bookmark-title" title="${title}">${shortTitle}</span>
            `;
            list.appendChild(item);
        });

        // Обработчики кнопок
        list.querySelectorAll('.btn-play-phone').forEach(btn => {
            btn.addEventListener('click', function() {
                const m = this.dataset.magnet;
                fetch('/index.php', { method:'POST', headers:{'Content-Type':'application/x-www-form-urlencoded'}, body:'action=play_local&magnet='+encodeURIComponent(m) })
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
        list.querySelectorAll('.btn-play-tv').forEach(btn => {
            btn.addEventListener('click', function() {
                const m = this.dataset.magnet;
                fetch('/index.php', { method:'POST', headers:{'Content-Type':'application/x-www-form-urlencoded'}, body:'action=play_tv&magnet='+encodeURIComponent(m) })
                .then(r => r.json()).then(d => { if(d.error) toast(d.error, 'error'); else if(d.m3u) navigator.clipboard.writeText(d.m3u).then(() => toast('M3U скопирован!', 'success')); });
            });
        });
        list.querySelectorAll('.panel-drop').forEach(btn => {
            btn.addEventListener('click', function() {
                const idx = this.dataset.index;
                fetch('/index.php', { method:'POST', headers:{'Content-Type':'application/x-www-form-urlencoded'}, body:'action=remove_bookmark&index='+idx })
                .then(r => r.json()).then(d => { if(d.ok) J.loadBookmarks(); });
            });
        });
    });
};
})();
