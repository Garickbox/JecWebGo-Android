(function() {
const J = window.JecWeb;

function renderSettingsForm(s) {
    J.settings = s;

    const toggleDeps = () => {
        const playLog = document.getElementById('set_enable_play_log');
        const historyBlock = document.getElementById('historyDeps');
        const autoSB = document.getElementById('set_auto_speed_detect');
        const manualSB = document.getElementById('manualSuperblitz');
        const autoSBInfo = document.getElementById('autoSuperblitzInfo');
        const speedBtn = document.getElementById('measureSpeedBtn');
        
        if (historyBlock) historyBlock.style.display = playLog?.checked ? '' : 'none';
        
        if (autoSB?.checked) {
            if (manualSB) manualSB.style.display = 'none';
            if (autoSBInfo) {
                autoSBInfo.style.display = '';
                const level = parseInt(document.getElementById('set_superblitz_aggressiveness')?.value || 3);
                autoSBInfo.innerHTML = `<span style="color:var(--muted);">Уровень ${level}: ${getAggrDesc(level)}</span>`;
            }
            if (speedBtn) speedBtn.style.display = '';
        } else {
            if (manualSB) manualSB.style.display = '';
            if (autoSBInfo) autoSBInfo.style.display = 'none';
            if (speedBtn) speedBtn.style.display = 'none';
        }
    };

    const disabledLabel = (text) => `<span style="color:var(--red);" title="Функция не реализована">⚠️ ${text}</span>`;
    const partialLabel = (text) => `<span style="color:var(--orange);" title="Работает частично">⚠️ ${text}</span>`;

    document.getElementById('tab-settings').innerHTML = `
    <div class="settings-container" style="max-width:500px; margin:0 auto;">
        <h3 style="margin-bottom:16px; color:var(--accent);">⚙️ Настройки</h3>
        
        <div class="settings-block">
            <div class="settings-block-title">Основные</div>
            <label class="setting-row">
                <span>Поисковая система</span>
                <select id="set_search_provider" style="width:150px;">
                    <option value="jacred" ${s.search_provider === "jacred" ? "selected" : ""}>JacRed</option>
                    <option value="knaben" ${s.search_provider === "knaben" ? "selected" : ""}>Knaben (VPN)</option>
                    <option value="torrentio" ${s.search_provider === "torrentio" ? "selected" : ""}>Torrentio (IMDb ID)</option>
                </select>
            </label>
            <label class="setting-row">
                <span>Вести историю просмотров</span>
                <input type="checkbox" id="set_enable_play_log" ${s.enable_play_log ? 'checked' : ''}>
            </label>
            <label class="setting-row">
                <span>Включить закладки</span>
                <input type="checkbox" id="set_enable_bookmarks" ${s.enable_bookmarks ? 'checked' : ''}>
            </label>
            <label class="setting-row">
                <span>Голосовой поиск</span>
                <input type="checkbox" id="set_voice_search" ${s.voice_search ? 'checked' : ''}>
            </label>
            <label class="setting-row">
                <span>Голосовой ассистент</span>
                <input type="checkbox" id="set_voice_assistant" ${s.voice_assistant !== false ? 'checked' : ''}>
            </label>
            <label class="setting-row">
                ${disabledLabel('Fallback парсер (RuTor)')}
                <input type="checkbox" id="set_use_fallback_parser" disabled ${s.use_fallback_parser ? 'checked' : ''}>
            </label>
            <label class="setting-row">
                <span>Показывать панель торрентов</span>
                <input type="checkbox" id="set_show_torrent_panel" ${s.show_torrent_panel ? 'checked' : ''}>
            </label>
            <label class="setting-row">
                ${disabledLabel('Звук при завершении загрузки')}
                <input type="checkbox" id="set_sound_on_completion" disabled ${s.sound_on_completion ? 'checked' : ''}>
            </label>
        </div>

        <div class="settings-block">
            <div class="settings-block-title">SuperBlitz</div>
            <label class="setting-row">
                <span>Автоопределение скорости</span>
                <input type="checkbox" id="set_auto_speed_detect" ${s.auto_speed_detect ? 'checked' : ''}>
            </label>
            <div id="autoSuperblitzInfo" style="display:${s.auto_speed_detect ? '' : 'none'}; font-size:0.8rem; margin-left:10px; margin-bottom:8px;">
                <span style="color:var(--muted);">Уровень ${s.superblitz_aggressiveness || 3}: ${getAggrDesc(s.superblitz_aggressiveness || 3)}</span>
            </div>
            <div id="manualSuperblitz" style="display:${s.auto_speed_detect ? 'none' : ''};">
                <label class="setting-row">
                    <span>SuperBlitz: первые МБ</span>
                    <input type="number" id="set_superblitz_first_mb" value="${s.superblitz_first_mb || 20}" style="width:80px;">
                </label>
                <label class="setting-row">
                    <span>SuperBlitz: потоки</span>
                    <input type="number" id="set_superblitz_first_threads" min="1" max="30" value="${s.superblitz_first_threads || 20}" style="width:80px;">
                </label>
                <label class="setting-row">
                    <span>Предзагрузка: МБ</span>
                    <input type="number" id="set_preload_mb" value="${s.preload_mb || 50}" style="width:80px;">
                </label>
                <label class="setting-row">
                    <span>Предзагрузка: потоки</span>
                    <input type="number" id="set_preload_threads" min="1" max="20" value="${s.preload_threads || 6}" style="width:80px;">
                </label>
            </div>
            <button id="measureSpeedBtn" style="display:${s.auto_speed_detect ? '' : 'none'}; margin-top:6px; width:100%; padding:6px; background:var(--surface); border:1px solid var(--border); color:var(--text); border-radius:4px; cursor:pointer;">⚡ Определить сейчас</button>
        </div>

        <div class="settings-block">
            <div class="settings-block-title">Ограничения</div>
            <div id="historyDeps" style="display:${s.enable_play_log ? '' : 'none'};">
                <label class="setting-row">
                    <span>Лимит истории поиска</span>
                    <input type="number" id="set_history_limit" min="5" max="200" value="${s.history_limit || 50}" style="width:80px;">
                </label>
                <label class="setting-row">
                    <span>Лимит истории просмотров</span>
                    <input type="number" id="set_play_log_limit" min="10" max="500" value="${s.play_log_limit || 100}" style="width:80px;">
                </label>
                <label class="setting-row">
                    ${disabledLabel('Автоочистка истории (дней)')}
                    <input type="number" id="set_auto_clear_play_log_days" min="0" max="365" disabled value="${s.auto_clear_play_log_days || 0}" style="width:80px;">
                </label>
            </div>
            <label class="setting-row">
                ${partialLabel('Максимум одновременных загрузок')}
                <input type="number" id="set_max_concurrent_downloads" min="1" max="10" disabled value="${s.max_concurrent_downloads || 3}" style="width:80px;">
            </label>
        </div>

        <div class="settings-block">
            <div class="settings-block-title">Интерфейс и ТВ</div>
            <label class="setting-row">
                <span>Тема</span>
                <select id="set_theme" style="width:150px;">
                    <option value="dark" ${s.theme === 'dark' ? 'selected' : ''}>Тёмная</option>
                    <option value="oled" ${s.theme === 'oled' ? 'selected' : ''}>OLED (чёрный)</option>
                </select>
            </label>
            <label class="setting-row">
                <span>IP телевизора</span>
                <input type="text" id="set_tv_ip" value="${s.tv_ip || '192.168.1.128'}" style="width:150px;">
            </label>
            <label class="setting-row">
                <span>Действие "Телефон"</span>
                <select id="set_phone_action" style="width:150px;">
                    <option value="am" ${s.phone_action === 'am' ? 'selected' : ''}>Открыть VLC</option>
                    <option value="web_player" ${s.phone_action === 'web_player' ? 'selected' : ''}>Встроенный плеер</option>
                </select>
            </label>
            <label class="setting-row">
                <span>Действие "ТВ"</span>
                <select id="set_tv_action" style="width:150px;">
                    <option value="m3u" ${s.tv_action === 'm3u' ? 'selected' : ''}>Копировать M3U</option>
                    <option value="adb" ${s.tv_action === 'adb' ? 'selected' : ''}>Запуск через ADB</option>
                </select>
            </label>
            <label class="setting-row">
                <span>Пароль (пусто = без пароля)</span>
                <input type="text" id="set_password" value="${s.password || ''}" style="width:150px;">
            </label>
        </div>

            <label class="setting-row">
                <span>AI API ключ (DeepSeek)</span>
                <input type="password" id="set_ai_api_key" value="${s.ai_api_key || ''}" style="width:150px;">
            </label>
        <button id="saveSettingsBtn" class="btn" style="width:100%; margin-top:12px; background:var(--accent);">Сохранить</button>
        <div id="saveStatus" style="margin-top:8px; text-align:center;"></div>
    </div>
    <style>
        .settings-block { margin-bottom: 16px; padding: 8px 0; border-top: 1px solid var(--border); }
        .settings-block-title { font-size: 0.85rem; font-weight: 600; color: var(--accent); margin-bottom: 10px; }
        .setting-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; font-size: 0.85rem; color: var(--text); }
        .setting-row input[type="checkbox"] { width: 18px; height: 18px; }
        .setting-row input, .setting-row select { background: var(--surface); border: 1px solid var(--border); color: var(--text); padding: 4px 8px; border-radius: 4px; font-size: 0.8rem; }
        .setting-row input:disabled, .setting-row select:disabled { opacity: 0.5; }
    </style>`;

    document.getElementById('set_auto_speed_detect').addEventListener('change', toggleDeps);
    document.getElementById('set_enable_play_log').addEventListener('change', toggleDeps);

    document.getElementById('measureSpeedBtn').addEventListener('click', function() {
        const btn = this;
        btn.textContent = '⏳ Измерение...';
        btn.disabled = true;
        fetch('/index.php?_=' + Date.now(), {
            method:'POST',
            headers:{'Content-Type':'application/x-www-form-urlencoded'},
            body:'action=measure_speed'
        })
        .then(r => r.json())
        .then(d => {
            if (d.ok) {
                J.settings.superblitz_aggressiveness = d.superblitz_aggressiveness;
                btn.textContent = '✅ Уровень ' + d.superblitz_aggressiveness;
                const autoInfo = document.getElementById('autoSuperblitzInfo');
                if (autoInfo) {
                    autoInfo.innerHTML = `<span style="color:var(--muted);">Уровень ${d.superblitz_aggressiveness}: ${getAggrDesc(d.superblitz_aggressiveness)}</span>`;
                }
            } else {
                btn.textContent = '❌ Ошибка';
            }
            setTimeout(() => { btn.textContent = '⚡ Определить сейчас'; btn.disabled = false; }, 3000);
        })
        .catch(() => {
            btn.textContent = '❌ Ошибка сети';
            setTimeout(() => { btn.textContent = '⚡ Определить сейчас'; btn.disabled = false; }, 3000);
        });
    });

    document.getElementById('saveSettingsBtn').addEventListener('click', function() {
        const newSettings = {
            search_provider: document.getElementById('set_search_provider').value,
            enable_play_log: document.getElementById('set_enable_play_log').checked,
            enable_bookmarks: document.getElementById('set_enable_bookmarks').checked,
            tv_ip: document.getElementById('set_tv_ip').value.trim(),
            phone_action: document.getElementById('set_phone_action').value,
            tv_action: document.getElementById('set_tv_action').value,
            superblitz_first_mb: parseInt(document.getElementById('set_superblitz_first_mb')?.value) || 20,
            superblitz_first_threads: parseInt(document.getElementById('set_superblitz_first_threads')?.value) || 20,
            preload_mb: parseInt(document.getElementById('set_preload_mb')?.value) || 50,
            preload_threads: parseInt(document.getElementById('set_preload_threads')?.value) || 6,
            superblitz_aggressiveness: parseInt(document.getElementById('set_superblitz_aggressiveness')?.value) || 3,
            auto_speed_detect: document.getElementById('set_auto_speed_detect').checked,
            use_fallback_parser: false,
            theme: document.getElementById('set_theme').value,
            history_limit: parseInt(document.getElementById('set_history_limit')?.value) || 50,
            play_log_limit: parseInt(document.getElementById('set_play_log_limit')?.value) || 100,
            auto_clear_play_log_days: 0,
            show_torrent_panel: document.getElementById('set_show_torrent_panel').checked,
            voice_search: document.getElementById('set_voice_search').checked,
            voice_assistant: document.getElementById('set_voice_assistant').checked,
            password: document.getElementById('set_password').value,
            ai_api_key: document.getElementById('set_ai_api_key').value,
            php: s.php || 8809,
            ws: s.ws || 8810,
            poster: s.poster || 5002,
            max_concurrent_downloads: 3,
            sound_on_completion: false,
        };
        const statusEl = document.getElementById('saveStatus');
        statusEl.textContent = 'Сохранение...';
        statusEl.style.color = 'var(--muted)';
        fetch('/index.php?_=' + Date.now(), {
            method:'POST',
            cache: 'no-store',
            headers:{'Content-Type':'application/x-www-form-urlencoded'},
            body:'action=save_settings&data=' + encodeURIComponent(JSON.stringify(newSettings))
        })
        .then(r => r.json())
        .then(d => {
            if (d.ok) {
                J.settings = d.settings;
                J.aiSoundEnabled = d.settings.voice_assistant !== false;
                statusEl.textContent = '✅ Настройки сохранены';
                statusEl.style.color = 'var(--green)';
                setTimeout(() => { statusEl.textContent = ''; }, 2000);
            } else {
                statusEl.textContent = '❌ Ошибка: ' + (d.error || 'неизвестная ошибка');
                statusEl.style.color = 'var(--red)';
            }
        })
        .catch(err => {
            statusEl.textContent = '❌ Ошибка сети';
            statusEl.style.color = 'var(--red)';
        });
    });
}

J.loadSettingsForm = function() {
    fetch('/index.php?_=' + Date.now(), {
        method:'POST',
        cache: 'no-store',
        headers:{'Content-Type':'application/x-www-form-urlencoded'},
        body:'action=get_settings'
    })
    .then(r => r.json())
    .then(data => {
        const s = data.settings || data;
        renderSettingsForm(s);
    })
    .catch(() => {
        document.getElementById('tab-settings').innerHTML = '<p style="color:var(--red);">Не удалось загрузить настройки</p>';
    });
};

function getAggrDesc(level) {
    const descriptions = {
        1: 'Минимальная нагрузка (10 МБ, 8 потоков → 30 МБ, 3 потока)',
        2: 'Умеренная (15 МБ, 12 потоков → 40 МБ, 4 потока)',
        3: 'Сбалансированная (20 МБ, 20 потоков → 50 МБ, 6 потоков)',
        4: 'Высокая (30 МБ, 25 потоков → 80 МБ, 8 потоков)',
        5: 'Максимальная (50 МБ, 30 потоков → 150 МБ, 10 потоков)'
    };
    return descriptions[level] || '';
}
})();
