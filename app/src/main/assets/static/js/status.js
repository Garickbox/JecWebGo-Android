(function() {
const J = window.JecWeb;

J.updateStatus = function(data) {
    if (data) {
        document.getElementById('localDot').style.background = data.local_ts ? 'var(--green)' : 'var(--red)';
        document.getElementById('tvDot').style.background = data.tv_ts ? 'var(--green)' : 'var(--red)';
        document.getElementById('jacredDot').style.background = data.jacred ? 'var(--green)' : 'var(--red)';
        document.getElementById('posterDot').style.background = data.poster ? 'var(--green)' : 'var(--red)';
        return;
    }
    fetch('/index.php', { method:'POST', headers:{'Content-Type':'application/x-www-form-urlencoded'}, body:'action=status' })
    .then(r => r.json())
    .then(d => {
        document.getElementById('localDot').style.background = d.local_ts ? 'var(--green)' : 'var(--red)';
        document.getElementById('tvDot').style.background = d.tv_ts ? 'var(--green)' : 'var(--red)';
        document.getElementById('jacredDot').style.background = d.jacred ? 'var(--green)' : 'var(--red)';
        document.getElementById('posterDot').style.background = d.poster ? 'var(--green)' : 'var(--red)';
    })
    .catch(() => {});
};

J.updateTorrentPanel = function(torrents) {
    if (!J.settings.show_torrent_panel) {
        document.getElementById('torrentStatusPanel').style.display = 'none';
        return;
    }
    const panel = document.getElementById('torrentStatusPanel');
    const list = document.getElementById('panelTorrentList');
    if (!panel || !list) return;
    
    if (!torrents || torrents.length === 0) {
        panel.style.display = 'none';
        return;
    }
    
    const shown = torrents.slice(0, 2);
    let html = '';
    shown.forEach(t => {
        const name = (t.name || t.title || t.hash);
        const shortName = name.length > 35 ? name.substring(0, 32) + '…' : name;
        const percent = t.file_size ? ((t.loaded_size / t.file_size)*100).toFixed(0) : 0;
        html += `
        <div class="panel-torrent">
            <div class="panel-actions-left">
                <button class="panel-btn panel-play-phone" data-hash="${t.hash}" title="Смотреть на телефоне">▶</button>
                <button class="panel-btn panel-play-tv" data-hash="${t.hash}" title="Отправить на ТВ">📺</button>
                <button class="panel-btn panel-drop" data-hash="${t.hash}" title="Удалить">✕</button>
            </div>
            <div class="panel-info">
                <span class="panel-name" title="${name}">${shortName}</span>
                <div class="progress-bar panel-progress"><div class="progress-fill" style="width:${percent}%"></div></div>
            </div>
        </div>`;
    });
    list.innerHTML = html;
    panel.style.display = 'block';
    
    // ▶ Телефон
    list.querySelectorAll('.panel-play-phone').forEach(btn => {
        btn.addEventListener('click', function(e) {
            e.stopPropagation();
            const hash = this.dataset.hash;
            fetch('/index.php', {
                method:'POST',
                headers:{'Content-Type':'application/x-www-form-urlencoded'},
                body:'action=play_local&hash=' + encodeURIComponent(hash) + '&title='
            })
            .then(r => r.json())
            .then(d => {
                if (d.error) {
                    toast(d.error, 'error');
                } else if (d.video_url) {
                    J.openPlayer(d.video_url);
                    addActiveHash(d.hash, 'phone');
                } else {
                    toast('Запущено на телефоне', 'success');
                    if (d.hash) addActiveHash(d.hash, 'phone');
                }
            })
            .catch(() => {});
        });
    });
    
    // 📺 ТВ (копирование M3U)
    list.querySelectorAll('.panel-play-tv').forEach(btn => {
        btn.addEventListener('click', function(e) {
            e.stopPropagation();
            const hash = this.dataset.hash;
            fetch('/index.php', {
                method:'POST',
                headers:{'Content-Type':'application/x-www-form-urlencoded'},
                body:'action=play_tv_hash&hash=' + encodeURIComponent(hash) + '&ip=' + encodeURIComponent(J.settings.tv_ip || '192.168.1.128')
            })
            .then(r => r.json())
            .then(d => {
                if (d.error) toast(d.error, 'error');
                else if (d.m3u) navigator.clipboard.writeText(d.m3u).then(() => toast('M3U скопирован для ТВ', 'success'));
                else if (d.ok) toast('Команда отправлена на ТВ', 'success');
            })
            .catch(() => {});
        });
    });
    
    // ✕ Удалить
    list.querySelectorAll('.panel-drop').forEach(btn => {
        btn.addEventListener('click', function(e) {
            e.stopPropagation();
            const hash = this.dataset.hash;
            if (confirm('Удалить торрент безвозвратно?')) {
                fetch('/index.php', { method:'POST', headers:{'Content-Type':'application/x-www-form-urlencoded'}, body:'action=drop&hash='+encodeURIComponent(hash)+'&ip=127.0.0.1' })
                .then(() => { if (typeof J.loadMagnets === 'function') J.loadMagnets(); });
            }
        });
    });
};

// Закрытие панели
document.getElementById('panelClose').addEventListener('click', function() {
    document.getElementById('torrentStatusPanel').style.display = 'none';
});
})();
