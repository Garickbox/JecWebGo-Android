package com.garickbox.jecwebgo.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
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
                        .androidx.compose.foundation.clickable { s = s.copy(hw_mode = id) }
                ) {
                    RadioButton(selected = selected, onClick = { s = s.copy(hw_mode = id) })
                    Column {
                        Text(label, fontSize = 13.sp)
                        Text(hint, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Button(onClick = {
            SettingsStore.save(ctx, s)
            Toast.makeText(ctx, "Сохранено", Toast.LENGTH_SHORT).show()
        }, modifier = Modifier.fillMaxWidth()) { Text("Сохранить") }
    }
}
