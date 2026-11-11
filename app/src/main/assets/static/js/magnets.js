(function() {
const J = window.JecWeb;
let cachedTVData = null;

// Загрузка только локальных магнитов
J.loadMagnets = function() {
    fetch('/index.php', { method:'POST', headers:{'Content-Type':'application/x-www-form-urlencoded'}, body:'action=get_magnets' })
    .then(r => r.json())
    .then(data => { J.renderMagnets(data, false); })
    .catch(() => {});
};

// Загрузка ТВ (по кнопке)
J.loadMagnetsWithTV = function() {
    const tvCard = document.getElementById('cardTV');
    const tvCount = document.getElementById('tvCount');
    if (tvCard) tvCard.style.opacity = '0.6';
    if (tvCount) tvCount.textContent = 'загрузка...';

    fetch('/index.php', { method:'POST', headers:{'Content-Type':'application/x-www-form-urlencoded'}, body:'action=get_magnets&include_tv=true' })
    .then(r => r.json())
    .then(data => {
        cachedTVData = data.tv || [];
        J.renderMagnets(data, true);
        if (tvCard) tvCard.style.opacity = '1';
    })
    .catch(() => {
        if (tvCount) tvCount.textContent = 'нет связи';
        if (tvCard) tvCard.style.opacity = '1';
        document.getElementById('tvMagnets').innerHTML = '<div class="empty-magnets">Нет соединения с ТВ</div>';
    });
};

J.renderMagnets = function(data, updateTV) {
    if (data.local) {
        J.renderMagnetsList('local', data.local, false);
        document.getElementById('localCount').textContent = (data.local || []).length + ' активных';
    }
    if (updateTV) {
        if (data.tv && data.tv.length > 0) {
            J.renderMagnetsList('tv', data.tv, true);
            document.getElementById('tvCount').textContent = data.tv.length + ' активных';
        } else {
            document.getElementById('tvMagnets').innerHTML = `
                <div class="empty-magnets">
                    <p>Нет активных загрузок</p>
                    <button id="loadTVBtn" class="btn" style="margin-top:8px; background:var(--accent);">Загрузить список ТВ</button>
                </div>`;
            document.getElementById('tvCount').textContent = '0 активных';
            document.getElementById('loadTVBtn').addEventListener('click', function() {
                J.loadMagnetsWithTV();
            });
        }
    }
};

J.renderMagnetsList = function(type, torrents, isTV) {
    const container = document.getElementById(type + 'Magnets');
    if (!container) return;
    if (!torrents || torrents.length === 0) {
        container.innerHTML = '<div class="empty-magnets">Нет активных загрузок</div>';
        return;
    }
    let html = '';
    if (isTV && torrents.length > 0) {
        html += `<div style="text-align:right; margin-bottom:6px;">
            <button id="refreshTVBtn" class="panel-btn" style="background:var(--surface); border:1px solid var(--border); color:var(--text); padding:4px 12px; border-radius:4px; cursor:pointer;">🔄 Обновить</button>
        </div>`;
    }
    const ip = isTV ? (J.settings.tv_ip || '192.168.1.128') : '127.0.0.1';
    torrents.forEach(t => {
        const name = t.name || t.title || t.hash;
        const percent = t.file_size ? ((t.loaded_size / t.file_size)*100).toFixed(1) : 0;
        const downSpeed = t.download_speed ? (t.download_speed/1024).toFixed(0)+' KB/s' : '0 KB/s';
        const sizeDone = t.loaded_size ? (t.loaded_size/1073741824).toFixed(2) + ' GB' : '0 GB';
        const sizeTotal = t.file_size ? (t.file_size/1073741824).toFixed(2) + ' GB' : '?';
        const seeds = t.peers_active ?? t.seeds ?? t.sid ?? 0;
        const peers = t.peers_total ?? t.peers ?? t.pir ?? 0;
        html += `
        <div class="magnet-item" data-hash="${t.hash}">
            <div class="magnet-info">
                <div class="magnet-title">${name}</div>
                <div class="magnet-details">${sizeDone} / ${sizeTotal} · ↓ ${downSpeed} · ${percent}% · Сиды: ${seeds} / Пиры: ${peers}</div>
                <div class="progress-bar"><div class="progress-fill" style="width:${percent}%"></div></div>
            </div>
            <div class="magnet-actions">
                ${!isTV ? `<button class="btn-play-phone" data-hash="${t.hash}" title="Смотреть на телефоне">▶</button>` : ''}
                <button class="btn-play-tv" data-hash="${t.hash}" data-ip="${ip}" title="${isTV ? 'Запустить на ТВ' : 'Скопировать M3U для ТВ'}">📺</button>
                <button class="btn-stop" data-hash="${t.hash}" data-ip="${ip}" title="Остановить">⏹</button>
                <button class="btn-drop" data-hash="${t.hash}" data-ip="${ip}" title="Удалить">✕</button>
            </div>
        </div>`;
    });
    container.innerHTML = html;

    // Кнопка обновления ТВ
    const refreshBtn = document.getElementById('refreshTVBtn');
    if (refreshBtn) {
        refreshBtn.addEventListener('click', function() {
            J.loadMagnetsWithTV();
        });
    }

    // ▶ Телефон
    container.querySelectorAll('.btn-play-phone').forEach(btn => {
        btn.addEventListener('click', function() {
            const hash = this.dataset.hash;
            fetch('/index.php', {
                method:'POST',
                headers:{'Content-Type':'application/x-www-form-urlencoded'},
                body:'action=play_local&hash=' + encodeURIComponent(hash) + '&title=' + encodeURIComponent(name)
            })
            .then(r => r.json())
            .then(d => {
                if (d.error) { toast(d.error, 'error'); }
                else if (d.video_url) {
                    J.openPlayer(d.video_url);
                    if (d.hash) addActiveHash(d.hash, 'phone');
                } else {
                    toast('Запущено на телефоне', 'success');
                    if (d.hash) addActiveHash(d.hash, 'phone');
                }
            })
            .catch(() => toast('Ошибка запуска', 'error'));
        });
    });

    // 📺 ТВ
    container.querySelectorAll('.btn-play-tv').forEach(btn => {
        btn.addEventListener('click', function() {
            const hash = this.dataset.hash;
            const ip = this.dataset.ip;
            fetch('/index.php', {
                method:'POST',
                headers:{'Content-Type':'application/x-www-form-urlencoded'},
                body:'action=play_tv_hash&hash=' + encodeURIComponent(hash) + '&ip=' + encodeURIComponent(ip)
            })
            .then(r => r.json())
            .then(d => {
                if (d.error) toast(d.error, 'error');
                else if (d.m3u) navigator.clipboard.writeText(d.m3u).then(() => toast('M3U скопирован для ТВ', 'success'));
                else if (d.ok) toast('Команда отправлена на ТВ', 'success');
            })
            .catch(() => toast('Ошибка отправки на ТВ', 'error'));
        });
    });

    // Стоп
    container.querySelectorAll('.btn-stop').forEach(btn => {
        btn.addEventListener('click', function() {
            const hash = this.dataset.hash;
            const ip = this.dataset.ip;
            fetch('/index.php', { method:'POST', headers:{'Content-Type':'application/x-www-form-urlencoded'}, body:'action=stop&hash='+encodeURIComponent(hash)+'&ip='+encodeURIComponent(ip) })
            .then(r => r.json()).then(d => { if(d.ok) J.loadMagnets(); });
        });
    });

    // Удаление
    container.querySelectorAll('.btn-drop').forEach(btn => {
        btn.addEventListener('click', function() {
            const hash = this.dataset.hash;
            const ip = this.dataset.ip;
            if (confirm('Удалить торрент безвозвратно?')) {
                fetch('/index.php', { method:'POST', headers:{'Content-Type':'application/x-www-form-urlencoded'}, body:'action=drop&hash='+encodeURIComponent(hash)+'&ip='+encodeURIComponent(ip) })
                .then(r => r.json()).then(d => {
                    if(d.ok) {
                        // Немедленно удаляем из DOM, чтобы не ждать обновления
                        const item = this.closest('.magnet-item');
                        if (item) item.remove();
                        // Обновляем счётчик
                        const countEl = document.getElementById(type + 'Count');
                        if (countEl) {
                            const remaining = document.querySelectorAll('#' + type + 'Magnets .magnet-item').length;
                            countEl.textContent = remaining + ' активных';
                        }
                        // Обновляем весь список в фоне (на случай, если были изменения)
                        J.loadMagnets();
                    }
                });
            }
        });
    });
};

// Переключение карточек
document.getElementById('cardLocal').addEventListener('click', function() {
    document.querySelectorAll('.magnet-device-card').forEach(c => c.classList.remove('active'));
    this.classList.add('active');
    document.getElementById('localMagnets').style.display = 'block';
    document.getElementById('tvMagnets').style.display = 'none';
    J.loadMagnets();
});

document.getElementById('cardTV').addEventListener('click', function() {
    document.querySelectorAll('.magnet-device-card').forEach(c => c.classList.remove('active'));
    this.classList.add('active');
    document.getElementById('localMagnets').style.display = 'none';
    document.getElementById('tvMagnets').style.display = 'block';

    const tvContainer = document.getElementById('tvMagnets');
    if (cachedTVData && cachedTVData.length > 0) {
        J.renderMagnetsList('tv', cachedTVData, true);
        document.getElementById('tvCount').textContent = cachedTVData.length + ' активных';
    } else {
        tvContainer.innerHTML = `
            <div class="empty-magnets">
                <p>Нет загруженных данных ТВ</p>
                <button id="loadTVBtn" class="btn" style="margin-top:8px; background:var(--accent);">Загрузить список ТВ</button>
            </div>`;
        document.getElementById('loadTVBtn').addEventListener('click', function() {
            J.loadMagnetsWithTV();
        });
    }
});
})();
