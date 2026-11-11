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
import com.garickbox.jecwebgo.data.store.SettingsStore

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    var s by remember { mutableStateOf(SettingsStore.load(ctx)) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Настройки", style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary)

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

        Button(onClick = {
            SettingsStore.save(ctx, s)
            Toast.makeText(ctx, "Сохранено", Toast.LENGTH_SHORT).show()
        }, modifier = Modifier.fillMaxWidth()) { Text("Сохранить") }
    }
}
