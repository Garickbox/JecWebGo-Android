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

    fun delete(ctx: Context, item: DownloadedItem) {
        runCatching { ctx.contentResolver.delete(android.net.Uri.parse(item.uri), null, null) }
        DownloadRepository.removeHistory(ctx, item.id)
    }

    fun removeFromList(ctx: Context, item: DownloadedItem) {
        DownloadRepository.removeHistory(ctx, item.id)
    }

    fun open(ctx: Context, item: DownloadedItem) {
        com.garickbox.jecwebgo.ui.player.PendingPlayer.openReady(
            url = item.uri,
            title = item.title,
            hash = "",
            currentIndex = 1,
            fileName = item.fileName
        )
        android.util.Log.d("JecWeb", "local play: ${item.uri} file=${item.fileName}")
    }
}
