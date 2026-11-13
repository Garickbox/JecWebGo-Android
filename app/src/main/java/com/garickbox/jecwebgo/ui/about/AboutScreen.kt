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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garickbox.jecwebgo.data.network.DataRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    var version by remember { mutableStateOf("?") }

    LaunchedEffect(Unit) {
        try {
            val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            version = pi.versionName ?: "?"
        } catch (_: Exception) {}
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("О приложении", fontWeight = FontWeight.SemiBold) }) }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            Text("🎬", fontSize = 64.sp)
            Spacer(Modifier.height(12.dp))
            Text("JecWeb Go", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Версия $version", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            Text("Медиацентр: поиск торрентов, просмотр, скачивание, AI-ассистент.",
                fontSize = 13.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Card(
                Modifier.fillMaxWidth().clickable {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Garickbox/JecWebGo-Android"))
                    runCatching { ctx.startActivity(intent) }
                }
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("📦", fontSize = 24.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("GitHub", fontWeight = FontWeight.SemiBold)
                        Text("github.com/Garickbox/JecWebGo-Android",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("↗", fontSize = 20.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    if (refreshing) return@Button
                    refreshing = true
                    scope.launch {
                        val items = DataRepository.refresh(ctx)
                        refreshing = false
                        Toast.makeText(ctx, if (items.isNotEmpty()) "Обновлено: ${items.size} записей" else "Не удалось", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = !refreshing
            ) {
                if (refreshing) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (refreshing) "Обновление..." else "🔄 Обновить базу")
            }
            Spacer(Modifier.height(24.dp))
            Text("Made with ❤️ in Termux",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
