package com.garickbox.jecwebgo.data.download

import android.content.Context
import com.garickbox.jecwebgo.service.DownloadService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

object DownloadRepository {
    private val _active = MutableStateFlow<DownloadItem?>(null)
    val active: StateFlow<DownloadItem?> = _active.asStateFlow()

    private val _queue = MutableStateFlow<List<DownloadItem>>(emptyList())
    val queue: StateFlow<List<DownloadItem>> = _queue.asStateFlow()

    private val _history = MutableStateFlow<List<DownloadedItem>>(emptyList())
    val history: StateFlow<List<DownloadedItem>> = _history.asStateFlow()

    fun enqueue(ctx: Context, request: DownloadRequest): String {
        val id = UUID.randomUUID().toString()
        _queue.value = _queue.value + DownloadItem(id = id, request = request)
        DownloadService.start(ctx)
        return id
    }

    fun next(): DownloadItem? {
        val n = _queue.value.firstOrNull() ?: return null
        _queue.value = _queue.value.drop(1)
        val item = n.copy(status = DownloadItem.Status.ACTIVE)
        _active.value = item
        return item
    }

    fun updateProgress(downloaded: Long, speed: Long) {
        val cur = _active.value ?: return
        _active.value = cur.copy(downloadedBytes = downloaded, speedBps = speed)
    }

    fun complete(item: DownloadItem, uri: String, size: Long) {
        _active.value = null
        _history.value = listOf(
            DownloadedItem(
                id = item.id,
                title = item.request.title,
                fileName = item.request.fileName,
                uri = uri,
                sizeBytes = size,
                completedAt = System.currentTimeMillis()
            )
        ) + _history.value
    }

    fun fail(item: DownloadItem, error: String) {
        _active.value = null
    }
}
