package com.garickbox.jecwebgo.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class TmdbGenre(val id: Int, val name: String, val emoji: String)

val TMDB_GENRES = listOf(
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
fun GenreGrid(onGenreClick: (TmdbGenre) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            "Жанры · популярное 2025-2026",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            userScrollEnabled = false
        ) {
            items(TMDB_GENRES) { g ->
                Card(
                    Modifier.fillMaxWidth().aspectRatio(1f).clickable { onGenreClick(g) },
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(g.emoji, fontSize = 28.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(g.name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center, maxLines = 1)
                    }
                }
            }
        }
    }
}
