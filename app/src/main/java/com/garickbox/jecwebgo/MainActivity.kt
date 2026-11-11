package com.garickbox.jecwebgo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.garickbox.jecwebgo.service.TorrServerService
import com.garickbox.jecwebgo.ui.bookmarks.BookmarksScreen
import com.garickbox.jecwebgo.ui.history.HistoryScreen
import com.garickbox.jecwebgo.ui.settings.SettingsScreen
import com.garickbox.jecwebgo.ui.placeholders.MagnetsScreen
import com.garickbox.jecwebgo.ui.status.StatusBar
import com.garickbox.jecwebgo.ui.search.SearchScreen
import com.garickbox.jecwebgo.ui.home.HomeScreen
import com.garickbox.jecwebgo.ui.theme.JecWebTheme

data class Tab(val route: String, val label: String, val icon: ImageVector)
val tabs = listOf(
    Tab("home", "Главная", Icons.Filled.Home),
    Tab("search", "Поиск", Icons.Filled.Search),
    Tab("bookmarks", "Закладки", Icons.Filled.Star),
    Tab("history", "История", Icons.Filled.History),
    Tab("magnets", "Магниты", Icons.Filled.Download),
    Tab("settings", "Настройки", Icons.Filled.Settings)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        TorrServerService.start(this)
        setContent {
            JecWebTheme {
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
                                label = { Text(t.label) }
                            )
                        }
                    }
                }) { pad ->
                    Column(Modifier.fillMaxSize().padding(pad)) {
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
                        composable("magnets") { MagnetsScreen() }
                        composable("settings") { SettingsScreen() }
                    }
                    }
                }
            }
        }
    }
}
