package com.garickbox.jecwebgo.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import coil.compose.AsyncImage
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.garickbox.jecwebgo.data.network.TmdbData
import com.garickbox.jecwebgo.data.network.TmdbGenre
import com.garickbox.jecwebgo.data.network.TmdbMovie
import com.garickbox.jecwebgo.data.network.TmdbRepository

@Composable
fun GenreMoviesSheet(
    genre: TmdbGenre,
    onDismiss: () -> Unit,
    onSearch: (title: String) -> Unit
) {
    val ctx = LocalContext.current
    var data by remember { mutableStateOf<TmdbData?>(null) }
    var loading by remember { mutableStateOf(true) }
    var detailsMovie by remember { mutableStateOf<TmdbMovie?>(null) }

    LaunchedEffect(genre.id) {
        loading = true
        data = TmdbRepository.load(ctx)
        loading = false
    }

    detailsMovie?.let { m ->
        TmdbDetailsDialog(m, onDismiss = { detailsMovie = null }, onSearch = onSearch)
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val dialogWindowProvider = androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider
        androidx.compose.runtime.DisposableEffect(Unit) {
            dialogWindowProvider?.window?.let { w ->
                try {
                    w.statusBarColor = android.graphics.Color.TRANSPARENT
                    w.navigationBarColor = android.graphics.Color.TRANSPARENT
                    w.setLayout(
                        android.view.WindowManager.LayoutParams.MATCH_PARENT,
                        android.view.WindowManager.LayoutParams.MATCH_PARENT
                    )
                    androidx.core.view.WindowCompat.setDecorFitsSystemWindows(w, false)
                } catch (_: Exception) {}
                val wic = androidx.core.view.WindowCompat.getInsetsController(w, w.decorView)
                wic.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                wic.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            onDispose { }
        }
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 2.dp
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${genre.emoji} ${genre.name}", style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f))
                        IconButton(onClick = onDismiss) {
                            Text("✕", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))

                val movies = data?.byGenre(genre.id, 50) ?: emptyList()

                when {
                    loading -> Box(Modifier.fillMaxWidth().height(200.dp), Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    movies.isEmpty() -> Box(Modifier.fillMaxWidth().height(200.dp), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("База TMDB ещё не готова", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Text("Первый прогон раз в неделю", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(movies) { movie ->
                            MovieCard(movie) { detailsMovie = movie }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MovieCard(movie: TmdbMovie, onClick: () -> Unit) {
    val mctx = LocalContext.current
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(10.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
            val url = TmdbRepository.posterUrl(movie)
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)), Alignment.Center) {
                Text("🎬", fontSize = 32.sp)
            }
            if (url.isNotBlank()) {
                val req = remember(url) {
                    coil.request.ImageRequest.Builder(mctx).data(url).size(400, 600).crossfade(false).build()
                }
                AsyncImage(model = req, contentDescription = movie.title,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop)
            }
            if (movie.rating > 0.0) {
                Box(Modifier.align(Alignment.TopStart).padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFFFC107).copy(alpha = 0.95f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text("★ ${"%.1f".format(movie.rating)}", fontSize = 10.sp,
                        color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
            val scrimBrush = remember { Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))) }
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(72.dp).background(scrimBrush))
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(8.dp)) {
                if (movie.year.isNotBlank()) {
                    Text(movie.year, fontSize = 10.sp, color = Color.White.copy(alpha = 0.75f),
                        fontWeight = FontWeight.Bold)
                }
                Text(movie.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, color = Color.White)
            }
        }
    }
}
