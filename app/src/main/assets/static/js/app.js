(function() {
const J = window.JecWeb;

document.addEventListener('DOMContentLoaded', function() {
    document.querySelectorAll('.tabs button').forEach(btn => {
        btn.addEventListener('click', function() {
            const tab = this.dataset.tab;
            
            // Сбрасываем активные классы
            document.querySelectorAll('.tabs button').forEach(b => b.classList.remove('active'));
            this.classList.add('active');
            
            J.currentTab = tab;
            document.querySelectorAll('.tab-content').forEach(c => c.style.display = 'none');
            document.getElementById('tab-' + tab).style.display = 'block';
            
            if (tab === 'search') {
                // Очищаем поле поиска и показываем каталог
                const searchInput = document.getElementById('searchInput');
                if (searchInput) searchInput.value = '';
                
                // Сбрасываем результаты
                if (typeof J.clearResults === 'function') J.clearResults();
                
                // Показываем каталог
                if (typeof J.showCatalog === 'function') {
                    J.showCatalog();
                }
            } else if (tab === 'bookmarks') {
                startBackgroundServices();
                if (typeof J.loadBookmarks === 'function') J.loadBookmarks();
            } else if (tab === 'history') {
                startBackgroundServices();
                if (typeof J.loadPlayHistory === 'function') J.loadPlayHistory();
            } else if (tab === 'magnets') {
                startBackgroundServices();
                if (typeof J.loadMagnets === 'function') J.loadMagnets();
            } else if (tab === 'settings') {
                if (typeof J.loadSettingsForm === 'function') J.loadSettingsForm();
            }
            if (tab !== 'settings' && typeof J.updateStatus === 'function') J.updateStatus();
        });
    });

    // Первоначальная загрузка
    if (typeof J.loadHistory === 'function') {
        J.loadHistory();
    }
});
})();
