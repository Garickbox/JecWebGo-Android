package com.garickbox.jecwebgo.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

private data class MoreTile(val route: String, val title: String, val icon: ImageVector)

private val moreTiles = listOf(
    MoreTile("history", "История", Icons.Filled.History),
    MoreTile("bookmarks", "Метки", Icons.Filled.Star),
    MoreTile("settings", "Настройки", Icons.Filled.Settings),
    MoreTile("about", "О приложении", Icons.Filled.Info)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(nav: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Ещё", fontWeight = FontWeight.SemiBold) })
        }
    ) { pad ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(pad).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(moreTiles) { tile ->
                Card(
                    Modifier.fillMaxWidth().aspectRatio(1f).clickable {
                        nav.navigate(tile.route) { launchSingleTop = true }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(tile.icon, contentDescription = tile.title,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text(tile.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
