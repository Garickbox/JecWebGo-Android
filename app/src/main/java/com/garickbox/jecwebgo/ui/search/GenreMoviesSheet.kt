package com.garickbox.jecwebgo.ui.search

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.garickbox.jecwebgo.domain.AiRepository
import com.garickbox.jecwebgo.domain.model.AiMovie

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenreMoviesSheet(
    genre: TmdbGenre,
    onDismiss: () -> Unit,
    onSearch: (title: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var movies by remember { mutableStateOf<List<AiMovie>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(genre.id) {
        loading = true
        movies = AiRepository.discoverGenre(genre.id, 1)
        loading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                "${genre.emoji} ${genre.name}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(4.dp))
            Text("Популярное из TMDB · реальные фильмы",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))

            when {
                loading -> Box(Modifier.fillMaxWidth().height(200.dp), Alignment.Center) {
                    CircularProgressIndicator()
                }
                movies.isEmpty() -> Box(Modifier.fillMaxWidth().height(120.dp), Alignment.Center) {
                    Text("Фильмы не найдены", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(movies) { movie ->
                        Card(Modifier.fillMaxWidth().clickable {
                            onSearch(movie.title)
                            onDismiss()
                        }) {
                            Column {
                                Box(Modifier.fillMaxWidth().aspectRatio(2f/3f)) {
                                    if (movie.posterPath.isNotBlank()) {
                                        AsyncImage(
                                            model = "https://image.tmdb.org/t/p/w342${movie.posterPath}",
                                            contentDescription = movie.title,
                                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(Modifier.fillMaxSize(), Alignment.Center) { Text("🎬", fontSize = 32.sp) }
                                    }
                                }
                                Column(Modifier.padding(6.dp)) {
                                    Text(movie.title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (movie.rating.isNotBlank()) {
                                            Text("★ ${movie.rating}", fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.width(6.dp))
                                        }
                                        if (movie.year.isNotBlank()) {
                                            Text(movie.year, fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
