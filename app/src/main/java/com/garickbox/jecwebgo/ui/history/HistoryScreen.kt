package com.garickbox.jecwebgo.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.garickbox.jecwebgo.data.store.HistoryEntry
import com.garickbox.jecwebgo.data.store.HistoryStore
import com.garickbox.jecwebgo.data.store.PlayLogEntry
import com.garickbox.jecwebgo.data.store.PlayLogStore
import com.garickbox.jecwebgo.ui.common.PlayActions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var searches by remember { mutableStateOf<List<HistoryEntry>>(emptyList()) }
    var playlog by remember { mutableStateOf<List<PlayLogEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    suspend fun reload() {
        loading = true
        searches = HistoryStore.list(ctx)
        playlog = PlayLogStore.list(ctx).reversed()
        loading = false
    }
    LaunchedEffect(Unit) { reload() }

    if (loading) { Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }; return }

    val fmt = remember { SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()) }

    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (searches.isNotEmpty()) {
            item { Text("Запросы", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
            itemsIndexed(searches) { idx, h ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(h.query, fontWeight = FontWeight.SemiBold)
                            Text(fmt.format(Date(h.time * 1000)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(onClick = {
                            scope.launch { HistoryStore.removeAt(ctx, idx); reload() }
                        }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("✕") }
                    }
                }
            }
        }
        if (playlog.isNotEmpty()) {
            item { Spacer(Modifier.height(8.dp)); Text("Просмотры", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
            itemsIndexed(playlog) { idx, p ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text((if (p.type == "tv") "📺 " else "▶ ") + p.title, fontWeight = FontWeight.SemiBold, maxLines = 2)
                        Text(fmt.format(Date(p.time * 1000)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(onClick = { PlayActions.playPhone(ctx, scope, p.magnet, p.title) },
                                modifier = Modifier.weight(1f).height(38.dp), contentPadding = PaddingValues(0.dp)) { Text("▶", fontSize = 15.sp) }
                            Button(onClick = { PlayActions.playTV(ctx, scope, p.magnet, p.title) },
                                modifier = Modifier.weight(1f).height(38.dp), contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)) { Text("📺", fontSize = 15.sp) }
                            OutlinedButton(onClick = { scope.launch { PlayLogStore.removeAt(ctx, playlog.size - 1 - idx); reload() } },
                                modifier = Modifier.weight(1f).height(38.dp), contentPadding = PaddingValues(0.dp)) { Text("✕", fontSize = 15.sp) }
                        }
                    }
                }
            }
        }
        if (searches.isEmpty() && playlog.isEmpty()) {
            item { Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { Text("История пуста", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        }
    }
}
