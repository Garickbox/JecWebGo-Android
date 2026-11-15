package com.garickbox.jecwebgo.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garickbox.jecwebgo.data.store.SettingsStore
import com.garickbox.jecwebgo.ui.common.win95.Win95Button
import com.garickbox.jecwebgo.ui.common.win95.Win95Checkbox
import com.garickbox.jecwebgo.ui.common.win95.Win95Radio
import com.garickbox.jecwebgo.ui.common.win95.Win95TextField
import com.garickbox.jecwebgo.ui.common.win95.Win95TitleBar
import com.garickbox.jecwebgo.ui.common.win95.win95Bevel
import com.garickbox.jecwebgo.ui.theme.Win95Colors
import com.garickbox.jecwebgo.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    bottomBarHeight: Dp = 0.dp
) {
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

    Column(
        Modifier
            .fillMaxSize()
            .background(Win95Colors.Teal)
            .navigationBarsPadding()
            .padding(4.dp)
            .padding(bottom = bottomBarHeight)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Win95Colors.Face)
                .win95Bevel(raised = true, thickness = 2.dp)
                .padding(2.dp)
        ) {
            Win95TitleBar(
                title = "Настройки — JecWeb",
                icon = "⚙",
                showClose = false
            )

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(6.dp)
            ) {
                SettingSection("📀 База данных",
                    trailing = if (dbCount > 0) "$dbCount · $dbLastUpdate" else "не загружена"
                ) {
                    Win95Button(
                        onClick = {
                            if (dbRefreshing) return@Win95Button
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
                                    ctx.toast("Обновлено: ${items.size}")
                                } catch (e: Exception) {
                                    dbRefreshing = false
                                    ctx.toast("Ошибка: ${e.message}")
                                }
                            }
                        },
                        enabled = !dbRefreshing,
                        modifier = Modifier.fillMaxWidth().height(24.dp)
                    ) {
                        Text(
                            if (dbRefreshing) "⏳ Обновление..." else "🔄 Обновить",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                SettingSection("⚙ Поведение") {
                    SettingRow {
                        Win95Checkbox(
                            checked = s.enable_bookmarks,
                            onToggle = { s = s.copy(enable_bookmarks = !s.enable_bookmarks) }
                        )
                        Spacer(Modifier.width(6.dp))
                        SettingLabel("Закладки включены")
                    }
                    SettingRow {
                        Win95Checkbox(
                            checked = s.enable_play_log,
                            onToggle = { s = s.copy(enable_play_log = !s.enable_play_log) }
                        )
                        Spacer(Modifier.width(6.dp))
                        SettingLabel("История просмотров включена")
                    }
                    SettingRow {
                        Box(Modifier.width(140.dp)) {
                            SettingLabel("Лимит истории поиска")
                        }
                        Win95TextField(
                            value = s.history_limit.toString(),
                            onValueChange = { v -> v.toIntOrNull()?.let { s = s.copy(history_limit = it) } },
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    SettingRow {
                        Box(Modifier.width(140.dp)) {
                            SettingLabel("Лимит истории просмотров")
                        }
                        Win95TextField(
                            value = s.play_log_limit.toString(),
                            onValueChange = { v -> v.toIntOrNull()?.let { s = s.copy(play_log_limit = it) } },
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                SettingSection("🎬 Воспроизведение") {
                    SettingLabel("Аппаратное декодирование")
                    Spacer(Modifier.height(4.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        listOf(
                            Triple("auto", "Auto", "HW→SW"),
                            Triple("full", "Full", "HW+DR"),
                            Triple("sw", "SW", "только SW")
                        ).forEach { (id, label, hint) ->
                            val selected = s.hw_mode == id
                            Row(
                                Modifier
                                    .weight(1f)
                                    .background(if (selected) Win95Colors.FaceDark else Win95Colors.Face)
                                    .then(if (selected) Modifier.win95Bevel(raised = false, thickness = 2.dp) else Modifier.win95Bevel(raised = true, thickness = 2.dp))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { s = s.copy(hw_mode = id) }
                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Win95Radio(selected = selected, onSelect = { s = s.copy(hw_mode = id) })
                                Spacer(Modifier.width(4.dp))
                                Column {
                                    Text(
                                        label,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = Win95Colors.Text
                                    )
                                    Text(
                                        hint,
                                        fontSize = 8.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Win95Colors.TextDim
                                    )
                                }
                            }
                        }
                    }
                }

                SettingSection("🔊 Авто-выбор дорожек") {
                    SettingRow {
                        Win95Checkbox(
                            checked = s.auto_russian_audio,
                            onToggle = { s = s.copy(auto_russian_audio = !s.auto_russian_audio) }
                        )
                        Spacer(Modifier.width(6.dp))
                        SettingLabel("Авто-русская аудиодорожка")
                    }
                    SettingRow {
                        Win95Checkbox(
                            checked = s.auto_russian_subs,
                            onToggle = { s = s.copy(auto_russian_subs = !s.auto_russian_subs) }
                        )
                        Spacer(Modifier.width(6.dp))
                        SettingLabel("Авто-русские субтитры")
                    }
                }

                Spacer(Modifier.height(6.dp))
            }

            // ═══ [OK] [Отмена] внизу окна (Q2=C) ═══
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Win95Button(
                    onClick = {
                        SettingsStore.save(ctx, s)
                        ctx.toast("Сохранено")
                        onBack()
                    },
                    modifier = Modifier.weight(1f).height(32.dp)
                ) {
                    Text("OK", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
                Win95Button(
                    onClick = onBack,
                    modifier = Modifier.weight(1f).height(32.dp)
                ) {
                    Text("Отмена", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                SettingsStatusSeg("C:\\JecWeb\\config\\", Modifier.weight(2f))
                SettingsStatusSeg("Настройки", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SettingSection(
    title: String,
    trailing: String = "",
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Win95Colors.Face)
            .win95Bevel(raised = true, thickness = 2.dp)
            .padding(6.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Win95Colors.Text
            )
            if (trailing.isNotBlank()) {
                Spacer(Modifier.weight(1f))
                Text(
                    trailing,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Win95Colors.TextDim
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Win95Colors.Gray)
        )
        Spacer(Modifier.height(6.dp))
        content()
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun SettingRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
private fun SettingLabel(text: String) {
    Text(
        text,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        color = Win95Colors.Text,
        maxLines = 1,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
    )
}

@Composable
private fun SettingsStatusSeg(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(Win95Colors.Face)
            .win95Bevel(raised = false, thickness = 1.dp)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            color = Win95Colors.Text,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}
