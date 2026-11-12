package com.garickbox.jecwebgo.ui.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.garickbox.jecwebgo.data.network.TmdbMovie
import com.garickbox.jecwebgo.data.network.TmdbRepository

@Composable
fun TmdbDetailsDialog(movie: TmdbMovie, onDismiss: () -> Unit, onSearch: (String) -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth().padding(8.dp).heightIn(max = 640.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth().height(280.dp)) {
                    val url = TmdbRepository.posterUrl(movie)
                    if (url.isNotBlank()) {
                        AsyncImage(model = url, contentDescription = movie.title,
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Box(Modifier.fillMaxSize(), Alignment.Center) { Text("🎬", fontSize = 64.sp) }
                    }
                }
                Column(Modifier.padding(12.dp)) {
                    Text(movie.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    if (movie.original_title.isNotBlank() && movie.original_title != movie.title) {
                        Text(movie.original_title, fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
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
                    if (movie.director.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Режиссёр: ${movie.director}", fontSize = 11.sp)
                    }
                    if (movie.cast.isNotEmpty()) {
                        Text("В ролях: ${movie.cast.joinToString(", ")}",
                            fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onSearch(movie.title); onDismiss() },
                            modifier = Modifier.weight(1f)) { Text("🔍 Искать в базе") }
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Закрыть") }
                    }
                }
            }
        }
    }
}
