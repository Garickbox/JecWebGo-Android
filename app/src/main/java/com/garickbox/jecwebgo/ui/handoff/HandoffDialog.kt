package com.garickbox.jecwebgo.ui.handoff

import android.util.Base64
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.garickbox.jecwebgo.data.download.DownloadRepository
import com.garickbox.jecwebgo.data.download.DownloadRequest
import com.garickbox.jecwebgo.domain.TorrentFile
import com.garickbox.jecwebgo.domain.TorrentRepository
import com.garickbox.jecwebgo.ui.common.PlayActions
import com.garickbox.jecwebgo.ui.home.FilePickerDialog
import com.garickbox.jecwebgo.util.TitleUtil
import kotlinx.coroutines.launch

@Composable
fun HandoffDialog(
    magnet: String?,
    torrentBytes: ByteArray?,
    title: String,
    onDismiss: () -> Unit
) {
    if (magnet == null && torrentBytes == null) return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var pickerHash by remember { mutableStateOf<String?>(null) }
    var files by remember { mutableStateOf<List<TorrentFile>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var errText by remember { mutableStateOf<String?>(null) }

    pickerHash?.let { hash ->
        FilePickerDialog(
            title = title,
            files = files,
            loading = loading,
            onDismiss = onDismiss,
            onConfirm = { picked ->
                picked.forEach { f ->
                    DownloadRepository.enqueue(ctx, DownloadRequest(
                        hash = hash,
                        fileIndex = f.id,
                        fileName = f.path.substringAfterLast("/"),
                        title = TitleUtil.cleanTitle(title),
                        magnet = magnet ?: ""
                    ))
                }
                Toast.makeText(ctx, "В очередь: ${picked.size}", Toast.LENGTH_SHORT).show()
                onDismiss()
            }
        )
        return
    }

    Dialog(onDismissRequest = { if (!working) onDismiss() }) {
        Card(Modifier.fillMaxWidth().padding(12.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Магнит / Торрент", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                Text(title, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (magnet != null) "magnet-ссылка" else "torrent (${torrentBytes?.size ?: 0} b)",
                    fontSize = 10.sp, color = MaterialTheme.colorScheme.primary
                )
                errText?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(12.dp))

                if (working) {
                    Box(Modifier.fillMaxWidth().height(60.dp), Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    Button(
                        onClick = {
                            scope.launch {
                                working = true; errText = null
                                val h = addToTs(magnet, torrentBytes)
                                working = false
                                if (h == null) errText = "TorrServer недоступен"
                                else { PlayActions.playHash(ctx, scope, h, title); onDismiss() }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("▶ Играть") }
                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                working = true; errText = null
                                val h = addToTs(magnet, torrentBytes)
                                if (h == null) { working = false; errText = "TorrServer недоступен"; return@launch }
                                loading = true
                                files = TorrentRepository.getFiles(h)
                                loading = false
                                working = false
                                pickerHash = h
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) { Text("⬇ Скачать") }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Отмена") }
                }
            }
        }
    }
}

private suspend fun addToTs(magnet: String?, torrentBytes: ByteArray?): String? {
    return if (magnet != null) TorrentRepository.add(magnet)
    else {
        val b64 = Base64.encodeToString(torrentBytes ?: return null, Base64.NO_WRAP)
        TorrentRepository.addTorrentBase64(b64)
    }
}
