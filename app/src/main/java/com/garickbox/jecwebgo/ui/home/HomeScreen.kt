package com.garickbox.jecwebgo.ui.home

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.garickbox.jecwebgo.data.model.GroupedTorrent
import com.garickbox.jecwebgo.data.network.TorrentItem
import com.garickbox.jecwebgo.ui.common.PlayActions
import java.net.URLEncoder

private fun posterUrl(poster: String): String {
    if (poster.isBlank()) return ""
    if (poster.startsWith("posters/"))
        return "https://raw.githubusercontent.com/Garickbox/JecWebGo-Android/data/" + poster
    return "https://ai-jecweb.garickwot.workers.dev/img?url=" + URLEncoder.encode(poster, "UTF-8")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenSearch: (String) -> Unit, vm: HomeViewModel = viewModel()) {
    val s by vm.state.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<GroupedTorrent?>(null) }
    var noNet by remember { mutableStateOf(false) }
    var netCheckKey by remember { mutableStateOf(0) }
    LaunchedEffect(netCheckKey) {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork
        val caps = if (net != null) cm.getNetworkCapabilities(net) else null
        noNet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) != true
    }
    LaunchedEffect(Unit) { if (s.items.isEmpty() && !s.loading) vm.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Главная", fontWeight = FontWeight.SemiBold)
                        if (s.lastUpdate.isNotEmpty() && s.lastUpdate != "—")
                            Text("Обновлено: ${s.lastUpdate}  ·  ${s.items.size} фильмов",
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refresh() }, enabled = !s.refreshing) {
                        if (s.refreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Filled.Refresh, "refresh")
                    }
                }
            )
        }
    ) { pad ->
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
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) { items(s.items) { g -> GroupCard(g) { selected = g } } }
            }
        }
    }

    if (noNet) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Нет интернета") },
            text = { Text("Проверьте подключение и попробуйте снова.") },
            confirmButton = { TextButton(onClick = { netCheckKey++ }) { Text("Повторить") } },
            dismissButton = { TextButton(onClick = { noNet = false }) { Text("Позже") } }
        )
    }

    selected?.let { g ->
        DetailsDialog(
            g = g,
            onClose = { selected = null },
            onPlay = { item ->
                PlayActions.playPhone(ctx, scope, item.magnet, item.title)
            },
            onTV = { item ->
                PlayActions.playTV(ctx, scope, item.magnet, item.title)
            },
            onSearch = { onOpenSearch(g.cleanTitle); selected = null }
        )
    }
}

@Composable
private fun GroupCard(g: GroupedTorrent, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().height(330.dp).clickable { onClick() }, shape = RoundedCornerShape(10.dp)) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
                val url = posterUrl(g.poster)
                if (url.isNotBlank()) {
                    AsyncImage(model = url, contentDescription = g.cleanTitle,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)),
                        contentScale = ContentScale.Crop)
                } else {
                    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)), Alignment.Center) {
                        Text("🎬", fontSize = 32.sp)
                    }
                }
                if (g.count > 1) {
                    Box(Modifier.align(Alignment.TopEnd).padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text("${g.count} разд.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Column(Modifier.padding(8.dp)) {
                Text(g.cleanTitle, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (g.rating > 0.0)
                        Text("★ %.1f".format(g.rating), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.weight(1f))
                    if (g.year.isNotBlank())
                        Text(g.year, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun DetailsDialog(
    g: GroupedTorrent,
    onClose: () -> Unit,
    onPlay: (TorrentItem) -> Unit,
    onTV: (TorrentItem) -> Unit,
    onSearch: () -> Unit
) {
    Dialog(onDismissRequest = onClose) {
        Card(Modifier.fillMaxWidth().padding(8.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth().height(240.dp)) {
                    val url = posterUrl(g.poster)
                    if (url.isNotBlank()) {
                        AsyncImage(model = url, contentDescription = g.cleanTitle,
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Box(Modifier.fillMaxSize(), Alignment.Center) { Text("🎬", fontSize = 64.sp) }
                    }
                }
                Column(Modifier.padding(12.dp)) {
                    Text(g.cleanTitle, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (g.rating > 0.0)
                            Text("★ %.1f".format(g.rating), fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        if (g.year.isNotBlank()) { Spacer(Modifier.width(8.dp)); Text(g.year, fontSize = 12.sp) }
                        if (g.genre.isNotBlank()) { Spacer(Modifier.width(8.dp)); Text(g.genre, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    if (g.description.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(g.description, fontSize = 12.sp, maxLines = 5, overflow = TextOverflow.Ellipsis)
                    }
                    if (g.director.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text("Режиссёр: ${g.director}", fontSize = 11.sp)
                    }
                    if (g.actors.isNotBlank())
                        Text("В ролях: ${g.actors}", fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)

                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Раздачи (${g.count})", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = onSearch) { Text("🔍 Найти свежие") }
                    }
                    Spacer(Modifier.height(4.dp))

                    g.items.forEach { item ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(buildSpec(item), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Text(item.title, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(Modifier.width(6.dp))
                            Button(onClick = { onPlay(item); onClose() },
                                modifier = Modifier.height(36.dp).width(52.dp), contentPadding = PaddingValues(0.dp)) {
                                Text("▶", fontSize = 14.sp)
                            }
                            Spacer(Modifier.width(4.dp))
                            Button(onClick = { onTV(item); onClose() },
                                modifier = Modifier.height(36.dp).width(52.dp), contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)) {
                                Text("📺", fontSize = 14.sp)
                            }
                        }
                        Divider()
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

private fun buildSpec(item: TorrentItem): String {
    val parts = mutableListOf<String>()
    val q = extractQuality(item.title)
    if (q.isNotBlank()) parts.add(q)
    val v = extractVoice(item.title)
    if (v.isNotBlank()) parts.add(v)
    if (item.size.isNotBlank()) parts.add(item.size)
    parts.add("🔼 ${item.seeds}")
    return parts.joinToString(" · ")
}

private fun extractVoice(title: String): String {
    val t = title.lowercase()
    return when {
        t.contains("jaskier") -> "MVO Jaskier"
        t.contains("moviedalen") -> "Dub MovieDalen"
        t.contains("селезень") -> "Dub Селезень"
        t.contains("hdrezka") -> "HDrezka"
        t.contains("new-team") -> "New-Team"
        t.contains("lostfilm") -> "LostFilm"
        t.contains("elektri4") || t.contains("elektri4ka") -> "ELEKTRI4KA"
        t.contains("domino") -> "DoMiNo"
        t.contains("exkinoray") -> "ExKinoRay"
        t.contains(" megapeer") -> "MegaPeer"
        t.contains(" дуб") -> "Дубляж"
        t.contains(" mvo") -> "MVO"
        t.contains(" avo") -> "AVO"
        else -> ""
    }
}

private fun extractQuality(title: String): String {
    val m = Regex("""(2160p|1080p|720p|480p|WEB-?DL|WEB-?Rip|BDRip|HDRip|HDTV|BluRay|4K|UHD|HEVC|AVC)""", RegexOption.IGNORE_CASE).findAll(title)
        .map { it.value.uppercase() }.take(2).toList()
    return m.joinToString(" ")
}
