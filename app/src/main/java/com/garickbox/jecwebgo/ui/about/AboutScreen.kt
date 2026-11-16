package com.garickbox.jecwebgo.ui.about

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garickbox.jecwebgo.data.network.DataRepository
import com.garickbox.jecwebgo.ui.common.win95.Win95Button
import com.garickbox.jecwebgo.ui.common.win95.Win95TitleBar
import com.garickbox.jecwebgo.ui.common.win95.win95Bevel
import com.garickbox.jecwebgo.ui.theme.Win95Colors
import com.garickbox.jecwebgo.util.toast
import kotlinx.coroutines.launch

@Composable
fun AboutScreen(
    onBack: () -> Unit = {},
    bottomBarHeight: Dp = 0.dp
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    var version by remember { mutableStateOf("?") }
    var dbCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        try {
            val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            version = pi.versionName ?: "?"
            dbCount = DataRepository.loadCached(ctx).size
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
                title = "О программе — JecWeb",
                icon = "ℹ",
                onClose = onBack,
                showClose = true
            )

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(6.dp)
            ) {
                // ═══ Шапка ═══
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Win95Colors.Face)
                        .win95Bevel(raised = true, thickness = 2.dp)
                        .padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🎬", fontSize = 42.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "JecWeb Go",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Win95Colors.Text
                    )
                    Text(
                        "Версия $version",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Win95Colors.TextDim
                    )
                }
                Spacer(Modifier.height(4.dp))

                // ═══ Описание ═══
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Win95Colors.Face)
                        .win95Bevel(raised = true, thickness = 2.dp)
                        .padding(6.dp)
                ) {
                    Text(
                        "📝 Описание",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Win95Colors.Text
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Медиацентр: поиск торрентов, просмотр, скачивание, AI-ассистент.",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Win95Colors.Text
                    )
                }
                Spacer(Modifier.height(4.dp))

                // ═══ GitHub ═══
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Win95Colors.Face)
                        .win95Bevel(raised = true, thickness = 2.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Garickbox/JecWebGo-Android"))
                            runCatching { ctx.startActivity(i) }
                        }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📦", fontSize = 20.sp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "GitHub",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Win95Colors.Text
                        )
                        Text(
                            "github.com/Garickbox/JecWebGo-Android",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Win95Colors.TextDim
                        )
                    }
                    Text("↗", fontSize = 16.sp, color = Win95Colors.Navy)
                }
                Spacer(Modifier.height(4.dp))

                // ═══ База данных ═══
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Win95Colors.Face)
                        .win95Bevel(raised = true, thickness = 2.dp)
                        .padding(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "📊 База данных",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Win95Colors.Text
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (dbCount > 0) "$dbCount" else "—",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Win95Colors.TextDim
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Win95Button(
                        onClick = {
                            if (refreshing) return@Win95Button
                            refreshing = true
                            scope.launch {
                                val items = DataRepository.refresh(ctx)
                                dbCount = items.size
                                refreshing = false
                                ctx.toast(if (items.isNotEmpty()) "Обновлено: ${items.size}" else "Не удалось")
                            }
                        },
                        enabled = !refreshing,
                        modifier = Modifier.fillMaxWidth().height(24.dp)
                    ) {
                        Text(
                            if (refreshing) "⏳ Обновление..." else "🔄 Обновить базу",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))

                // ═══ Футер ═══
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Made with ❤ in Termux",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Win95Colors.TextDim
                    )
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Win95Button(
                    onClick = onBack,
                    modifier = Modifier.weight(1f).height(32.dp)
                ) {
                    Text("OK", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                AboutStatusSeg("C:\\JecWeb\\", Modifier.weight(2f))
                AboutStatusSeg("О программе", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AboutStatusSeg(text: String, modifier: Modifier = Modifier) {
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
