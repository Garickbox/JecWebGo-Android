package com.garickbox.jecwebgo.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.garickbox.jecwebgo.data.network.TmdbMovie
import com.garickbox.jecwebgo.data.network.TmdbRepository

@Composable
fun TmdbDetailsDialog(movie: TmdbMovie, onDismiss: () -> Unit, onSearch: (String) -> Unit, onActorClick: (Int, String) -> Unit = { _, _ -> }) {
    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth().padding(8.dp).heightIn(max = 720.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth().height(280.dp)) {
                    val url = remember(movie.id) { TmdbRepository.posterUrl(movie) }
                    if (url.isNotBlank()) {
                        AsyncImage(model = url, contentDescription = movie.title,
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Box(Modifier.fillMaxSize(), Alignment.Center) { Text("🎬", fontSize = 64.sp) }
                    }
                }
                Column(Modifier.padding(12.dp)) {
                    if (movie.belongs_to_collection != null) {
                        Row(
                            Modifier.clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📚 ${movie.belongs_to_collection.name}",
                                fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onTertiary)
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(movie.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    if (movie.original_title.isNotBlank() && movie.original_title != movie.title) {
                        Text(movie.original_title, fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontStyle = FontStyle.Italic)
                    }
                    if (movie.tagline.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text("«${movie.tagline}»", fontSize = 12.sp, fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (movie.rating > 0.0) {
                            Text("★ ${"%.1f".format(movie.rating)}", fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(8.dp))
                        }
                        if (movie.year.isNotBlank()) { Text(movie.year, fontSize = 12.sp); Spacer(Modifier.width(8.dp)) }
                        if (movie.runtime > 0) { Text("${movie.runtime} мин", fontSize = 12.sp); Spacer(Modifier.width(8.dp)) }
                    }
                    if (movie.genres.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(movie.genres.joinToString(", "), fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (movie.overview.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(movie.overview, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                    if (movie.crew.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        val directors = movie.crew.filter { it.job == "Director" }.map { it.name }.distinct()
                        if (directors.isNotEmpty()) Text("🎬 Режиссёр: ${directors.joinToString(", ")}", fontSize = 11.sp)
                        val producers = movie.crew.filter { it.job == "Producer" }.map { it.name }.distinct().take(2)
                        if (producers.isNotEmpty()) Text("💼 Продюсер: ${producers.joinToString(", ")}", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val composers = movie.crew.filter { it.job == "Composer" }.map { it.name }.distinct().take(1)
                        if (composers.isNotEmpty()) Text("🎵 Музыка: ${composers.joinToString(", ")}", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else if (movie.director.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Режиссёр: ${movie.director}", fontSize = 11.sp)
                    }
                    if (movie.production_companies.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text("🏢 ${movie.production_companies.take(3).joinToString(", ") { it.name }}",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (movie.budget > 0 || movie.revenue > 0) {
                        Spacer(Modifier.height(4.dp))
                        val parts = mutableListOf<String>()
                        if (movie.budget > 0) parts.add("💰 Бюджет: ${formatMoney(movie.budget)}")
                        if (movie.revenue > 0) parts.add("💵 Сборы: ${formatMoney(movie.revenue)}")
                        Text(parts.joinToString(" · "), fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (movie.cast_details.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text("🎭 В ролях:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            movie.cast_details.take(8).forEach { c ->
                                Column(
                                    Modifier.width(60.dp)
                                        .clickable { onActorClick(c.id, c.name) },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        Modifier.size(48.dp).clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val photoUrl = remember(c.id) { TmdbRepository.actorPhotoUrl(c.profile_file) }
                                        if (photoUrl.isNotBlank()) {
                                            AsyncImage(model = photoUrl, contentDescription = c.name,
                                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                                contentScale = ContentScale.Crop)
                                        } else {
                                            Text("🎭", fontSize = 16.sp)
                                        }
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Text(c.name, fontSize = 9.sp, maxLines = 2,
                                        overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    } else if (movie.cast.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text("В ролях: ${movie.cast.take(5).joinToString(", ")}",
                            fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (movie.keywords.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("# " + movie.keywords.take(6).joinToString(" · "),
                            fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onSearch(movie.title); onDismiss() },
                            modifier = Modifier.weight(1f).height(40.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                            Text("🔍 Искать", fontSize = 12.sp, maxLines = 1, softWrap = false)
                        }
                        OutlinedButton(onClick = onDismiss,
                            modifier = Modifier.weight(1f).height(40.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                            Text("Закрыть", fontSize = 12.sp, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    }
}

private fun formatMoney(v: Long): String = when {
    v >= 1_000_000_000 -> "%.1f млрд".format(v / 1_000_000_000.0)
    v >= 1_000_000 -> "%.0f млн".format(v / 1_000_000.0)
    else -> "$v"
}
