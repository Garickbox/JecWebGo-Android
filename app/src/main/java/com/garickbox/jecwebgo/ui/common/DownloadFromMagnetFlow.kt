package com.garickbox.jecwebgo.ui.common

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.garickbox.jecwebgo.data.download.DownloadRepository
import com.garickbox.jecwebgo.data.download.DownloadRequest
import com.garickbox.jecwebgo.domain.TorrentFile
import com.garickbox.jecwebgo.domain.TorrentRepository
import com.garickbox.jecwebgo.ui.home.FilePickerDialog
import com.garickbox.jecwebgo.util.TitleUtil
import kotlinx.coroutines.delay

@Composable
fun DownloadFromMagnetFlow(
    magnet: String,
    title: String,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    var pickerHash by remember { mutableStateOf<String?>(null) }
    var files by remember { mutableStateOf<List<TorrentFile>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var phase by remember { mutableStateOf("Добавление торрента…") }

    LaunchedEffect(magnet) {
        loading = true
        error = null
        phase = "Добавление торрента…"
        val h = TorrentRepository.add(magnet)
        if (h == null) {
            error = "Не удалось добавить торрент (TorrServer недоступен / магнет мёртв)"
            loading = false
            return@LaunchedEffect
        }
        phase = "Получение списка файлов…"
        var got: List<TorrentFile> = emptyList()
        val deadline = System.currentTimeMillis() + 30000L
        while (System.currentTimeMillis() < deadline && got.isEmpty()) {
            got = TorrentRepository.getFiles(h)
            if (got.isEmpty()) delay(1000)
        }
        if (got.isEmpty()) {
            error = "Файлы не найдены (нет пиров / магнет мёртв)"
            loading = false
            return@LaunchedEffect
        }
        files = got
        pickerHash = h
        loading = false
    }

    if (error != null) {
        Dialog(onDismissRequest = onDismiss) {
            Card(Modifier.fillMaxWidth().padding(8.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Ошибка", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Закрыть") }
                }
            }
        }
        return
    }

    if (pickerHash != null) {
        FilePickerDialog(
            title = title,
            files = files,
            loading = false,
            onDismiss = onDismiss,
            onConfirm = { picked ->
                val h = pickerHash!!
                picked.forEach { f ->
                    DownloadRepository.enqueue(ctx, DownloadRequest(
                        hash = h,
                        fileIndex = f.id,
                        fileName = f.path.substringAfterLast("/"),
                        title = TitleUtil.cleanTitle(title),
                        magnet = magnet,
                        totalBytes = f.length
                    ))
                }
                Toast.makeText(ctx, "В очередь: ${picked.size}", Toast.LENGTH_SHORT).show()
                onDismiss()
            }
        )
    } else {
        Dialog(onDismissRequest = onDismiss) {
            Card(Modifier.fillMaxWidth().padding(8.dp)) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(phase, fontSize = 12.sp)
                }
            }
        }
    }
}
