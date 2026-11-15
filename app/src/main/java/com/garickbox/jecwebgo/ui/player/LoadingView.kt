package com.garickbox.jecwebgo.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.garickbox.jecwebgo.data.network.TmdbCache
import com.garickbox.jecwebgo.util.PosterUtil
import com.garickbox.jecwebgo.util.TitleUtil

private const val POSTER_BASE = "https://cdn.jsdelivr.net/gh/Garickbox/JecWebGo-Android@data/posters_tmdb/"

/**
 * LoadingView v3 — ПРОСТАЯ концепция:
 * - Большой постер (50% высоты)
 * - Title + info-строка (rating · year · runtime · genres)
 * - Описание (overview → tagline → "")
 * - Режиссёр (1 строка)
 * - 3 актёра (с фото)
 * - Подборка (если есть)
 * - Статус + Отмена
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

    // Match по нормализации — без "contains" для коротких (<4) во избежание ложных срабатываний
    val currentMovie = remember(title) {
        try {
            val tmdb = TmdbCache.peek() ?: return@remember null
            if (title.isBlank()) return@remember null
            val cleanT = TitleUtil.cleanTitle(title).trim()
            if (cleanT.length < 2) return@remember null
            fun norm(s: String) = s.lowercase().replace(Regex("[^а-яёa-z0-9]"), "")
            val normT = norm(cleanT)
            if (normT.isBlank()) return@remember null
            val candidates = mutableListOf(normT)
            if (cleanT.contains(":")) candidates.add(norm(cleanT.substringBefore(":")))
            if (cleanT.contains(" - ")) candidates.add(norm(cleanT.substringBefore(" - ")))
            tmdb.movies.find { m ->
                val nmT = norm(TitleUtil.cleanTitle(m.title))
                val nmO = norm(m.original_title)
                candidates.any { c ->
                    if (c.isBlank()) false
                    else if (nmT == c || nmO == c) true
                    else if (c.length >= 4 && nmT.length >= 4 && (nmT.startsWith(c) || c.startsWith(nmT))) true
                    else if (c.length >= 4 && nmO.length >= 4 && (nmO.startsWith(c) || c.startsWith(nmO))) true
                    else false
                }
            }
        } catch (_: Exception) { null }
    }

    // Описание: параметр → overview → tagline → ""
    val finalDesc = remember(description, currentMovie?.id) {
        when {
            description.isNotBlank() && !description.startsWith("🎬 А пока посмотрите") -> description
            currentMovie?.overview?.isNotBlank() == true -> currentMovie.overview
            currentMovie?.tagline?.isNotBlank() == true -> "«${currentMovie.tagline}»"
            else -> ""
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {

            // ═══ 1. БОЛЬШОЙ ПОСТЕР (50% высоты экрана) ═══
            Box(Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 400.dp)) {
                if (url.isNotBlank()) {
                    AsyncImage(
                        model = url,
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("🎬", fontSize = 96.sp, color = Color.White.copy(alpha = 0.25f))
                    }
                }
                // Градиент снизу для читаемости
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0.5f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.95f)
                        )
                    )
                )
                // Спиннер сверху справа
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).size(28.dp),
                    strokeWidth = 3.dp
                )
                // Collection badge сверху слева (если есть)
                if (currentMovie?.belongs_to_collection != null) {
                    Box(
                        Modifier.align(Alignment.TopStart).padding(12.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFF6B35).copy(alpha = 0.9f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("📚 ${currentMovie.belongs_to_collection.name.take(20)}",
                            fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // ═══ 2. ИНФО БЛОК ═══
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {

                // Title
                Text(TitleUtil.cleanTitle(title),
                    color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)

                // Info-строка
                if (currentMovie != null) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (currentMovie.rating > 0.0) {
                            Text("★ %.1f".format(currentMovie.rating),
                                color = Color(0xFFFFC107), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(10.dp))
                        }
                        if (currentMovie.year.isNotBlank()) {
                            Text(currentMovie.year, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
                            Spacer(Modifier.width(8.dp))
                        }
                        if (currentMovie.runtime > 0) {
                            Text("${currentMovie.runtime} мин", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
                            Spacer(Modifier.width(8.dp))
                        }
                    }
                    if (currentMovie.genres.isNotEmpty()) {
                        Spacer(Modifier.height(3.dp))
                        Text(currentMovie.genres.joinToString(", "),
                            color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }

                // Описание
                if (finalDesc.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(finalDesc, color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp, lineHeight = 18.sp,
                        maxLines = 6, overflow = TextOverflow.Ellipsis)
                }

                // Режиссёр
                if (currentMovie?.director?.isNotBlank() == true) {
                    Spacer(Modifier.height(10.dp))
                    Text("🎬 Режиссёр: ${currentMovie.director}",
                        color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                // Актёры — горизонтальный Row с фото (top-3)
                if (currentMovie?.cast_details?.isNotEmpty() == true) {
                    Spacer(Modifier.height(12.dp))
                    Text("🎭 В ролях:",
                        color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        currentMovie.cast_details.take(3).forEach { c ->
                            Column(
                                Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    Modifier.size(56.dp).clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val photoUrl = remember(c.id, c.profile_file) {
                                        if (c.profile_file.isNotBlank())
                                            "https://cdn.jsdelivr.net/gh/Garickbox/JecWebGo-Android@data/posters_actors/${c.profile_file}"
                                        else ""
                                    }
                                    if (photoUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = photoUrl,
                                            contentDescription = c.name,
                                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Text("🎭", fontSize = 20.sp)
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(c.name,
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 10.sp, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }

                // ═══ 3. СТАТУС + ОТМЕНА ═══
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Text(status, color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) { Text("Отменить", fontSize = 13.sp) }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
