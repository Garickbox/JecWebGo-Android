package com.garickbox.jecwebgo.data.download

import android.content.Context
import com.garickbox.jecwebgo.data.store.DownloadHistoryStore
import com.garickbox.jecwebgo.data.store.DownloadStateStore
import com.garickbox.jecwebgo.data.store.PersistedDownload
import com.garickbox.jecwebgo.service.DownloadService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

object DownloadRepository {
    private val _active = MutableStateFlow<DownloadItem?>(null)
    val active: StateFlow<DownloadItem?> = _active.asStateFlow()

    private val _queue = MutableStateFlow<List<DownloadItem>>(emptyList())
    val queue: StateFlow<List<DownloadItem>> = _queue.asStateFlow()

    private val _history = MutableStateFlow<List<DownloadedItem>>(emptyList())
    val history: StateFlow<List<DownloadedItem>> = _history.asStateFlow()

    @Volatile var paused: Boolean = false
    @Volatile var cancelled: Boolean = false

    private var appCtx: Context? = null

    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
        _history.value = DownloadHistoryStore.load(ctx)
        val persisted = DownloadStateStore.load(ctx)
        if (persisted.isNotEmpty()) {
            android.util.Log.d("JecWeb", "restoring ${persisted.size} interrupted downloads")
            _queue.value = persisted.map { p ->
                var uri = p.uri.takeIf { it.isNotBlank() }
                // Восстановление через MediaStore, если uri потерян или файл не доступен
                if (uri != null && !MediaStoreWriter.exists(ctx, android.net.Uri.parse(uri))) {
                    android.util.Log.w("JecWeb", "uri не доступен, ищем по имени: ${p.request.fileName}")
                    uri = null
                }
                if (uri == null) {
                    val parentRel = p.request.relPath.substringBeforeLast("/", "")
                    val found = MediaStoreWriter.findByName(ctx, p.request.fileName, parentRel)
                    if (found != null) {
                        val size = MediaStoreWriter.getFileSize(ctx, found)
                        android.util.Log.d("JecWeb", "recovered file: ${p.request.fileName} size=$size")
                        uri = found.toString()
                    }
                }
                DownloadItem(
                    id = p.id,
                    request = p.request,
                    totalBytes = p.request.totalBytes,
                    downloadedBytes = p.downloadedBytes,
                    status = DownloadItem.Status.QUEUED,
                    existingUri = uri
                )
            }
            DownloadService.start(ctx)
        }
    }

    private var _lastPersistMs = 0L
    private fun persist(force: Boolean = false) {
        val ctx = appCtx ?: return
        val now = System.currentTimeMillis()
        if (!force && now - _lastPersistMs < 3000L) return
        _lastPersistMs = now
        val items = (listOfNotNull(_active.value) + _queue.value).map {
            PersistedDownload(it.id, it.request, it.existingUri ?: "", it.downloadedBytes)
        }
        DownloadStateStore.save(ctx, items)
    }

    fun enqueue(ctx: Context, request: DownloadRequest): String {
        val id = UUID.randomUUID().toString()
        _queue.update { it + DownloadItem(id = id, request = request, totalBytes = request.totalBytes) }
        persist()
        DownloadService.start(ctx)
        return id
    }

    fun groupKeyOf(item: DownloadItem): String =
        item.request.title + "|" + item.request.relPath.substringBeforeLast("/", "")

    fun moveGroupInQueue(groupKey: String, neighborKey: String) {
        if (groupKey == neighborKey || groupKey.isBlank() || neighborKey.isBlank()) return
        _queue.update { cur ->
            val q = cur.toMutableList()
            val groupIdx = q.indices.filter { groupKeyOf(q[it]) == groupKey }
            val neighIdx = q.indices.filter { groupKeyOf(q[it]) == neighborKey }
            if (groupIdx.isEmpty() || neighIdx.isEmpty()) return@update cur
            val groupFirst = groupIdx.first()
            val groupLast = groupIdx.last()
            val neighFirst = neighIdx.first()
            val neighLast = neighIdx.last()
            val adjacent = (groupLast + 1 == neighFirst) || (neighLast + 1 == groupFirst)
            if (!adjacent) {
                android.util.Log.w("JecWeb", "moveGroupInQueue: blocks not adjacent")
                return@update cur
            }
            val groupItems = groupIdx.map { q[it] }
            val neighItems = neighIdx.map { q[it] }
            val allIdx = (groupIdx + neighIdx).sorted()
            val combined = if (groupFirst < neighFirst) neighItems + groupItems else groupItems + neighItems
            allIdx.forEachIndexed { i, idx -> q[idx] = combined[i] }
            q.toList()
        }
        persist()
    }

    fun removeGroupFromQueue(groupKey: String) {
        _queue.update { q -> q.filter { groupKeyOf(it) != groupKey } }
        persist()
    }

    fun moveInQueue(from: Int, to: Int) {
        _queue.update { cur ->
            val q = cur.toMutableList()
            if (from !in q.indices || to !in q.indices || from == to) return@update cur
            val item = q.removeAt(from)
            q.add(to, item)
            q.toList()
        }
        persist()
    }

    fun removeFromQueue(id: String) {
        _queue.update { q -> q.filter { it.id != id } }
        persist()
    }

    fun pauseQueueItem(id: String) {
        _queue.update { q -> q.map { if (it.id == id) it.copy(status = DownloadItem.Status.PAUSED) else it } }
        persist()
    }

    fun resumeQueueItem(id: String) {
        _queue.update { q -> q.map { if (it.id == id) it.copy(status = DownloadItem.Status.QUEUED) else it } }
        persist()
    }

    fun pauseGroup(groupKey: String) {
        _queue.value = _queue.value.map {
            if (groupKeyOf(it) == groupKey) it.copy(status = DownloadItem.Status.PAUSED) else it
        }
        persist()
    }

    fun resumeGroup(groupKey: String) {
        _queue.value = _queue.value.map {
            if (groupKeyOf(it) == groupKey) it.copy(status = DownloadItem.Status.QUEUED) else it
        }
        persist()
    }

    fun next(): DownloadItem? {
        val n = _queue.value.firstOrNull { it.status != DownloadItem.Status.PAUSED } ?: return null
        _queue.update { q -> q.filter { it.id != n.id } }
        paused = false
        cancelled = false
        val item = n.copy(status = DownloadItem.Status.ACTIVE, totalBytes = n.request.totalBytes)
        _active.value = item
        persist()
        return item
    }

    fun setActiveUri(uri: String) {
        val cur = _active.value ?: return
        _active.value = cur.copy(existingUri = uri)
        persist()
    }

    fun setTotal(total: Long) {
        val cur = _active.value ?: return
        _active.value = cur.copy(totalBytes = total)
    }

    fun updateProgress(downloaded: Long, total: Long, speed: Long) {
        val cur = _active.value ?: return
        _active.value = cur.copy(
            downloadedBytes = downloaded,
            totalBytes = if (total > 0) total else cur.totalBytes,
            speedBps = speed
        )
        persist()
    }

    fun pause() {
        val cur = _active.value ?: return
        paused = true
        _active.value = cur.copy(status = DownloadItem.Status.PAUSED, speedBps = 0L)
        persist()
    }

    fun resume() {
        val cur = _active.value ?: return
        paused = false
        cancelled = false
        _active.value = cur.copy(status = DownloadItem.Status.ACTIVE)
    }

    /** Мгновенный стоп: _active = null сразу, downloader увидит cancelled на след. chunk */
    fun cancel() {
        if (_active.value == null) return
        cancelled = true
        paused = false
        _active.value = null
        persist()
    }

    fun complete(ctx: Context, item: DownloadItem, uri: String, size: Long) {
        _active.value = null
        paused = false
        cancelled = false
        val newItem = DownloadedItem(
            id = item.id, title = item.request.title, fileName = item.request.fileName,
            uri = uri, sizeBytes = size, completedAt = System.currentTimeMillis(),
            magnet = item.request.magnet
        )
        _history.update { listOf(newItem) + it }
        persist()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            DownloadHistoryStore.save(ctx, _history.value)
        }
    }

    fun removeHistory(ctx: Context, id: String) {
        _history.update { h -> h.filter { it.id != id } }
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            DownloadHistoryStore.save(ctx, _history.value)
        }
    }

    fun requeueForRetry(item: DownloadItem, delayMs: Long) {
        _active.value = null
        val pausedItem = item.copy(status = DownloadItem.Status.PAUSED)
        _queue.update { q -> listOf(pausedItem) + q }
        persist()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            kotlinx.coroutines.delay(delayMs)
            _queue.update { q ->
                q.map { if (it.id == item.id) it.copy(status = DownloadItem.Status.QUEUED) else it }
            }
            persist()
        }
        android.util.Log.d("JecWeb", "requeueForRetry ${item.request.fileName} in ${delayMs}ms")
    }

    fun fail(item: DownloadItem, error: String) {
        _active.value = null
        paused = false
        cancelled = false
        persist()
        android.util.Log.e("JecWeb", "download fail: ${item.request.fileName} — $error")
    }
}
