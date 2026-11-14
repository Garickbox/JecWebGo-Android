package com.garickbox.jecwebgo.ui.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.garickbox.jecwebgo.ui.common.SubScreenTopBar
import com.garickbox.jecwebgo.data.store.SettingsStore
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(onBack: () -> Unit = {}) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SubScreenTopBar("Настройки", onBack)
        SettingsScreenBody()
    }
}

@Composable
private fun SettingsScreenBody() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var s by remember { mutableStateOf(SettingsStore.load(ctx)) }
    var dbCount by remember { mutableIntStateOf(0) }
    var dbLastUpdate by remember { mutableStateOf("—") }
    var dbRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        try {
            val items = withContext(Dispatchers.IO) { com.garickbox.jecwebgo.data.network.DataRepository.loadCached(ctx) }
            dbCount = items.size
            val age = withContext(Dispatchers.IO) { com.garickbox.jecwebgo.data.network.DataRepository.cacheAge(ctx) }
            if (age < Long.MAX_VALUE / 2) {
                dbLastUpdate = java.text.SimpleDateFormat("dd.MM HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(System.currentTimeMillis() - age))
            }
        } catch (_: Exception) {}
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Настройки", style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary)

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(12.dp)) {
                Text("📀 База данных", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                if (dbCount > 0) {
                    Text("$dbCount фильмов · $dbLastUpdate",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("не загружена", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (dbRefreshing) return@Button
                        dbRefreshing = true
                        scope.launch {
                            try {
                                val items = withContext(Dispatchers.IO) {
                                    com.garickbox.jecwebgo.data.network.DataRepository.refresh(ctx)
                                }
                                dbCount = items.size
                                val age2 = withContext(Dispatchers.IO) { com.garickbox.jecwebgo.data.network.DataRepository.cacheAge(ctx) }
                                if (age2 < Long.MAX_VALUE / 2) {
                                    dbLastUpdate = java.text.SimpleDateFormat("dd.MM HH:mm", java.util.Locale.getDefault())
                                        .format(java.util.Date(System.currentTimeMillis() - age2))
                                }
                                dbRefreshing = false
                                Toast.makeText(ctx, "Обновлено: ${items.size}", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                dbRefreshing = false
                                Toast.makeText(ctx, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = !dbRefreshing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (dbRefreshing) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (dbRefreshing) "Обновление..." else "🔄 Обновить")
                }
            }
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

        OutlinedTextField(value = s.history_limit.toString(),
            onValueChange = { v -> v.toIntOrNull()?.let { s = s.copy(history_limit = it) } },
            label = { Text("Лимит истории поиска") }, modifier = Modifier.fillMaxWidth(),
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))

        OutlinedTextField(value = s.play_log_limit.toString(),
            onValueChange = { v -> v.toIntOrNull()?.let { s = s.copy(play_log_limit = it) } },
            label = { Text("Лимит истории просмотров") }, modifier = Modifier.fillMaxWidth(),
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))

        OutlinedTextField(value = s.theme, onValueChange = { s = s.copy(theme = it) },
            label = { Text("Тема (dark / oled)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

        HorizontalDivider()
        Text("Воспроизведение", style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary)
        Text("Аппаратное декодирование", fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            listOf(
                Triple("auto", "Auto", "HW→SW"),
                Triple("full", "Full", "HW+DR"),
                Triple("sw", "SW", "только SW")
            ).forEach { (id, label, hint) ->
                val selected = s.hw_mode == id
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                        .clickable { s = s.copy(hw_mode = id) }
                ) {
                    RadioButton(selected = selected, onClick = { s = s.copy(hw_mode = id) })
                    Column {
                        Text(label, fontSize = 13.sp)
                        Text(hint, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        HorizontalDivider()
        Text("Авто-выбор дорожек", style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = s.auto_russian_audio, onCheckedChange = { s = s.copy(auto_russian_audio = it) })
            Text("Авто-русская аудиодорожка")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = s.auto_russian_subs, onCheckedChange = { s = s.copy(auto_russian_subs = it) })
            Text("Авто-русские субтитры")
        }
        HorizontalDivider()
        Button(onClick = {
            SettingsStore.save(ctx, s)
            Toast.makeText(ctx, "Сохранено", Toast.LENGTH_SHORT).show()
        }, modifier = Modifier.fillMaxWidth()) { Text("Сохранить") }
    }
}
