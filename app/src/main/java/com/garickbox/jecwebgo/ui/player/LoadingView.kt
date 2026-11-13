package com.garickbox.jecwebgo.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.garickbox.jecwebgo.util.PosterUtil

@Composable
fun LoadingView(
    title: String,
    description: String,
    poster: String,
    status: String,
    onCancel: () -> Unit
) {
    val url = PosterUtil.posterUrl(poster)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (url.isNotBlank()) {
            AsyncImage(
                model = url, contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop, alpha = 0.25f
            )
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))

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
            if (description.isNotBlank()) {
                Text(description, color = Color.White,
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
