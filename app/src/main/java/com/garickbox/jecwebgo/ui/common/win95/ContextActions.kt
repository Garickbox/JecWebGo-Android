package com.garickbox.jecwebgo.ui.common.win95

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.garickbox.jecwebgo.util.toast

fun copyMagnetToClipboard(ctx: Context, magnet: String) {
    if (magnet.isBlank()) {
        ctx.toast("Magnet пуст")
        return
    }
    try {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("magnet", magnet))
        ctx.toast("Magnet скопирован")
    } catch (e: Exception) {
        ctx.toast("Не удалось скопировать: ${e.message?.take(40)}")
    }
}

fun shareMagnet(ctx: Context, magnet: String, title: String) {
    if (magnet.isBlank()) {
        ctx.toast("Magnet пуст")
        return
    }
    try {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "$title\n\n$magnet")
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(i, "Поделиться magnet").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(chooser)
    } catch (e: Exception) {
        ctx.toast("Не удалось поделиться: ${e.message?.take(40)}")
    }
}

fun openDownloadsFolder(ctx: Context) {
    val authority = "com.android.externalstorage.documents"
    val docId = "primary:Download/JecWeb"
    val uri = try {
        DocumentsContract.buildDocumentUri(authority, docId)
    } catch (_: Exception) { return }
    try {
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(i)
    } catch (_: Exception) {
        ctx.toast("Не удалось открыть папку")
    }
}

fun openFile(ctx: Context, uriStr: String) {
    if (uriStr.isBlank()) {
        ctx.toast("URI файла пуст")
        return
    }
    try {
        val uri = Uri.parse(uriStr)
        val mime = ctx.contentResolver.getType(uri) ?: "video/*"
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(i)
    } catch (e: Exception) {
        ctx.toast("Не удалось открыть: ${e.message?.take(40)}")
    }
}
