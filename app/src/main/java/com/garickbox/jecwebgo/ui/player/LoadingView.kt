package com.garickbox.jecwebgo.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.garickbox.jecwebgo.data.network.TmdbCache
import com.garickbox.jecwebgo.data.network.TmdbMovie
import com.garickbox.jecwebgo.util.PosterUtil
import com.garickbox.jecwebgo.util.TitleUtil

private const val POSTER_BASE = "https://cdn.jsdelivr.net/gh/Garickbox/JecWebGo-Android@data/posters_tmdb/"

/**
 * LoadingView v2 «Кинозал»:
 * - Левая колонка (45%): коллаж 6 постеров (2×3, alpha 0.3) + «Похожие» снизу
 * - Правая колонка (55%): основной постер + title/rating/year/runtime/genres + описание + статус + Отмена
 * - Спиннер справа сверху
 * Использует TmdbCache (10k фильмов) для коллажа и похожих по жанру.
 */
@Composable
fun LoadingView(
    title: String,
    description: String,
    poster: String,
    status: String,
    loadingNonce: Long = 0L,
    onCancel: () -> Unit
) {
    val url = remember(poster) { PosterUtil.posterUrl(poster) }

    // 1. Матчим текущий фильм в TmdbCache (по title)
    val currentMovie = remember(title) {
        try {
            val tmdb = TmdbCache.peek()
            if (tmdb != null && title.isNotBlank()) {
                val cleanT = TitleUtil.cleanTitle(title).lowercase().trim()
                if (cleanT.length >= 2) {
                    tmdb.movies.find { m ->
                        val mt = TitleUtil.cleanTitle(m.title).lowercase().trim()
                        mt == cleanT
                            || (mt.isNotBlank() && cleanT.contains(mt))
                            || (cleanT.isNotBlank() && mt.contains(cleanT))
                    }
                } else null
            } else null
        } catch (_: Exception) { null }
    }

    // 2. 6 постеров для коллажа (по жанру текущего, или случайные)
    val bgPosters = remember(currentMovie?.id, title) {
        try {
            val tmdb = TmdbCache.peek() ?: return@remember emptyList<TmdbMovie>()
            val pool = if (currentMovie != null && currentMovie.genre_ids.isNotEmpty()) {
                tmdb.movies.filter { m ->
                    m.id != currentMovie.id &&
                        m.poster_file.isNotBlank() &&
                        m.genre_ids.any { it in currentMovie.genre_ids }
                }
            } else {
                tmdb.movies.filter { it.poster_file.isNotBlank() }
            }
            pool.shuffled().take(6)
        } catch (_: Exception) { emptyList() }
    }

    // 3. Похожие (по жанру, sorted by rating)
    val similar = remember(currentMovie?.id) {
        try {
            val tmdb = TmdbCache.peek() ?: return@remember emptyList<TmdbMovie>()
            if (currentMovie != null && currentMovie.genre_ids.isNotEmpty()) {
                tmdb.movies.filter { m ->
                    m.id != currentMovie.id &&
                        m.poster_file.isNotBlank() &&
                        m.genre_ids.any { it in currentMovie.genre_ids }
                }.sortedByDescending { it.rating }.take(6)
            } else emptyList()
        } catch (_: Exception) { emptyList() }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxSize()) {

            // ═══════ ЛЕВАЯ КОЛОНКА (45%): коллаж 2×3 + Похожие ═══════
            Column(Modifier.weight(0.45f).fillMaxHeight()) {

                // Коллаж 6 постеров 2×3, alpha 0.3
                Column(Modifier.weight(1f).fillMaxWidth()) {
                    for (row in 0 until 3) {
                        Row(Modifier.weight(1f).fillMaxWidth()) {
                            for (col in 0 until 2) {
                                val idx = row * 2 + col
                                Box(Modifier.weight(1f).fillMaxHeight().padding(2.dp)) {
                                    if (idx < bgPosters.size) {
                                        val bgUrl = remember(bgPosters[idx].poster_file) {
                                            POSTER_BASE + bgPosters[idx].poster_file
                                        }
                                        AsyncImage(
                                            model = bgUrl,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize()
                                                .alpha(0.3f)
                                                .clip(RoundedCornerShape(6.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else if (url.isNotBlank()) {
                                        AsyncImage(
                                            model = url,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize()
                                                .alpha(0.15f)
                                                .clip(RoundedCornerShape(6.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Похожие (только если есть данные из TMDB)
                if (similar.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth().height(120.dp)) {
                        Text("Похожие", color = Color.White,
                            fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 2.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(similar, key = { it.id }) { m ->
                                val simUrl = remember(m.poster_file) { POSTER_BASE + m.poster_file }
                                Column(Modifier.width(52.dp)) {
                                    AsyncImage(
                                        model = simUrl,
                                        contentDescription = m.title,
                                        modifier = Modifier.fillMaxWidth()
                                            .aspectRatio(2f / 3f)
                                            .clip(RoundedCornerShape(5.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(m.title, color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 7.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.height(8.dp))
                }
            }

            // ═══════ ПРАВАЯ КОЛОНКА (55%): постер + инфо ═══════
            Column(
                Modifier.weight(0.55f).fillMaxHeight().padding(12.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                // Основной постер (weight 1 = занимает всё доступное место)
                if (url.isNotBlank()) {
                    AsyncImage(
                        model = url,
                        contentDescription = title,
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Box(
                        Modifier.fillMaxWidth().weight(1f, fill = false).aspectRatio(2f / 3f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🎬", fontSize = 64.sp, color = Color.White.copy(alpha = 0.3f))
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Title
                Text(TitleUtil.cleanTitle(title),
                    color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)

                // Инфо-строка (rating · year · runtime · genres)
                if (currentMovie != null) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (currentMovie.rating > 0.0) {
                            Text("★ %.1f".format(currentMovie.rating),
                                color = Color(0xFFFFC107), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(8.dp))
                        }
                        if (currentMovie.year.isNotBlank()) {
                            Text(currentMovie.year, color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
                            Spacer(Modifier.width(6.dp))
                        }
                        if (currentMovie.runtime > 0) {
                            Text("${currentMovie.runtime}м", color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
                            Spacer(Modifier.width(6.dp))
                        }
                        if (currentMovie.genres.isNotEmpty()) {
                            Text(currentMovie.genres.take(2).joinToString(", "),
                                color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                // Описание
                if (description.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(description, color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp, lineHeight = 15.sp,
                        maxLines = 5, overflow = TextOverflow.Ellipsis)
                }

                // Статус
                Spacer(Modifier.height(8.dp))
                Text(status, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)

                // Кнопка Отмена
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    contentPadding = PaddingValues(0.dp)
                ) { Text("Отменить", fontSize = 12.sp) }
            }
        }

        // Спиннер справа сверху
        Box(Modifier.fillMaxSize().padding(12.dp)) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).size(28.dp),
                strokeWidth = 3.dp
            )
        }
    }
}
