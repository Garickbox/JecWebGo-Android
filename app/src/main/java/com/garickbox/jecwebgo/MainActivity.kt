package com.garickbox.jecwebgo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.garickbox.jecwebgo.service.TorrServerService
import com.garickbox.jecwebgo.ui.about.AboutScreen
import com.garickbox.jecwebgo.ui.bookmarks.BookmarksScreen
import com.garickbox.jecwebgo.ui.download.DownloadScreen
import com.garickbox.jecwebgo.ui.handoff.HandoffDialog
import com.garickbox.jecwebgo.ui.history.HistoryScreen
import com.garickbox.jecwebgo.ui.home.HomeScreen
import com.garickbox.jecwebgo.ui.more.MoreScreen
import com.garickbox.jecwebgo.ui.player.PendingPlayer
import com.garickbox.jecwebgo.ui.player.ErrorView
import com.garickbox.jecwebgo.ui.player.LoadingView
import com.garickbox.jecwebgo.ui.player.PlayerScreen
import com.garickbox.jecwebgo.ui.search.SearchScreen
import com.garickbox.jecwebgo.ui.settings.SettingsScreen
import com.garickbox.jecwebgo.ui.status.StatusBar
import com.garickbox.jecwebgo.ui.theme.JecWebTheme

data class Tab(val route: String, val label: String, val icon: ImageVector)
val tabs = listOf(
    Tab("home", "Главная", Icons.Filled.Home),
    Tab("search", "Поиск", Icons.Filled.Search),
    Tab("downloads", "Загрузки", Icons.Filled.Download),
    Tab("more", "Ещё", Icons.Filled.MoreHoriz)
)

private val moreRoutes = setOf("more", "history", "bookmarks", "settings", "about")

