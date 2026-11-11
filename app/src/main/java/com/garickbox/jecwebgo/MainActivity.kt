package com.garickbox.jecwebgo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.garickbox.jecwebgo.service.TorrServerService
import com.garickbox.jecwebgo.ui.bookmarks.BookmarksScreen
import com.garickbox.jecwebgo.ui.download.DownloadScreen
import com.garickbox.jecwebgo.ui.handoff.HandoffDialog
import com.garickbox.jecwebgo.ui.history.HistoryScreen
import com.garickbox.jecwebgo.ui.home.HomeScreen
import com.garickbox.jecwebgo.ui.search.SearchScreen
import com.garickbox.jecwebgo.ui.settings.SettingsScreen
import com.garickbox.jecwebgo.ui.status.StatusBar
import com.garickbox.jecwebgo.ui.theme.JecWebTheme

data class Tab(val route: String, val label: String, val icon: ImageVector)
val tabs = listOf(
    Tab("home", "Главная", Icons.Filled.Home),
    Tab("search", "Поиск", Icons.Filled.Search),
    Tab("bookmarks", "Метки", Icons.Filled.Star),
    Tab("history", "История", Icons.Filled.History),
    Tab("downloads", "Загрузки", Icons.Filled.Download),
    Tab("settings", "Настройки", Icons.Filled.Settings)
)

class MainActivity : ComponentActivity() {
    private var handoffMagnet by mutableStateOf<String?>(null)
    private var handoffTorrent by mutableStateOf<ByteArray?>(null)
    private var handoffTitle by mutableStateOf("")

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        TorrServerService.start(this)
        handleIntent(intent)
        setContent {
            JecWebTheme {
                HandoffDialog(
                    magnet = handoffMagnet,
                    torrentBytes = handoffTorrent,
                    title = handoffTitle,
                    onDismiss = { handoffMagnet = null; handoffTorrent = null; handoffTitle = "" }
                )
                val nav = rememberNavController()
                val entry by nav.currentBackStackEntryAsState()
                val current = entry?.destination?.route ?: "home"
                var pendingSearch by remember { mutableStateOf<String?>(null) }
                Scaffold(bottomBar = {
                    NavigationBar {
                        tabs.forEach { t ->
                            NavigationBarItem(
                                selected = current == t.route,
                                onClick = {
                                    if (current != t.route) nav.navigate(t.route) {
                                        popUpTo("home"); launchSingleTop = true
                                    }
                                },
                                icon = { Icon(t.icon, t.label) },
                                label = { Text(t.label, maxLines = 1, softWrap = false, fontSize = 10.sp) }
                            )
                        }
                    }
                }) { pad ->
                    androidx.compose.foundation.layout.Column(
                        Modifier.padding(pad).fillMaxSize()
                    ) {
                        StatusBar()
                        NavHost(nav, "home", Modifier.fillMaxSize()) {
                            composable("home") { HomeScreen(onOpenSearch = { q ->
                                pendingSearch = q
                                nav.navigate("search") { popUpTo("search"); launchSingleTop = true }
                            }) }
                            composable("search") { SearchScreen(
                                pendingQuery = pendingSearch,
                                onQueryConsumed = { pendingSearch = null }
                            ) }
                            composable("bookmarks") { BookmarksScreen() }
                            composable("history") { HistoryScreen() }
                            composable("downloads") { DownloadScreen() }
                            composable("settings") { SettingsScreen() }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        val scheme = data.scheme ?: return
        if (scheme == "magnet") {
            val dn = data.getQueryParameter("dn") ?: "Magnet"
            handoffMagnet = data.toString()
            handoffTorrent = null
            handoffTitle = dn
            return
        }
        if ((scheme == "content" || scheme == "file") && intent.type == "application/x-bittorrent") {
            try {
                val bytes = contentResolver.openInputStream(data)?.use { it.readBytes() } ?: return
                handoffMagnet = null
                handoffTorrent = bytes
                handoffTitle = data.lastPathSegment?.removeSuffix(".torrent") ?: "Torrent"
            } catch (_: Exception) {}
        }
    }
}
