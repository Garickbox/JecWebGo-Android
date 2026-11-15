package com.garickbox.jecwebgo.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garickbox.jecwebgo.data.network.TMDB_CATEGORIES
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
    onCategoryClick: (TmdbCategory) -> Unit
) {
    val ctx = LocalContext.current
    var genres by remember { mutableStateOf<List<TmdbGenre>>(FALLBACK_GENRES) }
    val cfg = LocalConfiguration.current
    val cols = if (cfg.screenWidthDp >= 600) 6 else 6  // 6 всегда

    LaunchedEffect(Unit) {
        val data = TmdbRepository.load(ctx)
        if (data != null && data.genres.isNotEmpty()) genres = data.genres
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(vertical = 4.dp),
        contentPadding = PaddingValues(bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("Жанры", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        }
        item {
            LazyVerticalGrid(
                columns = GridCells.Fixed(cols),
                modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                userScrollEnabled = false
            ) {
                items(genres) { g ->
                    GenreCard(emoji = g.emoji, name = g.name) { onGenreClick(g) }
                }
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            Text("Категории", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        }
        item {
            LazyVerticalGrid(
                columns = GridCells.Fixed(cols),
                modifier = Modifier.fillMaxWidth().heightIn(max = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                userScrollEnabled = false
            ) {
                items(TMDB_CATEGORIES) { c ->
                    GenreCard(emoji = c.emoji, name = c.name) { onCategoryClick(c) }
                }
            }
        }
    }
}

@Composable
private fun GenreCard(emoji: String, name: String, onClick: () -> Unit) {
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
