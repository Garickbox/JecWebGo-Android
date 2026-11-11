package com.garickbox.jecwebgo.ui.status

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garickbox.jecwebgo.domain.TorrentRepository
import kotlinx.coroutines.delay

@Composable
fun StatusBar() {
    val ctx = LocalContext.current
    var tsAlive by remember { mutableStateOf<Boolean?>(null) }
    var jacAlive by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            tsAlive = TorrentRepository.isAlive()
            jacAlive = runCatching {
                com.garickbox.jecwebgo.data.network.HttpClient
                    .get("https://jac.red/api/v1.0/torrents?search=a").isNotBlank()
            }.getOrDefault(false)
            delay(30_000)
        }
    }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Dot("TorrServer", tsAlive)
        Dot("ТВ", null)
        Dot("JacRed", jacAlive)
        Dot("Постеры", null)
    }
}

@Composable
private fun Dot(label: String, state: Boolean?) {
    val color = when (state) {
        true -> Color(0xFF4CAF50)
        false -> Color(0xFFE53935)
        null -> Color(0xFF9E9E9E)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
