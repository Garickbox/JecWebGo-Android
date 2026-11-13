package com.garickbox.jecwebgo.ui.download

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.garickbox.jecwebgo.data.download.DownloadRepository
import com.garickbox.jecwebgo.data.download.DownloadedItem
import com.garickbox.jecwebgo.ui.common.PlayActions
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
    val scope = rememberCoroutineScope()
    var deleteDialogFor by remember { mutableStateOf<DownloadedItem?>(null) }
    var expandedGroups by remember { mutableStateOf<Set<String>>(emptySet()) }

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
        // Проверка: уведомления выключены — предупредить
        var notifWarned by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            try {
                val nm = ctx.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                if (!nm.areNotificationsEnabled() && !notifWarned) {
                    notifWarned = true
                }
            } catch (_: Exception) {}
        }
        if (notifWarned) {
            AlertDialog(
                onDismissRequest = { notifWarned = false },
                title = { Text("Уведомления отключены") },
                text = { Text("Без уведомлений загрузка может остановиться в фоне на MIUI. Включите уведомления для JecWeb Go.") },
                confirmButton = {
                    TextButton(onClick = {
                        notifWarned = false
                        try {
                            val i = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            ctx.startActivity(i)
                        } catch (_: Exception) {}
                    }) { Text("Открыть настройки") }
                },
                dismissButton = {
                    TextButton(onClick = { notifWarned = false }) { Text("Позже") }
                }
            )
        }
        val queueGroups = remember(queue) {
            queue.groupBy { DownloadRepository.groupKeyOf(it) }
        }
        val groupKeys = remember(queueGroups) { queueGroups.keys.toList() }
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (active != null) {
                item { SectionTitle("Сейчас качается") }
                item { ActiveCard(
                    item = active!!,
                    onPause = { DownloadRepository.pause() },
                    onResume = { DownloadRepository.resume() },
                    onCancel = { DownloadRepository.cancel() },
                    onWatch = { active?.let { PlayActions.playHash(ctx, scope, it.request.hash, it.request.title) } }
                ) }
            }
            if (queue.isNotEmpty()) {
                item { Spacer(Modifier.height(8.dp)); SectionTitle("В очереди (${queue.size})") }
                itemsIndexed(groupKeys) { gIdx, key ->
                    val items = queueGroups[key] ?: emptyList()
                    if (items.isEmpty()) return@itemsIndexed
                    val first = items.first()
                    val parentDir = first.request.relPath.substringBeforeLast("/", "")
                    val isExpanded = key in expandedGroups || items.size == 1
                    val prevKey = groupKeys.getOrNull(gIdx - 1)
                    val nextKey = groupKeys.getOrNull(gIdx + 1)
                    QueueGroupCard(
                        title = first.request.title,
                        parentDir = parentDir,
                        files = items,
                        groupIndex = gIdx,
                        totalGroups = groupKeys.size,
                        isExpanded = isExpanded,
                        canExpand = items.size > 1,
                        onToggleExpand = {
                            expandedGroups = if (key in expandedGroups) expandedGroups - key else expandedGroups + key
                        },
                        onMoveGroupUp = { prevKey?.let { DownloadRepository.moveGroupInQueue(key, it) } },
                        onMoveGroupDown = { nextKey?.let { DownloadRepository.moveGroupInQueue(key, it) } },
                        onRemoveGroup = { DownloadRepository.removeGroupFromQueue(key) },
                        onRemoveFile = { f -> DownloadRepository.removeFromQueue(f.id) }
                    )
                }
            }
            if (history.isNotEmpty()) {
                item { Spacer(Modifier.height(8.dp)); SectionTitle("Скачано (${history.size})") }
                items(history) { h -> HistoryCard(h, fmt,
                    onOpen = { vm.open(ctx, h) },
                    onDelete = { deleteDialogFor = h }
                ) }
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
    deleteDialogFor?.let { h ->
        DeleteHistoryDialog(
            item = h,
            onDismiss = { deleteDialogFor = null },
            onRemoveFromList = { vm.removeFromList(ctx, h); deleteDialogFor = null },
            onDeleteFile = { vm.delete(ctx, h); deleteDialogFor = null }
        )
    }
}