class MainActivity : ComponentActivity() {
    private var handoffMagnet by mutableStateOf<String?>(null)
    private var handoffTorrent by mutableStateOf<ByteArray?>(null)
    private var handoffTitle by mutableStateOf("")

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        TorrServerService.start(this)
        com.garickbox.jecwebgo.data.download.DownloadRepository.init(this)
        handleIntent(intent)
        requestRuntimePermissions()
        setContent {
            JecWebTheme {
                HandoffDialog(
                    magnet = handoffMagnet,
                    torrentBytes = handoffTorrent,
                    title = handoffTitle,
                    onDismiss = { handoffMagnet = null; handoffTorrent = null; handoffTitle = "" }
                )
                if (needBatteryOptRequest) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { needBatteryOptRequest = false },
                        title = { androidx.compose.material3.Text("⚡ Разрешение энергосбережения") },
                        text = { androidx.compose.material3.Text(
                            "На MIUI/POCO система может убивать TorrServer в фоне через 1-2 минуты. " +
                            "Чтобы Play и Download работали стабильно — отключите оптимизацию батареи для JecWeb Go."
                        ) },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = {
                                needBatteryOptRequest = false
                                try {
                                    val i = android.content.Intent(
                                        android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                        android.net.Uri.parse("package:$packageName")
                                    )
                                    startActivity(i)
                                } catch (_: Exception) {
                                    try {
                                        startActivity(android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                    } catch (_: Exception) {}
                                }
                            }) { androidx.compose.material3.Text("Открыть настройки") }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(onClick = { needBatteryOptRequest = false }) {
                                androidx.compose.material3.Text("Позже")
                            }
                        }
                    )
                }
                if (needAdminRequest) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { needAdminRequest = false },
                        title = { androidx.compose.material3.Text("🔐 Управление экраном") },
                        text = { androidx.compose.material3.Text(
                            "Разрешите JecWeb Go управление экраном для:\n" +
                            "• Полной блокировки касаний (System Lock)\n" +
                            "• Мгновенного гашения экрана по таймеру сна\n\n" +
                            "Разрешение запрашивается один раз. Отозвать можно в Настройки → Безопасность → Администрирование."
                        ) },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = {
                                needAdminRequest = false
                                try {
                                    val i = android.content.Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                                    i.putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                                        com.garickbox.jecwebgo.admin.AdminReceiver.componentName(this@MainActivity))
                                    i.putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                        "System Lock + блокировка экрана по таймеру")
                                    startActivity(i)
                                } catch (_: Exception) {}
                            }) { androidx.compose.material3.Text("Активировать") }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(onClick = { needAdminRequest = false }) {
                                androidx.compose.material3.Text("Позже")
                            }
                        }
                    )
                }
                val nav = rememberNavController()
                var searchResetKey by remember { mutableStateOf(0) }
                val playerRequest by PendingPlayer.data.collectAsState()
                LaunchedEffect(playerRequest) {
                    if (playerRequest != null) nav.navigate("player") { launchSingleTop = true }
                }
                val entry by nav.currentBackStackEntryAsState()
                val current = entry?.destination?.route ?: "home"
                val selectedTab = if (current in moreRoutes) "more" else current
                var pendingSearch by remember { mutableStateOf<String?>(null) }
                Scaffold(bottomBar = {
                    if (current != "player") NavigationBar {
                        tabs.forEach { t ->
                            NavigationBarItem(
                                selected = selectedTab == t.route,
                                onClick = {
                                    if (t.route == "search") searchResetKey++
                                    if (current != t.route) nav.navigate(t.route) {
                                        popUpTo("home")
                                        launchSingleTop = true
                                    }
                                },
                                icon = {
                                    Icon(t.icon, contentDescription = t.label, modifier = Modifier.size(26.dp))
                                }
                            )
                        }
                    }
                }) { pad ->
                    Column(Modifier.padding(pad).fillMaxSize()) {
                        if (current != "player") StatusBar()
                        NavHost(nav, "home", Modifier.fillMaxSize()) {
                            composable("home") { HomeScreen(onOpenSearch = { q ->
                                pendingSearch = q
                                nav.navigate("search") { popUpTo("search"); launchSingleTop = true }
                            }) }
                            composable("search") { SearchScreen(
                                pendingQuery = pendingSearch,
                                onQueryConsumed = { pendingSearch = null },
                                resetKey = searchResetKey
                            ) }
                            composable("downloads") { DownloadScreen() }
                            composable("more") { MoreScreen(nav) }
                            composable("history") { HistoryScreen() }
                            composable("bookmarks") { BookmarksScreen() }
                            composable("settings") { SettingsScreen() }
                            composable("about") { AboutScreen() }
                            composable("player") {
                                val pr = playerRequest
                                if (pr == null) {
                                    LaunchedEffect(Unit) { nav.popBackStack() }
                                } else if (pr.loading) {
                                    LoadingView(
                                        title = pr.title,
                                        description = pr.description,
                                        poster = pr.poster,
                                        status = pr.status,
                                        onCancel = {
                                            PendingPlayer.consume()
                                            nav.popBackStack()
                                        }
                                    )
                                } else if (pr.error != null) {
                                    ErrorView(
                                        title = pr.title,
                                        message = pr.error ?: "Ошибка",
                                        onRetry = null,
                                        onBack = {
                                            PendingPlayer.consume()
                                            nav.popBackStack()
                                        }
                                    )
                                } else {
                                    PlayerScreen(
                                        url = pr.url,
                                        title = pr.title,
                                        hash = pr.hash,
                                        currentIndex = pr.currentIndex,
                                        fileName = pr.fileName,
                                        poster = pr.poster,
                                        description = pr.description,
                                        onBack = {
                                            PendingPlayer.consume()
                                            nav.popBackStack()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private var needBatteryOptRequest by mutableStateOf(false)

    private var needAdminRequest by mutableStateOf(false)

    private fun requestRuntimePermissions() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100)
        }
        try {
            val pm = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                needBatteryOptRequest = true
            }
        } catch (_: Exception) {}
        if (com.garickbox.jecwebgo.admin.AdminReceiver.isActive(this)) {
            com.garickbox.jecwebgo.admin.AdminReceiver.applyWhitelist(this)
        } else {
            needAdminRequest = true
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
