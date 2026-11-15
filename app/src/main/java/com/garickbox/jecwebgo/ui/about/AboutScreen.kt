package com.garickbox.jecwebgo.ui.about

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garickbox.jecwebgo.data.network.DataRepository
import kotlinx.coroutines.launch
import com.garickbox.jecwebgo.util.toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit = {}) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    var version by remember { mutableStateOf("?") }
    val cfg = LocalConfiguration.current
    val isLandscape = cfg.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    LaunchedEffect(Unit) {
        try {
            val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            version = pi.versionName ?: "?"
        } catch (_: Exception) {}
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = { com.garickbox.jecwebgo.ui.common.SubScreenTopBar("О приложении", onBack) }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.TopCenter) {
        if (isLandscape) {
            // LANDSCAPE: 2 колонки — визитка слева, действия справа
            Row(
                Modifier.fillMaxSize().widthIn(max = 900.dp).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(end = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("🎬", fontSize = 52.sp)
                    Text("JecWeb Go", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Версия $version", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(4.dp))
                    Text("Made with ❤️ in Termux", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Медиацентр: поиск торрентов, просмотр, скачивание, AI-ассистент.", fontSize = 12.sp)
                    Card(Modifier.fillMaxWidth().clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Garickbox/JecWebGo-Android"))
                        runCatching { ctx.startActivity(intent) }
                    }) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("📦", fontSize = 20.sp)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("GitHub", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("github.com/Garickbox/JecWebGo-Android", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("↗", fontSize = 18.sp)
                        }
                    }
                    Button(onClick = {
                        if (refreshing) return@Button
                        refreshing = true
                        scope.launch {
                            val items = DataRepository.refresh(ctx)
                            refreshing = false
                            ctx.toast(if (items.isNotEmpty()) "Обновлено: ${items.size}" else "Не удалось")
                        }
                    }, modifier = Modifier.fillMaxWidth().height(44.dp), enabled = !refreshing) {
                        if (refreshing) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(if (refreshing) "Обновление..." else "🔄 Обновить базу")
                    }
                }
            }
        } else {
            // PORTRAIT: компактнее (было emoji 64sp, spacer 24)
            Column(
                Modifier.fillMaxWidth().widthIn(max = 500.dp).padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 88.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Spacer(Modifier.height(4.dp))
                Text("🎬", fontSize = 48.sp)
                Text("JecWeb Go", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Версия $version", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                HorizontalDivider()
                Text("Медиацентр: поиск торрентов, просмотр, скачивание, AI-ассистент.",
                    fontSize = 12.sp, textAlign = TextAlign.Center)
                Card(Modifier.fillMaxWidth().clickable {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Garickbox/JecWebGo-Android"))
                    runCatching { ctx.startActivity(intent) }
                }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("📦", fontSize = 20.sp)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("GitHub", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("github.com/Garickbox/JecWebGo-Android", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("↗", fontSize = 18.sp)
                    }
                }
                Button(onClick = {
                    if (refreshing) return@Button
                    refreshing = true
                    scope.launch {
                        val items = DataRepository.refresh(ctx)
                        refreshing = false
                        ctx.toast(if (items.isNotEmpty()) "Обновлено: ${items.size}" else "Не удалось")
                    }
                }, modifier = Modifier.fillMaxWidth().height(44.dp), enabled = !refreshing) {
                    if (refreshing) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(if (refreshing) "Обновление..." else "🔄 Обновить базу")
                }
                Spacer(Modifier.height(8.dp))
                Text("Made with ❤️ in Termux", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        }  // Box
    }
}