@Composable private fun DeleteHistoryDialog(
    item: DownloadedItem,
    onDismiss: () -> Unit,
    onRemoveFromList: () -> Unit,
    onDeleteFile: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Удалить?") },
        text = { Text(item.title + "\n\nФайл: " + item.fileName) },
        confirmButton = {
            TextButton(onClick = onDeleteFile) {
                Text("Удалить и файл", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onRemoveFromList) { Text("Только из списка") }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        }
    )
}

@Composable private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable private fun ActiveCard(
    item: DownloadItem,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onWatch: () -> Unit
) {
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
                if (item.status == DownloadItem.Status.PAUSED)
                    Text("⏸", fontSize = 11.sp, color = MaterialTheme.colorScheme.tertiary)
                else
                    Text(formatSpeed(item.speedBps), fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (item.status == DownloadItem.Status.PAUSED) {
                    Button(onClick = onResume, modifier = Modifier.weight(1f).height(38.dp), contentPadding = PaddingValues(0.dp)) {
                        Text("▶ Дальше", fontSize = 11.sp)
                    }
                } else {
                    OutlinedButton(onClick = onPause, modifier = Modifier.weight(1f).height(38.dp), contentPadding = PaddingValues(0.dp)) {
                        Text("⏸ Пауза", fontSize = 11.sp)
                    }
                }
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).height(38.dp), contentPadding = PaddingValues(0.dp)) {
                    Text("⏹ Стоп", fontSize = 11.sp)
                }
                if (item.downloadedBytes > 5L * 1024 * 1024) {
                    Button(onClick = onWatch, modifier = Modifier.weight(1f).height(38.dp), contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)) {
                        Text("▶ Смотреть", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable private fun QueueGroupCard(
    title: String,
    parentDir: String,
    files: List<DownloadItem>,
    groupIndex: Int,
    totalGroups: Int,
    isExpanded: Boolean,
    canExpand: Boolean,
    onToggleExpand: () -> Unit,
    onMoveGroupUp: () -> Unit,
    onMoveGroupDown: () -> Unit,
    onRemoveGroup: () -> Unit,
    onRemoveFile: (DownloadItem) -> Unit
) {
    val totalSize = files.sumOf { it.request.totalBytes }
    Card(Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (canExpand) {
                    Text(
                        if (isExpanded) "▼" else "▶",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { onToggleExpand() }.padding(end = 6.dp)
                    )
                } else {
                    Text("⏳", fontSize = 18.sp)
                    Spacer(Modifier.width(6.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        title.ifBlank { "?" } + if (parentDir.isNotBlank()) " / ${parentDir.substringAfterLast("/")}" else "",
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (files.size == 1) files.first().request.fileName
                        else "${files.size} файлов · ${formatSize(totalSize)}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(
                    onClick = onMoveGroupUp,
                    enabled = groupIndex > 0,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) { Text("↑", fontSize = 16.sp) }
                TextButton(
                    onClick = onMoveGroupDown,
                    enabled = groupIndex < totalGroups - 1,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) { Text("↓", fontSize = 16.sp) }
                TextButton(
                    onClick = onRemoveGroup,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) { Text("✕", fontSize = 16.sp) }
            }
            if (canExpand && isExpanded) {
                HorizontalDivider(Modifier.padding(horizontal = 10.dp))
                files.forEach { f ->
                    Row(
                        Modifier.fillMaxWidth().padding(start = 26.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                f.request.fileName,
                                fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                formatSize(f.request.totalBytes),
                                fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = { onRemoveFile(f) },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) { Text("✕", fontSize = 14.sp) }
                    }
                }
            }
        }
    }
}

@Composable private fun HistoryCard(item: DownloadedItem, fmt: SimpleDateFormat, onOpen: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("✅", fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f).clickable(onClick = onOpen)) {
                Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatSize(item.sizeBytes) + " · " + fmt.format(Date(item.completedAt)),
                    fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onOpen, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                Text("▶", fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
            }
            TextButton(onClick = onDelete, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                Text("✕", fontSize = 16.sp)
            }
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
