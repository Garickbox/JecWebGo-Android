package com.garickbox.jecwebgo.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.garickbox.jecwebgo.data.store.SettingsStore
import com.garickbox.jecwebgo.domain.TorrentRepository
import com.garickbox.jecwebgo.domain.model.Settings
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    var s by remember { mutableStateOf(SettingsStore.load(ctx)) }
    val scope = rememberCoroutineScope()
    var measuring by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Настройки", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

        OutlinedTextField(
            value = s.tv_ip, onValueChange = { s = s.copy(tv_ip = it) },
            label = { Text("IP телевизора (TorrServer :8090)") },
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )

        OutlinedTextField(
            value = s.search_provider, onValueChange = { s = s.copy(search_provider = it) },
            label = { Text("Провайдер (jacred / knaben)") },
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )

        HorizontalDivider()

        Text("SuperBlitz", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = s.auto_speed_detect, onCheckedChange = { s = s.copy(auto_speed_detect = it) })
            Text("Автоопределение скорости")
        }

        if (s.auto_speed_detect) {
            val level = s.superblitz_aggressiveness
            Text(
                "Уровень $level: " + when (level) {
                    1 -> "низкий (30MB / 3 потока)"
                    2 -> "умеренный (40MB / 4)"
                    3 -> "сбалансированный (50MB / 6)"
                    4 -> "высокий (80MB / 8)"
                    5 -> "макс (150MB / 10)"
                    else -> "—"
                },
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = {
                if (measuring) return@Button
                measuring = true
                scope.launch {
                    val activeHash = TorrentRepository.firstHash()
                    if (activeHash == null) {
                        Toast.makeText(ctx, "Запустите любой фильм для замера", Toast.LENGTH_LONG).show()
                        measuring = false
                        return@launch
                    }
                    val level = TorrentRepository.measureSpeed(activeHash)
                    if (level > 0) {
                        s = s.copy(superblitz_aggressiveness = level)
                        SettingsStore.save(ctx, s)
                        Toast.makeText(ctx, "Уровень $level установлен", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(ctx, "Замер не удался", Toast.LENGTH_SHORT).show()
                    }
                    measuring = false
                }
            }, enabled = !measuring) {
                if (measuring) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("⚡ Определить сейчас")
            }
        } else {
            OutlinedTextField(value = s.preload_mb.toString(), onValueChange = { v -> v.toIntOrNull()?.let { s = s.copy(preload_mb = it) } },
                label = { Text("Предзагрузка МБ") }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(value = s.preload_threads.toString(), onValueChange = { v -> v.toIntOrNull()?.let { s = s.copy(preload_threads = it) } },
                label = { Text("Потоков") }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }

        HorizontalDivider()

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = s.enable_bookmarks, onCheckedChange = { s = s.copy(enable_bookmarks = it) })
            Text("Закладки включены")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = s.enable_play_log, onCheckedChange = { s = s.copy(enable_play_log = it) })
            Text("История просмотров включена")
        }

        OutlinedTextField(value = s.history_limit.toString(), onValueChange = { v -> v.toIntOrNull()?.let { s = s.copy(history_limit = it) } },
            label = { Text("Лимит истории поиска") }, modifier = Modifier.fillMaxWidth(),
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))

        OutlinedTextField(value = s.play_log_limit.toString(), onValueChange = { v -> v.toIntOrNull()?.let { s = s.copy(play_log_limit = it) } },
            label = { Text("Лимит истории просмотров") }, modifier = Modifier.fillMaxWidth(),
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))

        OutlinedTextField(value = s.theme, onValueChange = { s = s.copy(theme = it) },
            label = { Text("Тема (dark / oled)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

        Button(onClick = {
            SettingsStore.save(ctx, s)
            Toast.makeText(ctx, "Сохранено", Toast.LENGTH_SHORT).show()
        }, modifier = Modifier.fillMaxWidth()) { Text("Сохранить") }
    }
}
