(function() {
    const J = window.JecWeb;

    // ----- Состояние -----
    let moviesCache = [];
    let groupedMovies = [];
    let displayedCount = 0;
    const BATCH_SIZE = 15;
    let isLoading = false;
    const originalDoSearch = J.doSearch;

    // ----- Цвета категорий -----
    const categoryColors = {
        "Наши фильмы": "#1e5a2e",
        "Зарубежное кино": "#2e3a8c",
        "Наши сериалы": "#7a2e4a",
        "Зарубежные сериалы": "#2e6b7a",
        "Аниме": "#8c5a2e",
        "Мультфильмы": "#8c7a2e",
        "Документальные": "#4a2e6b",
        "Спорт": "#2e7a5a",
        "Музыкальные": "#6b2e5a"
    };
    const defaultColor = "#2a3a4a";

    // ----- Группировка и сортировка -----
    function groupByImdbId(data) {
        const map = new Map();
        data.forEach(item => {
            const id = item.imdb_id || item.title;
            if (!map.has(id)) {
                map.set(id, {
                    imdb_id: id,
                    title: item.title,
                    poster: item.poster,
                    rating: item.rating,
                    year: item.year,
                    genre: item.genre,
                    category: item.category,
                    description: item.description,
                    director: item.director,
                    actors: item.actors,
                    countries: item.countries,
                    duration: item.duration,
                    age_rating: item.age_rating,
                    releases: []
                });
            }
            const group = map.get(id);
            group.releases.push({
                title: item.title,
                size: item.size,
                seeds: item.seeds || 0,
                magnet: item.magnet,
                quality: extractQuality(item.title),
                leeches: item.leeches
            });
            if (item.rating > group.rating) group.rating = item.rating;
        });
        let result = Array.from(map.values()).map(g => {
            g.totalSeeds = g.releases.reduce((sum, r) => sum + r.seeds, 0);
            return g;
        });
        result.sort((a, b) => b.totalSeeds - a.totalSeeds);
        return result;
    }

    function extractQuality(title) {
        const parts = title.split(' ');
        return parts.length > 1 ? parts[parts.length-1] : '';
    }

    // ----- Генерация стильной заглушки -----
    function generatePlaceholderDataUrl(movie) {
        const canvas = document.createElement('canvas');
        const w = 300, h = 450;
        canvas.width = w;
        canvas.height = h;
        const ctx = canvas.getContext('2d');

        const color1 = categoryColors[movie.category] || defaultColor;
        const color2 = lightenColor(color1, 20);
        const grad = ctx.createLinearGradient(0, 0, w, h);
        grad.addColorStop(0, color1);
        grad.addColorStop(1, color2);
        ctx.fillStyle = grad;
        ctx.fillRect(0, 0, w, h);

        const initial = getTitleInitial(movie.title);
        ctx.font = 'bold 120px "Helvetica Neue", Helvetica, Arial, sans-serif';
        ctx.fillStyle = 'rgba(255,255,255,0.3)';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText(initial, w/2, h/2 - 20);

        const displayTitle = getCleanTitle(movie.title);
        ctx.font = 'bold 24px "Helvetica Neue", Helvetica, Arial, sans-serif';
        ctx.fillStyle = 'rgba(255,255,255,0.9)';
        ctx.textAlign = 'center';
        wrapText(ctx, displayTitle, w/2, h/2 + 60, w - 40, 28);

        if (movie.year) {
            ctx.font = '18px "Helvetica Neue", Helvetica, Arial, sans-serif';
            ctx.fillStyle = 'rgba(255,255,255,0.6)';
            ctx.fillText(movie.year, w/2, h - 30);
        }

        return canvas.toDataURL('image/jpeg', 0.85);
    }

    function getTitleInitial(title) {
        if (!title) return '?';
        const match = title.match(/[А-ЯA-Z0-9]/i);
        return match ? match[0].toUpperCase() : title.charAt(0).toUpperCase();
    }

    function getCleanTitle(title) {
        if (!title) return 'Без названия';
        return title.replace(/\s*\(?\d{4}\)?\s*/g, '').replace(/\b(WEB-DL|BDRip|1080p|2160p|HDRip|DVDrip|WEB-DLRip)\b/gi, '').trim();
    }

    function wrapText(ctx, text, x, y, maxWidth, lineHeight) {
        const words = text.split(' ');
        let line = '';
        let lines = [];
        for (let n = 0; n < words.length; n++) {
            let testLine = line + words[n] + ' ';
            let metrics = ctx.measureText(testLine);
            if (metrics.width > maxWidth && n > 0) {
                lines.push(line.trim());
                line = words[n] + ' ';
            } else {
                line = testLine;
            }
        }
        lines.push(line.trim());
        let startY = y - ((lines.length - 1) * lineHeight) / 2;
        for (let i = 0; i < lines.length; i++) {
            ctx.fillText(lines[i], x, startY + i * lineHeight);
        }
    }

    function lightenColor(hex, percent) {
        const num = parseInt(hex.slice(1), 16);
        const amt = Math.round(2.55 * percent);
        const R = Math.min(255, (num >> 16) + amt);
        const G = Math.min(255, ((num >> 8) & 0x00FF) + amt);
        const B = Math.min(255, (num & 0x0000FF) + amt);
        return `rgb(${R},${G},${B})`;
    }

    // ----- Умная загрузка постера с таймаутом -----
    function setPosterWithFallback(img, movie) {
        if (!movie.poster) {
            img.src = generatePlaceholderDataUrl(movie);
            return;
        }

        let fallbackTimer = null;
        const fallback = () => {
            if (fallbackTimer) clearTimeout(fallbackTimer);
            img.src = generatePlaceholderDataUrl(movie);
        };

        img.onerror = () => {
            fallback();
        };

        img.onload = () => {
            if (fallbackTimer) clearTimeout(fallbackTimer);
        };

        img.src = movie.poster;
        fallbackTimer = setTimeout(() => {
            if (!img.complete || img.naturalWidth === 0) {
                fallback();
            }
        }, 5000);
    }

    // ----- Рендер сетки -----
    function renderBatch() {
        if (displayedCount >= groupedMovies.length) return;
        const grid = document.getElementById('catalogGrid');
        if (!grid) return;
        const end = Math.min(displayedCount + BATCH_SIZE, groupedMovies.length);
        const fragment = document.createDocumentFragment();
        for (let i = displayedCount; i < end; i++) {
            const card = createPosterCard(groupedMovies[i]);
            fragment.appendChild(card);
        }
        grid.appendChild(fragment);
        displayedCount = end;
    }

    function createPosterCard(movie) {
        const card = document.createElement('div');
        card.className = 'poster-card';
        const releasesCount = movie.releases.length;
        const bestRelease = movie.releases.reduce((a, b) => (a.seeds > b.seeds) ? a : b, movie.releases[0]);
        const rating = movie.rating ? '★ ' + movie.rating : '';
        const year = movie.year || '';
        const category = movie.category || '';
        const displayTitle = movie.title || 'Без названия';

        card.innerHTML = `
            <div class="poster-img-wrap">
                <img class="poster-img" src="" loading="lazy">
                <div class="poster-overlay">
                    <span class="badge badge-top-left">${category}</span>
                    <span class="badge badge-top-right">${rating}</span>
                    <span class="badge badge-bottom-left badge-releases">${releasesCount} разд.</span>
                    <span class="badge badge-bottom-right">${year}</span>
                </div>
            </div>
            <div class="poster-info">
                <div class="poster-title" title="${displayTitle}">${displayTitle}</div>
                <div class="poster-meta">
                    <span>${bestRelease.size || '?'}</span>
                    <span>🔽 ${bestRelease.seeds || 0}</span>
                </div>
                <div class="poster-actions">
                    <button class="poster-btn phone">▶</button>
                    <button class="poster-btn tv">📺</button>
                    <button class="poster-btn bookmark">🔖</button>
                </div>
            </div>
        `;

        const img = card.querySelector('.poster-img');
        setPosterWithFallback(img, movie);

        card.querySelector('.poster-btn.phone').addEventListener('click', (e) => {
            e.stopPropagation();
            openReleasesModal(movie, 'phone');
        });
        card.querySelector('.poster-btn.tv').addEventListener('click', (e) => {
            e.stopPropagation();
            openReleasesModal(movie, 'tv');
        });
        card.querySelector('.poster-btn.bookmark').addEventListener('click', (e) => {
            e.stopPropagation();
            openReleasesModal(movie, 'bookmark');
        });
        card.addEventListener('click', () => {
            openDetailsModal(movie);
        });
        return card;
    }

    // ----- Размытые модалки с fallback-фоном -----
    function setModalBackground(modal, posterUrl, movie) {
        if (posterUrl) {
            const testImg = new Image();
            testImg.onload = () => {
                modal.classList.add('modal-blurred');
                modal.style.backgroundImage = `url(${posterUrl})`;
            };
            testImg.onerror = () => {
                const dataUrl = generatePlaceholderDataUrl(movie);
                modal.classList.add('modal-blurred');
                modal.style.backgroundImage = `url(${dataUrl})`;
            };
            testImg.src = posterUrl;
            setTimeout(() => {
                if (!testImg.complete || testImg.naturalWidth === 0) {
                    testImg.onerror();
                }
            }, 4000);
        } else {
            modal.classList.remove('modal-blurred');
            modal.style.backgroundImage = '';
        }
    }

    function openReleasesModal(movie, context) {
        const modal = document.getElementById('releasesModal');
        const list = document.getElementById('releasesList');
        const title = document.getElementById('releasesModalTitle');
        if (!modal || !list) return;

        title.textContent = 'Доступные раздачи';
        list.innerHTML = '';

        movie.releases.forEach(release => {
            const item = document.createElement('div');
            item.className = 'release-item';
            const size = release.size || '?';
            const seeds = release.seeds || 0;
            item.innerHTML = `
                <div class="release-info">
                    <div class="release-quality">${release.quality || 'HD'}</div>
                    <div class="release-meta">${size} · сиды: ${seeds}</div>
                </div>
                <div class="release-actions">
                    <button class="release-phone" title="Смотреть на телефоне">▶</button>
                    <button class="release-tv" title="На ТВ">📺</button>
                    <button class="release-bookmark" title="В закладки">🔖</button>
                </div>
            `;
            item.querySelector('.release-phone').addEventListener('click', () => {
                playOnPhone(release.magnet, release.title);
                closeModal(modal);
            });
            item.querySelector('.release-tv').addEventListener('click', () => {
                playOnTV(release.magnet, release.title);
                closeModal(modal);
            });
            item.querySelector('.release-bookmark').addEventListener('click', () => {
                addBookmark(release.magnet, release.title);
                closeModal(modal);
            });
            list.appendChild(item);
        });

        setModalBackground(modal, movie.poster, movie);
        modal.classList.add('active');
    }

    function openDetailsModal(movie) {
        const modal = document.getElementById('detailsModal');
        const content = document.getElementById('detailsContent');
        if (!modal || !content) return;

        const rating = movie.rating ? `★ ${movie.rating}` : '';
        const year = movie.year || '';
        const genre = movie.genre || '';
        const director = movie.director || '';
        const actors = movie.actors || '';
        const description = movie.description || '';
        const countries = movie.countries || '';
        const duration = movie.duration ? `${movie.duration} мин` : '';
        const age = movie.age_rating || '';

        content.innerHTML = `
            <div class="detail-title">${movie.title}</div>
            <div class="detail-meta">${[year, genre, rating].filter(Boolean).join(' · ')}</div>
            ${director ? `<div class="detail-meta">Режиссёр: ${director}</div>` : ''}
            ${actors ? `<div class="detail-meta">В ролях: ${actors}</div>` : ''}
            ${countries ? `<div class="detail-meta">Страна: ${countries}</div>` : ''}
            ${duration ? `<div class="detail-meta">${duration}</div>` : ''}
            ${age ? `<div class="detail-meta">${age}</div>` : ''}
            ${description ? `<div class="detail-desc">${description}</div>` : ''}
            <button class="detail-btn" id="detailWatchBtn">▶ Смотреть</button>
        `;

        document.getElementById('detailWatchBtn').addEventListener('click', () => {
            closeModal(modal);
            openReleasesModal(movie, 'phone');
        });

        setModalBackground(modal, movie.poster, movie);
        modal.classList.add('active');
    }

    function closeModal(modal) {
        modal.classList.remove('active');
        setTimeout(() => {
            modal.classList.remove('modal-blurred');
            modal.style.backgroundImage = '';
        }, 100);
    }

    // ----- Действия -----
    function playOnPhone(magnet, title) {
        if (J.lastPhoneHash) stopHash(J.lastPhoneHash);
        fetch('/index.php', {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: 'action=play_local&magnet=' + encodeURIComponent(magnet) + '&title=' + encodeURIComponent(title)
        })
        .then(r => r.json())
        .then(d => {
            if (d.error) return toast(d.error, 'error');
            if (d.hash) addActiveHash(d.hash, 'phone');
            if (d.video_url) {
                J.openPlayer(d.video_url);
                addToPlayLog('phone', d.hash, title);
            } else {
                toast('Запущено', 'success');
                addToPlayLog('phone', d.hash, title);
            }
        })
        .catch(() => toast('Ошибка', 'error'));
    }

    function playOnTV(magnet, title) {
        fetch('/index.php', {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: 'action=play_tv&magnet=' + encodeURIComponent(magnet) + '&title=' + encodeURIComponent(title)
        })
        .then(r => r.json())
        .then(d => {
            if (d.error) return toast(d.error, 'error');
            if (d.hash) addActiveHash(d.hash, 'tv');
            if (d.m3u) navigator.clipboard.writeText(d.m3u).then(() => toast('M3U скопирован', 'success'));
            else toast('Отправлено на ТВ', 'success');
            addToPlayLog('tv', d.hash, title);
        })
        .catch(() => toast('Ошибка', 'error'));
    }

    function addToPlayLog(type, hash, title) {
        if (!hash || !J.settings.enable_play_log) return;
        fetch('/index.php', {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: 'action=add_play_log&hash=' + encodeURIComponent(hash) +
                  '&title=' + encodeURIComponent(title) +
                  '&type=' + encodeURIComponent(type)
        }).catch(() => {});
    }

    function addBookmark(magnet, title) {
        fetch('/index.php', {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: 'action=add_bookmark&magnet=' + encodeURIComponent(magnet) + '&title=' + encodeURIComponent(title)
        })
        .then(r => r.json())
        .then(d => {
            if (d.ok) toast('Добавлено в закладки', 'success');
            else toast('Ошибка', 'error');
        });
    }

    // ----- Бесконечный скролл -----
    function setupCatalogObserver() {
        const sentinel = document.getElementById('catalogSentinel');
        if (!sentinel) return;
        const observer = new IntersectionObserver((entries) => {
            if (entries[0].isIntersecting && !isLoading && displayedCount < groupedMovies.length) {
                isLoading = true;
                renderBatch();
                isLoading = false;
            }
        }, { rootMargin: '200px' });
        observer.observe(sentinel);
    }

    // ----- Управление видимостью (публичные функции) -----
    function showCatalog() {
        document.getElementById('results').style.display = 'none';
        const grid = document.getElementById('catalogGrid');
        grid.style.display = 'grid';
        document.getElementById('filtersContainer').style.display = 'none';
        document.getElementById('historyRow').style.display = 'none';
        document.getElementById('status').textContent = '';
        if (groupedMovies.length === 0) {
            loadCatalog();
        } else if (displayedCount === 0) {
            renderBatch();
            setupCatalogObserver();
        }
    }

    function hideCatalog() {
        document.getElementById('catalogGrid').style.display = 'none';
        document.getElementById('results').style.display = 'block';
    }

    async function loadCatalog() {
        const grid = document.getElementById('catalogGrid');
        grid.innerHTML = '<div class="empty-magnets">Загрузка...</div>';
        try {
            const resp = await fetch('/static/torrents.json');
            const data = await resp.json();
            moviesCache = data;
            groupedMovies = groupByImdbId(data);
            displayedCount = 0;
            grid.innerHTML = '';
            renderBatch();
            setupCatalogObserver();
        } catch (e) {
            grid.innerHTML = '<div class="empty-magnets">Ошибка загрузки</div>';
        }
    }

    // Экспортируем публичные функции
    J.showCatalog = showCatalog;
    J.hideCatalog = hideCatalog;

    // Переопределяем doSearch
    J.doSearch = function(append) {
        const q = document.getElementById('searchInput').value.trim();
        if (q === '') {
            showCatalog();
            J.clearResults();
            return;
        }
        hideCatalog();
        originalDoSearch.call(J, append);
    };

    document.getElementById('releasesModalClose').addEventListener('click', () => {
        closeModal(document.getElementById('releasesModal'));
    });
    document.getElementById('detailsModalClose').addEventListener('click', () => {
        closeModal(document.getElementById('detailsModal'));
    });

    window.addEventListener('click', (e) => {
        if (!e.target.classList.contains('modal')) return;
        if (e.target.id === 'playerModal') return;
        closeModal(e.target);
    });

    if (document.readyState === 'complete' || document.readyState === 'interactive') {
        showCatalog();
    } else {
        document.addEventListener('DOMContentLoaded', showCatalog);
    }
})();
