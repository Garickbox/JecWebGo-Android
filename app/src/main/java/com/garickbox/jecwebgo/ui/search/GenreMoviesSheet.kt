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
import com.garickbox.jecwebgo.data.network.TmdbData
import com.garickbox.jecwebgo.data.network.TmdbGenre
import com.garickbox.jecwebgo.data.network.TmdbMovie
import com.garickbox.jecwebgo.data.network.TmdbRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenreMoviesSheet(
    genre: TmdbGenre,
    onDismiss: () -> Unit,
    onSearch: (title: String) -> Unit
) {
    val ctx = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 8.dp)) {
            Text("${genre.emoji} ${genre.name}", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("Популярное 2024-2026 · TMDB", fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))

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
                    modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
                    contentPadding = PaddingValues(bottom = 96.dp),
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

@Composable
private fun MovieCard(movie: TmdbMovie, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().height(320.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(220.dp)) {
                val url = TmdbRepository.posterUrl(movie)
                if (url.isNotBlank()) {
                    AsyncImage(model = url, contentDescription = movie.title,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)),
                        contentScale = ContentScale.Crop)
                } else {
                    Box(Modifier.fillMaxSize(), Alignment.Center) { Text("🎬", fontSize = 32.sp) }
                }
                if (movie.rating > 0.0) {
                    Box(Modifier.align(Alignment.TopStart).padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(androidx.compose.ui.graphics.Color(0xFFFFC107).copy(alpha = 0.95f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text("★ ${"%.1f".format(movie.rating)}", fontSize = 10.sp,
                            color = androidx.compose.ui.graphics.Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
                if (movie.year.isNotBlank()) {
                    Box(Modifier.align(Alignment.BottomStart).padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text(movie.year, fontSize = 10.sp, color = androidx.compose.ui.graphics.Color.White,
                            fontWeight = FontWeight.Bold)
                    }
                }
            }
            Column(Modifier.padding(8.dp)) {
                Text(movie.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
                if (movie.genres.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(movie.genres.joinToString(", ").take(30), fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
