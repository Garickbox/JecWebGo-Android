package com.garickbox.jecwebgo.data.download

import android.content.Context
import com.garickbox.jecwebgo.data.store.DownloadHistoryStore
import com.garickbox.jecwebgo.data.store.DownloadStateStore
import com.garickbox.jecwebgo.data.store.PersistedDownload
import com.garickbox.jecwebgo.service.DownloadService
import kotlinx.coroutines.flow.MutableStateFlow
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
                DownloadItem(id = p.id, request = p.request, totalBytes = p.request.totalBytes,
                    status = DownloadItem.Status.QUEUED,
                    existingUri = p.uri.takeIf { it.isNotBlank() })
            }
            DownloadService.start(ctx)
        }
    }

    private fun persist() {
        val ctx = appCtx ?: return
        val items = (listOfNotNull(_active.value) + _queue.value).map {
            PersistedDownload(it.id, it.request, it.existingUri ?: "")
        }
        DownloadStateStore.save(ctx, items)
    }

    fun enqueue(ctx: Context, request: DownloadRequest): String {
        val id = UUID.randomUUID().toString()
        _queue.value = _queue.value + DownloadItem(id = id, request = request, totalBytes = request.totalBytes)
        persist()
        DownloadService.start(ctx)
        return id
    }

    fun groupKeyOf(item: DownloadItem): String =
        item.request.title + "|" + item.request.relPath.substringBeforeLast("/", "")

    fun moveGroupInQueue(groupKey: String, neighborKey: String) {
        if (groupKey == neighborKey || groupKey.isBlank() || neighborKey.isBlank()) return
        val q = _queue.value.toMutableList()
        val groupIdx = q.indices.filter { groupKeyOf(q[it]) == groupKey }
        val neighIdx = q.indices.filter { groupKeyOf(q[it]) == neighborKey }
        if (groupIdx.isEmpty() || neighIdx.isEmpty()) return
        val groupItems = groupIdx.map { q[it] }
        val neighItems = neighIdx.map { q[it] }
        val allIdx = (groupIdx + neighIdx).sorted()
        val combined = if (groupIdx.first() < neighIdx.first()) groupItems + neighItems else neighItems + groupItems
        allIdx.forEachIndexed { i, idx -> q[idx] = combined[i] }
        _queue.value = q
        persist()
    }

    fun removeGroupFromQueue(groupKey: String) {
        _queue.value = _queue.value.filter { groupKeyOf(it) != groupKey }
        persist()
    }

    fun moveInQueue(from: Int, to: Int) {
        val q = _queue.value.toMutableList()
        if (from !in q.indices || to !in q.indices || from == to) return
        val item = q.removeAt(from)
        q.add(to, item)
        _queue.value = q
        persist()
    }

    fun removeFromQueue(id: String) {
        _queue.value = _queue.value.filter { it.id != id }
        persist()
    }

    fun next(): DownloadItem? {
        val n = _queue.value.firstOrNull() ?: return null
        _queue.value = _queue.value.drop(1)
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
            uri = uri, sizeBytes = size, completedAt = System.currentTimeMillis()
        )
        _history.value = listOf(newItem) + _history.value
        persist()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            DownloadHistoryStore.save(ctx, _history.value)
        }
    }

    fun removeHistory(ctx: Context, id: String) {
        _history.value = _history.value.filter { it.id != id }
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            DownloadHistoryStore.save(ctx, _history.value)
        }
    }

    fun fail(item: DownloadItem, error: String) {
        _active.value = null
        paused = false
        cancelled = false
        persist()
        android.util.Log.e("JecWeb", "download fail: ${item.request.fileName} — $error")
    }
}
