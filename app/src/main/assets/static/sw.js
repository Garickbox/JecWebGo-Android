const CACHE_NAME = 'jecweb-v1';
const urlsToCache = [
  '/',
  '/static/style.css',
  '/static/js/common.js',
  '/static/js/ws.js',
  '/static/js/search.js',
  '/static/js/player.js',
  '/static/js/magnets.js',
  '/static/js/bookmarks.js',
  '/static/js/history.js',
  '/static/js/settings.js',
  '/static/js/status.js',
  '/static/js/app.js',
  '/static/manifest.json'
];

self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE_NAME).then(cache => cache.addAll(urlsToCache))
  );
});

self.addEventListener('fetch', event => {
  event.respondWith(
    caches.match(event.request).then(response => response || fetch(event.request))
  );
});
