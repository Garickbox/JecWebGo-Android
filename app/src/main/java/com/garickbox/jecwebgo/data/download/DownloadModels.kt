package com.garickbox.jecwebgo.data.download

import kotlinx.serialization.Serializable

@Serializable
data class DownloadRequest(
    val hash: String,
    val fileIndex: Int,
    val fileName: String,
    val title: String,
    val magnet: String,
    val totalBytes: Long = 0L
)

data class DownloadItem(
    val id: String,
    val request: DownloadRequest,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val speedBps: Long = 0L,
    val status: Status = Status.QUEUED,
    val error: String? = null
) {
    enum class Status { QUEUED, ACTIVE, PAUSED, COMPLETED, FAILED, CANCELLED }
    val progress: Float
        get() = if (totalBytes <= 0L) 0f else (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
}

data class DownloadedItem(
    val id: String,
    val title: String,
    val fileName: String,
    val uri: String,
    val sizeBytes: Long,
    val completedAt: Long
)
