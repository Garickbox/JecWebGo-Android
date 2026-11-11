package com.garickbox.jecwebgo.ui.download

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.garickbox.jecwebgo.data.download.DownloadItem
import com.garickbox.jecwebgo.data.download.DownloadedItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(vm: DownloadViewModel = viewModel()) {
    val ctx = LocalContext.current
    val active by vm.active.collectAsState()
    val queue by vm.queue.collectAsState()
    val history by vm.history.collectAsState()
    val fmt = remember { SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text("Загрузки", fontWeight = FontWeight.SemiBold)
                    Text("${queue.size + (if (active != null) 1 else 0)} в очереди · ${history.size} готово",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            })
        }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (active != null) {
                item { SectionTitle("Сейчас качается") }
                item { ActiveCard(active!!) }
            }
            if (queue.isNotEmpty()) {
                item { Spacer(Modifier.height(8.dp)); SectionTitle("В очереди (${queue.size})") }
                items(queue) { q -> QueueCard(q) }
            }
            if (history.isNotEmpty()) {
                item { Spacer(Modifier.height(8.dp)); SectionTitle("Скачано (${history.size})") }
                items(history) { h -> HistoryCard(h, fmt, onOpen = { vm.open(ctx, h.uri) }) }
            }
            if (active == null && queue.isEmpty() && history.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📥", fontSize = 48.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("Здесь появятся загрузки", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Text("Открой фильм → ⬇ Скачать", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable private fun ActiveCard(item: DownloadItem) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(item.request.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Text(item.request.fileName, fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { item.progress }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            Row {
                Text("${(item.progress * 100).toInt()}%", fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                Text(formatSize(item.downloadedBytes) + " / " + formatSize(item.totalBytes), fontSize = 11.sp)
                Spacer(Modifier.width(8.dp))
                Text(formatSpeed(item.speedBps), fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable private fun QueueCard(item: DownloadItem) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("⏳", fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(item.request.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.request.fileName, fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable private fun HistoryCard(item: DownloadedItem, fmt: SimpleDateFormat, onOpen: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("✅", fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatSize(item.sizeBytes) + " · " + fmt.format(Date(item.completedAt)),
                    fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("▶", fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun formatSize(b: Long): String = when {
    b >= 1L shl 30 -> "%.2f GB".format(b / 1073741824.0)
    b >= 1L shl 20 -> "%.0f MB".format(b / 1048576.0)
    b >= 1024 -> "%.0f KB".format(b / 1024.0)
    else -> "$b B"
}
private fun formatSpeed(bps: Long): String = when {
    bps >= 1048576 -> "%.1f MB/s".format(bps / 1048576.0)
    bps >= 1024 -> "%.0f KB/s".format(bps / 1024.0)
    else -> "$bps B/s"
}
