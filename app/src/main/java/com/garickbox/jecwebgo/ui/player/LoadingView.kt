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
fun LoadingView(title: String, poster: String, status: String, onCancel: () -> Unit) {
    val url = PosterUtil.posterUrl(poster)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (url.isNotBlank()) {
            AsyncImage(
                model = url, contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop, alpha = 0.4f
            )
        }
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(64.dp), strokeWidth = 4.dp)
            Spacer(Modifier.height(24.dp))
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Text(status, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
            Spacer(Modifier.height(32.dp))
            OutlinedButton(
                onClick = onCancel,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) { Text("Отменить") }
        }
    }
}
