package com.garickbox.jecwebgo.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.garickbox.jecwebgo.data.network.TorrentItem

private fun cleanTitle(raw: String): String {
    var t = raw.trim()
    if (t.contains(" / ")) t = t.substringBefore(" / ").trim()
    val p = t.indexOf(" ("); if (p > 0) t = t.substring(0, p).trim()
    val b = t.indexOf(" ["); if (b > 0) t = t.substring(0, b).trim()
    return t.ifBlank { raw }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenSearch: (String) -> Unit, vm: HomeViewModel = viewModel()) {
    val s by vm.state.collectAsState()
    LaunchedEffect(Unit) { if (s.items.isEmpty() && !s.loading) vm.load() }
    Scaffold(topBar = {
        TopAppBar(
            title = { Column {
                Text("Главная", fontWeight = FontWeight.SemiBold)
                if (s.lastUpdate.isNotEmpty() && s.lastUpdate != "—")
                    Text("Обновлено: ${s.lastUpdate}", fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
            } },
            actions = { IconButton(onClick = { vm.refresh() }, enabled = !s.refreshing) {
                if (s.refreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Filled.Refresh, "refresh")
            } }
        )
    }) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when {
                s.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                s.items.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(s.error ?: "Нет данных", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { vm.refresh() }) { Text("Загрузить") }
                    }
                }
                else -> LazyVerticalGrid(columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(s.items) { item -> HomeCard(item) { onOpenSearch(cleanTitle(item.title)) } }
                }
            }
        }
    }
}

@Composable
private fun HomeCard(item: TorrentItem, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable { onClick() }, shape = RoundedCornerShape(10.dp)) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
                if (item.poster.isNotBlank()) {
                    AsyncImage(model = item.poster, contentDescription = item.title,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)),
                        contentScale = ContentScale.Crop)
                } else {
                    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)),
                        Alignment.Center) { Text("🎬", fontSize = 32.sp) }
                }
            }
            Column(Modifier.padding(8.dp)) {
                Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.rating.isNotBlank() && item.rating != "0.0")
                        Text("★ ${item.rating}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.weight(1f))
                    if (item.year.isNotBlank())
                        Text(item.year, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (item.seeds > 0)
                    Text("🔼 ${item.seeds}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
