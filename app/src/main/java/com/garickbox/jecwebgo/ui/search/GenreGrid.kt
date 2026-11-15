package com.garickbox.jecwebgo.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.garickbox.jecwebgo.data.network.TMDB_CATEGORIES
import com.garickbox.jecwebgo.data.network.TmdbActor
import com.garickbox.jecwebgo.data.network.TmdbCategory
import com.garickbox.jecwebgo.data.network.TmdbGenre
import com.garickbox.jecwebgo.data.network.TmdbRepository

private val FALLBACK_GENRES = listOf(
    TmdbGenre(28, "Боевик", "💥"),
    TmdbGenre(35, "Комедия", "😂"),
    TmdbGenre(878, "Фантастика", "🚀"),
    TmdbGenre(27, "Ужасы", "👻"),
    TmdbGenre(18, "Драма", "🎭"),
    TmdbGenre(9648, "Детектив", "🕵️"),
    TmdbGenre(10749, "Мелодрама", "💕"),
    TmdbGenre(12, "Приключения", "🗺️"),
    TmdbGenre(14, "Фэнтези", "🏰"),
    TmdbGenre(10752, "Военный", "⚔️"),
    TmdbGenre(16, "Мультфильм", "🎨"),
    TmdbGenre(36, "Исторический", "📜")
)

@Composable
fun GenreGrid(
    onGenreClick: (TmdbGenre) -> Unit,
    onCategoryClick: (TmdbCategory) -> Unit,
    onActorClick: (TmdbActor) -> Unit
) {
    val ctx = LocalContext.current
    var genres by remember { mutableStateOf<List<TmdbGenre>>(FALLBACK_GENRES) }
    var topActors by remember { mutableStateOf<List<TmdbActor>>(emptyList()) }

    LaunchedEffect(Unit) {
        val data = TmdbRepository.load(ctx)
        if (data != null) {
            if (data.genres.isNotEmpty()) genres = data.genres
            topActors = data.top_actors
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(vertical = 4.dp),
        contentPadding = PaddingValues(bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { SectionHeader("Жанры") }
        item {
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                userScrollEnabled = false
            ) {
                items(genres) { g ->
                    TileCard(emoji = g.emoji, name = g.name) { onGenreClick(g) }
                }
            }
        }
        item {
            Spacer(Modifier.height(4.dp))
            SectionHeader("Категории")
        }
        item {
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier.fillMaxWidth().heightIn(max = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                userScrollEnabled = false
            ) {
                items(TMDB_CATEGORIES) { c ->
                    TileCard(emoji = c.emoji, name = c.name) { onCategoryClick(c) }
                }
            }
        }
        if (topActors.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                SectionHeader("🎭 Актёры (${topActors.size})")
            }
            val rows = topActors.chunked(6)
            rows.forEachIndexed { _, rowActors ->
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowActors.forEach { actor ->
                            ActorTile(
                                actor = actor,
                                modifier = Modifier.weight(1f),
                                onClick = { onActorClick(actor) }
                            )
                        }
                        repeat(6 - rowActors.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
}

@Composable
private fun TileCard(emoji: String, name: String, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().aspectRatio(1f).clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            Modifier.fillMaxSize().padding(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(emoji, fontSize = 20.sp)
            Spacer(Modifier.height(2.dp))
            Text(name, fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun ActorTile(actor: TmdbActor, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val url = TmdbRepository.actorPhotoUrl(actor.profile_file)
    Column(
        modifier.aspectRatio(0.7f).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (url.isNotBlank()) {
                AsyncImage(
                    model = url,
                    contentDescription = actor.name,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text("🎭", fontSize = 20.sp)
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(actor.name,
            fontSize = 8.sp, fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 1.dp))
    }
}
