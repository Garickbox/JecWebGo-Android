package com.garickbox.jecwebgo.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.garickbox.jecwebgo.domain.TorrentFile

@Composable
fun FilePickerDialog(
    title: String,
    files: List<TorrentFile>,
    loading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (List<TorrentFile>) -> Unit
) {
    val selected = remember { mutableStateMapOf<Int, Boolean>() }

    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth().padding(8.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text("Выбор файлов", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(title, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(Modifier.height(8.dp))

                if (loading) {
                    Box(Modifier.fillMaxWidth().height(120.dp), Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (files.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(80.dp), Alignment.Center) {
                        Text("Файлы не найдены", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    // Группировка по папкам
                    data class FolderGroup(val dir: String, val files: List<TorrentFile>) {
                        val totalSize: Long get() = files.sumOf { it.length }
                    }
                    val folders = remember(files) {
                        files.groupBy { it.path.substringBeforeLast("/", "") }
                            .map { (dir, list) -> FolderGroup(dir, list.sortedBy { it.path }) }
                            .sortedBy { it.dir }
                    }
                    val hasSubdirs = folders.size > 1 || folders.firstOrNull()?.dir?.isNotBlank() == true
                    var expanded by remember { mutableStateOf<Set<String>>(folders.map { it.dir }.toSet()) }

                    fun selectFolder(fg: FolderGroup, on: Boolean) {
                        fg.files.forEach { selected[it.id] = on }
                    }
                    fun folderAllSelected(fg: FolderGroup): Boolean =
                        fg.files.all { selected[it.id] == true }
                    fun folderSomeSelected(fg: FolderGroup): Boolean =
                        fg.files.any { selected[it.id] == true }

                    // Верхние кнопки
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            files.forEach { selected[it.id] = true }
                        }) { Text("Всё", fontSize = 12.sp) }
                        TextButton(onClick = { selected.clear() }) { Text("Ничего", fontSize = 12.sp) }
                        if (hasSubdirs) {
                            TextButton(onClick = {
                                files.forEach { selected[it.id] = true }
                            }) {
                                Text("Скачать папкой", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Text("${selected.count { it.value }} / ${files.size}", fontSize = 12.sp)
                    }

                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (hasSubdirs) {
                            items(folders, key = { "folder_${it.dir}" }) { fg ->
                                val isExpanded = fg.dir in expanded
                                val allSel = folderAllSelected(fg)
                                val someSel = folderSomeSelected(fg)
                                Row(
                                    Modifier.fillMaxWidth()
                                        .clickable {
                                            expanded = if (isExpanded) expanded - fg.dir else expanded + fg.dir
                                        }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(if (isExpanded) "▼" else "▶", fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(end = 6.dp))
                                    androidx.compose.material3.TriStateCheckbox(
                                        state = when {
                                            allSel -> androidx.compose.ui.state.ToggleableState.On
                                            someSel -> androidx.compose.ui.state.ToggleableState.Indeterminate
                                            else -> androidx.compose.ui.state.ToggleableState.Off
                                        },
                                        onClick = { selectFolder(fg, !allSel) }
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            "📁 ${fg.dir.ifBlank { "Корень" }}",
                                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis
                                        )
                                        Text("${fg.files.size} файлов · ${formatSize(fg.totalSize)}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (isExpanded) {
                                    fg.files.forEach { f ->
                                        val isSel = selected[f.id] == true
                                        Row(
                                            Modifier.fillMaxWidth().clickable { selected[f.id] = !isSel }
                                                .padding(start = 32.dp, top = 2.dp, bottom = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(checked = isSel,
                                                onCheckedChange = { selected[f.id] = it })
                                            Column(Modifier.weight(1f)) {
                                                Text(f.path.substringAfterLast("/"),
                                                    fontSize = 11.sp, maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis)
                                                Text(formatSize(f.length), fontSize = 9.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            items(files, key = { it.id }) { f ->
                                val isSel = selected[f.id] == true
                                Row(
                                    Modifier.fillMaxWidth().clickable { selected[f.id] = !isSel }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(checked = isSel, onCheckedChange = { selected[f.id] = it })
                                    Column(Modifier.weight(1f)) {
                                        Text(f.path.substringAfterLast("/"), fontSize = 12.sp, maxLines = 1,
                                            overflow = TextOverflow.Ellipsis)
                                        Text(formatSize(f.length), fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Отмена") }
                    Button(
                        onClick = {
                            val picked = files.filter { selected[it.id] == true }
                            onConfirm(picked)
                        },
                        modifier = Modifier.weight(1f),
                        enabled = selected.count { it.value } > 0
                    ) { Text("В очередь") }
                }
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
