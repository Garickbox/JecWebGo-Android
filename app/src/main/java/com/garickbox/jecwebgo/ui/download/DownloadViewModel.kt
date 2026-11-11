package com.garickbox.jecwebgo.ui.download

import androidx.lifecycle.ViewModel

import android.content.Context
import com.garickbox.jecwebgo.data.download.DownloadItem
import com.garickbox.jecwebgo.data.download.DownloadRepository
import com.garickbox.jecwebgo.data.download.DownloadedItem
import kotlinx.coroutines.flow.StateFlow

class DownloadViewModel : ViewModel() {
    val active: StateFlow<DownloadItem?> = DownloadRepository.active
    val queue: StateFlow<List<DownloadItem>> = DownloadRepository.queue
    val history: StateFlow<List<DownloadedItem>> = DownloadRepository.history

    fun open(ctx: Context, uri: String) {
        runCatching {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(android.net.Uri.parse(uri), "video/mp4")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(intent)
        }
    }
}
