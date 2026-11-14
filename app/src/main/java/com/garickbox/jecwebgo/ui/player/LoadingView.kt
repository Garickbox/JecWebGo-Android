package com.garickbox.jecwebgo.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.garickbox.jecwebgo.util.PosterUtil
import androidx.compose.runtime.LaunchedEffect

@Composable
fun LoadingView(
    title: String,
    description: String,
    poster: String,
    status: String,
    loadingNonce: Long = 0L,
    onCancel: () -> Unit
) {
    // Local state — как в TmdbDetailsDialog. НЕ через PendingPlayer.updatePoster
    // (иначе LoadingView пересоздаётся → AsyncImage не получает URL → постер не грузится).
    var localPoster by remember(title) { mutableStateOf(poster) }
    var localDesc by remember(title) { mutableStateOf(description) }

    LaunchedEffect(title, loadingNonce) {
        if (title.isBlank()) return@LaunchedEffect
        if (localPoster.isNotBlank() && localDesc.isNotBlank()) return@LaunchedEffect
        try {
            val info = com.garickbox.jecwebgo.data.network.TmdbRepository.searchMovieInfo(title, "")
            if (info != null) {
                if (localPoster.isBlank() && info.posterUrl.isNotBlank()) {
                    android.util.Log.d("JecWeb", "LoadingView: poster via TMDB -> $title")
                    localPoster = info.posterUrl
                }
                if (localDesc.isBlank() && info.overview.isNotBlank()) {
                    android.util.Log.d("JecWeb", "LoadingView: description via TMDB -> $title (${info.overview.length} chars)")
                    localDesc = info.overview
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("JecWeb", "LoadingView searchMovieInfo fail: ${e.message}")
        }
    }

    val url = remember(localPoster) { PosterUtil.posterUrl(localPoster) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (url.isNotBlank()) {
            AsyncImage(
                model = url, contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // Градиент снизу для читаемости текста (постер полностью виден сверху)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.35f),
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.75f),
                            Color.Black.copy(alpha = 0.95f)
                        )
                    )
                )
            )
        }

        Box(Modifier.fillMaxSize().padding(16.dp)) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).size(32.dp),
                strokeWidth = 3.dp
            )
        }

        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Bottom
        ) {
            Text(title, color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            if (localDesc.isNotBlank()) {
                Text(localDesc, color = Color.White,
                    fontSize = 13.sp, lineHeight = 17.sp,
                    maxLines = 20, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
            }
            Text(status, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = onCancel,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) { Text("Отменить") }
        }
    }
}
