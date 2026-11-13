package com.garickbox.jecwebgo.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ErrorView(title: String, message: String, onRetry: (() -> Unit)?, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("⚠️", fontSize = 72.sp)
            Spacer(Modifier.height(16.dp))
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(message, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(32.dp))
            if (onRetry != null) {
                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text("🔄 Попробовать снова")
                }
                Spacer(Modifier.height(8.dp))
            }
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) { Text("Закрыть") }
        }
    }
}
