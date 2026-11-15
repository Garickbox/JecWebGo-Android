package com.garickbox.jecwebgo.ui.search
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import android.widget.Toast
import androidx.compose.runtime.*
import com.garickbox.jecwebgo.ui.common.PlayActions
import com.garickbox.jecwebgo.data.store.BookmarkStore
import com.garickbox.jecwebgo.ui.ai.AiSheet
import com.garickbox.jecwebgo.data.network.TmdbGenre
import com.garickbox.jecwebgo.data.network.TmdbCategory
import com.garickbox.jecwebgo.data.network.TmdbMovie
import com.garickbox.jecwebgo.ui.common.DownloadFromMagnetFlow
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.garickbox.jecwebgo.domain.model.SearchItem
import com.garickbox.jecwebgo.util.toast

@Composable
fun SearchScreen(
    pendingQuery: String? = null,
    onQueryConsumed: () -> Unit = {},
    resetKey: Int = 0,
    vm: SearchViewModel = viewModel()
) {
    val s by vm.state.collectAsState()
    LaunchedEffect(pendingQuery) {
        pendingQuery?.let {
            vm.onQuery(it)
            vm.search()
            onQueryConsumed()
        }
    }

    LaunchedEffect(resetKey) {
        if (resetKey > 0) vm.reset()
    }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    var showAi by remember { mutableStateOf(false) }
    // SheetReq — универсальный запрос: жанр/категория/актёр
    data class SheetReq(
        val title: String,
        val emoji: String,
        val loadMovies: suspend () -> List<TmdbMovie>
    )
    var sheetReq by remember { mutableStateOf<SheetReq?>(null) }
    var downloadFor by remember { mutableStateOf<SearchItem?>(null) }
    val onPlayPhone: (SearchItem) -> Unit = { item ->
        scope.launch {
            val hash = com.garickbox.jecwebgo.util.MagnetUtil.hashOf(item.magnet)
            val fromBase = try { com.garickbox.jecwebgo.domain.TorrentRepository.posterInfoByHash(ctx, hash) } catch (_: Exception) { null }
            val p = fromBase?.poster?.takeIf { it.isNotBlank() } ?: ""
            val d = fromBase?.description?.takeIf { it.isNotBlank() }
                ?: try { com.garickbox.jecwebgo.domain.TorrentRepository.randomAnnouncement(ctx) } catch (_: Exception) { "🎬 Подключение к торренту..." }
            android.util.Log.d("JecWeb", "onPlayPhone: hash=${hash.take(8)} base=${fromBase != null} poster=$p descLen=${d.length}")
            PlayActions.playPhone(ctx, scope, item.magnet, item.title, p, d)
        }
    }
    val onDownload: (SearchItem) -> Unit = { item -> downloadFor = item }
    val onBookmark: (SearchItem) -> Unit = { item ->
        scope.launch {
            BookmarkStore.add(ctx, item)
            ctx.toast("В закладки: ${item.title}")
        }
    }

    downloadFor?.let { item ->
        DownloadFromMagnetFlow(
            magnet = item.magnet,
            title = item.title,
            onDismiss = { downloadFor = null }
        )
    }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().padding(top = 4.dp, start = 12.dp, end = 12.dp, bottom = 12.dp)) {
        OutlinedTextField(
            value = s.query,
            onValueChange = vm::onQuery,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Поиск фильмов и сериалов…") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.search(); keyboard?.hide() }),
            trailingIcon = {
                IconButton(onClick = { vm.search(); keyboard?.hide() }) { Icon(Icons.Filled.Search, "search") }
            },
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(Modifier.height(8.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                s.loading -> Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator() }
                s.error != null -> Text("Ошибка: ${s.error}", modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error)
                s.items.isEmpty() && s.query.isNotBlank() -> Text("Ничего не найдено", Modifier.padding(12.dp))
                s.query.isBlank() && s.items.isEmpty() -> GenreGrid(
                    onGenreClick = { g ->
                        sheetReq = SheetReq(g.name, g.emoji) {
                            com.garickbox.jecwebgo.data.network.TmdbRepository.load(ctx)?.byGenre(g.id) ?: emptyList()
                        }
                    },
                    onCategoryClick = { c ->
                        sheetReq = SheetReq(c.name, c.emoji) {
                            com.garickbox.jecwebgo.data.network.TmdbRepository.load(ctx)?.byCategory(c.key) ?: emptyList()
                        }
                    }
                )
                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 72.dp)
                ) {
                    items(s.items) { item -> SearchCard(item, onPlayPhone, onDownload, onBookmark) }
                }
            }
        }
    }
        FloatingActionButton(
            onClick = { showAi = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) { Text("🤖", fontSize = 20.sp) }
        if (showAi) AiSheet(
            onDismiss = { showAi = false },
            onSearch = { q, _ -> vm.onQuery(q); vm.search(); showAi = false }
        )
        sheetReq?.let { req ->
            GenreMoviesSheet(
                title = req.title,
                emoji = req.emoji,
                loadMovies = req.loadMovies,
                onDismiss = { sheetReq = null },
                onSearch = { title -> vm.onQuery(title); vm.search(); sheetReq = null }
            )
        }
    }
}

@Composable
fun SearchCard(item: SearchItem, onPlayPhone: (SearchItem) -> Unit, onDownload: (SearchItem) -> Unit, onBookmark: (SearchItem) -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 2)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.relased.isNotBlank()) Text("📅 ${item.relased}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.quality > 0) Text("${item.quality}p", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                if (item.videotype.isNotBlank()) Text(item.videotype.uppercase(), fontSize = 12.sp)
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("📦 ${item.sizeName}", fontSize = 12.sp)
                Text("🔼 ${item.sid}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                Text("🔽 ${item.pir}", fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = { onPlayPhone(item) }, modifier = Modifier.weight(1f).height(40.dp), contentPadding = PaddingValues(0.dp)) { Text("▶", fontSize = 16.sp, maxLines = 1) }
                Button(onClick = { onDownload(item) }, modifier = Modifier.weight(1f).height(40.dp), contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)) { Text("⬇", fontSize = 16.sp, maxLines = 1) }
                OutlinedButton(onClick = { onBookmark(item) }, modifier = Modifier.weight(1f).height(40.dp), contentPadding = PaddingValues(0.dp)) { Text("🔖", fontSize = 16.sp, maxLines = 1) }
            }
        }
    }
}
