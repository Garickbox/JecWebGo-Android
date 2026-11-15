package com.garickbox.jecwebgo.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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

    // Матчим текущий фильм в TmdbCache — нормализация (убрать пунктуацию, скобки)
    val currentMovie = remember(title) {
        try {
            val tmdb = TmdbCache.peek()
            if (tmdb == null || title.isBlank()) return@remember null
            val cleanT = TitleUtil.cleanTitle(title).trim()
            if (cleanT.length < 2) return@remember null
            // Нормализация: только буквы+цифры
            fun norm(s: String) = s.lowercase().replace(Regex("[^а-яёa-z0-9]"), "")
            val normT = norm(cleanT)
            // Кандидаты: 1) полный title, 2) до ":", 3) до " - "
            val candidates = mutableListOf(normT)
            if (cleanT.contains(":")) candidates.add(norm(cleanT.substringBefore(":")))
            if (cleanT.contains(" - ")) candidates.add(norm(cleanT.substringBefore(" - ")))
            tmdb.movies.find { m ->
                val nmT = norm(TitleUtil.cleanTitle(m.title))
                val nmO = norm(m.original_title)
                candidates.any { c -> c.isNotBlank() && (nmT == c || nmO == c || nmT.contains(c) || c.contains(nmT)) }
            }
        } catch (_: Exception) { null }
    }

    // 6 постеров для коллажа — по жанру текущего, или случайные
    val bgPosters = remember(currentMovie?.id, title) {
        try {
            val tmdb = TmdbCache.peek() ?: return@remember emptyList<TmdbMovie>()
            val pool = if (currentMovie != null && currentMovie.genre_ids.isNotEmpty()) {
                tmdb.movies.filter { m ->
                    m.id != currentMovie.id && m.poster_file.isNotBlank() &&
                        m.genre_ids.any { it in currentMovie.genre_ids }
                }
            } else {
                tmdb.movies.filter { it.poster_file.isNotBlank() }
            }
            pool.distinctBy { it.id }.shuffled().take(6)
        } catch (_: Exception) { emptyList() }
    }

    // Похожие — 4 по жанру, sorted by rating
    val similar = remember(currentMovie?.id) {
        try {
            val tmdb = TmdbCache.peek() ?: return@remember emptyList<TmdbMovie>()
            if (currentMovie != null && currentMovie.genre_ids.isNotEmpty()) {
                tmdb.movies.filter { m ->
                    m.id != currentMovie.id && m.poster_file.isNotBlank() &&
                        m.genre_ids.any { it in currentMovie.genre_ids }
                }.distinctBy { it.id }.sortedByDescending { it.rating }.take(4)
            } else emptyList()
        } catch (_: Exception) { emptyList() }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxSize()) {

            // ═══════ ЛЕВАЯ КОЛОНКА (45%): 2 колонки × 3 + Похожие ═══════
            Column(Modifier.weight(0.45f).fillMaxHeight()) {

                // Коллаж: 2 Column по 3 — гарантированно 2 колонки
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    // Левая колонка (3 постера)
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        for (i in 0 until 3) {
                            Box(Modifier.weight(1f).fillMaxWidth().padding(1.dp)) {
                                val idx = i * 2  // 0, 2, 4
                                if (idx < bgPosters.size) {
                                    val bgUrl = remember(bgPosters[idx].poster_file) {
                                        POSTER_BASE + bgPosters[idx].poster_file
                                    }
                                    AsyncImage(
                                        model = bgUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize()
                                            .alpha(0.35f)
                                            .clip(RoundedCornerShape(4.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                    }
                    // Правая колонка (3 постера)
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        for (i in 0 until 3) {
                            Box(Modifier.weight(1f).fillMaxWidth().padding(1.dp)) {
                                val idx = i * 2 + 1  // 1, 3, 5
                                if (idx < bgPosters.size) {
                                    val bgUrl = remember(bgPosters[idx].poster_file) {
                                        POSTER_BASE + bgPosters[idx].poster_file
                                    }
                                    AsyncImage(
                                        model = bgUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize()
                                            .alpha(0.35f)
                                            .clip(RoundedCornerShape(4.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                    }
                }

                // Похожие — 4 × 42dp = 168dp (влезает в 45% от 360)
                if (similar.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth().height(110.dp).padding(top = 4.dp)) {
                        Text("Похожие", color = Color.White,
                            fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 6.dp, bottom = 2.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            itemsIndexed(similar, key = { index, m -> "sim_${m.id}_$index" }) { _, m ->
                                val simUrl = remember(m.poster_file) { POSTER_BASE + m.poster_file }
                                Column(Modifier.width(42.dp)) {
                                    AsyncImage(
                                        model = simUrl,
                                        contentDescription = m.title,
                                        modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                                            .clip(RoundedCornerShape(4.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Text(m.title, color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 6.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }

            // ═══════ ПРАВАЯ КОЛОНКА (55%): постер + инфо ═══════
            Column(
                Modifier.weight(0.55f).fillMaxHeight().padding(10.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                // Основной постер — ФИКСИРОВАННАЯ высота, чтобы инфо влезало
                if (url.isNotBlank()) {
                    AsyncImage(
                        model = url,
                        contentDescription = title,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)
                            .aspectRatio(2f / 3f, matchHeightConstraintsFirst = true)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Box(
                        Modifier.fillMaxWidth().heightIn(max = 200.dp).aspectRatio(2f / 3f),
                        contentAlignment = Alignment.Center
                    ) { Text("🎬", fontSize = 48.sp, color = Color.White.copy(alpha = 0.3f)) }
                }

                Spacer(Modifier.height(8.dp))

                // Title
                Text(TitleUtil.cleanTitle(title),
                    color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)

                // Инфо-строка (только если match найден)
                if (currentMovie != null) {
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (currentMovie.rating > 0.0) {
                            Text("★ %.1f".format(currentMovie.rating),
                                color = Color(0xFFFFC107), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(6.dp))
                        }
                        if (currentMovie.year.isNotBlank()) {
                            Text(currentMovie.year, color = Color.White.copy(alpha = 0.75f), fontSize = 10.sp)
                            Spacer(Modifier.width(5.dp))
                        }
                        if (currentMovie.runtime > 0) {
                            Text("${currentMovie.runtime}м", color = Color.White.copy(alpha = 0.75f), fontSize = 10.sp)
                            Spacer(Modifier.width(5.dp))
                        }
                        if (currentMovie.genres.isNotEmpty()) {
                            Text(currentMovie.genres.take(2).joinToString(", "),
                                color = Color.White.copy(alpha = 0.75f), fontSize = 10.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                // Описание (только реальное — не randomAnnouncement)
                if (description.isNotBlank() && !description.startsWith("🎬 А пока посмотрите")) {
                    Spacer(Modifier.height(6.dp))
                    Text(description, color = Color.White.copy(alpha = 0.85f),
                        fontSize = 10.sp, lineHeight = 14.sp,
                        maxLines = 4, overflow = TextOverflow.Ellipsis)
                } else if (currentMovie?.tagline?.isNotBlank() == true) {
                    Spacer(Modifier.height(6.dp))
                    Text("«${currentMovie.tagline}»", color = Color.White.copy(alpha = 0.75f),
                        fontSize = 10.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }

                // Статус
                Spacer(Modifier.height(6.dp))
                Text(status, color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)

                // Кнопка Отмена
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    contentPadding = PaddingValues(0.dp)
                ) { Text("Отменить", fontSize = 11.sp) }
            }
        }

        // Спиннер
        Box(Modifier.fillMaxSize().padding(10.dp)) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).size(24.dp),
                strokeWidth = 2.5.dp
            )
        }
    }
}
