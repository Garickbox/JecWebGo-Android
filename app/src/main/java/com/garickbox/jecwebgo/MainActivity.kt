package com.garickbox.jecwebgo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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

object MainState {
    @Volatile var currentRoute: String = "home"
}

class MainActivity : ComponentActivity() {
    private var handoffMagnet by mutableStateOf<String?>(null)
    private var handoffTorrent by mutableStateOf<ByteArray?>(null)
    private var handoffTitle by mutableStateOf("")

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        try {
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            val wic = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            wic.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            wic.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } catch (_: Exception) {}
        TorrServerService.start(this)
        com.garickbox.jecwebgo.data.download.DownloadRepository.init(this)
        handleIntent(intent)
        requestRuntimePermissions()
        setupPip()
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
                val pagerState = rememberPagerState(pageCount = { tabs.size })
                val scope = rememberCoroutineScope()
                var searchResetKey by remember { mutableIntStateOf(0) }
                val playerRequest by PendingPlayer.data.collectAsState()
                LaunchedEffect(pendingTabFromIntent) {
                    pendingTabFromIntent?.let { t ->
                        val idx = tabs.indexOfFirst { it.route == t }
                        if (idx >= 0) pagerState.animateScrollToPage(idx)
                        pendingTabFromIntent = null
                    }
                }
                LaunchedEffect(playerRequest?.loadingNonce) {
                    val pr = playerRequest
                    if (pr != null && pr.title.isNotBlank()
                        && nav.graph != null
                        && nav.currentDestination?.route != "player") {
                        try {
                            nav.navigate("player") { launchSingleTop = true }
                        } catch (e: Exception) {
                            android.util.Log.e("JecWeb", "nav.navigate(player) fail: ${e.message}")
                        }
                    }
                }
                val entry by nav.currentBackStackEntryAsState()
                val subRoute = entry?.destination?.route
                val inSubroute = subRoute != null && subRoute != "empty"
                val pagerRoute = tabs.getOrNull(pagerState.currentPage)?.route ?: "home"
                val current = if (inSubroute) (subRoute ?: "home") else pagerRoute
                val selectedTab = when {
                    !inSubroute -> pagerRoute
                    subRoute == "player" -> pagerRoute
                    subRoute in moreRoutes -> "more"
                    else -> subRoute ?: pagerRoute
                }
                var pendingSearch by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(subRoute, pagerState.currentPage) {
                    android.util.Log.d("JecWeb", "UI: subRoute=$subRoute inSubroute=$inSubroute page=${pagerState.currentPage} current=$current")
                    updatePipForRoute(subRoute ?: "empty")
                    try {
                        val wic = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                        wic.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                        wic.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    } catch (_: Exception) {}
                }
                Scaffold(contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0), bottomBar = {
                    if (subRoute != "player") {
                        // Стеклянная панель: Box с clip+bg+padding, NavigationBar прозрачный
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 8.dp, end = 8.dp, bottom = 6.dp)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f))
                        ) {
                            NavigationBar(
                                modifier = Modifier.height(56.dp),
                                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                                tonalElevation = 0.dp,
                                windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
                            ) {
                                tabs.forEachIndexed { idx, t ->
                                    NavigationBarItem(
                                        selected = selectedTab == t.route,
                                        onClick = {
                                            if (t.route == "search") searchResetKey++
                                            if (inSubroute) nav.popBackStack("empty", inclusive = false)
                                            scope.launch { pagerState.animateScrollToPage(idx) }
                                        },
                                        icon = {
                                            Icon(t.icon, contentDescription = t.label, modifier = Modifier.size(22.dp))
                                        }
                                    )
                                }
                            }
                        }
                    }
                }) { pad ->
                    Box(Modifier.padding(pad).fillMaxSize()) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            beyondViewportPageCount = 1
                        ) { page ->
                            when (page) {
                                0 -> HomeScreen(onOpenSearch = { q ->
                                    pendingSearch = q
                                    scope.launch { pagerState.animateScrollToPage(1) }
                                })
                                1 -> SearchScreen(
                                    pendingQuery = pendingSearch,
                                    onQueryConsumed = { pendingSearch = null },
                                    resetKey = searchResetKey
                                )
                                2 -> DownloadScreen()
                                3 -> MoreScreen(nav)
                            }
                        }
                        if (!inSubroute && pagerState.currentPage == 0) {
                            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
                                StatusBar()
                            }
                        }
                        NavHost(nav, "empty", Modifier.fillMaxSize()) {
                            composable("empty") {}
                            composable("history") { HistoryScreen(onBack = { nav.popBackStack() }) }
                            composable("bookmarks") { BookmarksScreen(onBack = { nav.popBackStack() }) }
                            composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
                            composable("about") { AboutScreen(onBack = { nav.popBackStack() }) }
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
                                        loadingNonce = pr.loadingNonce,
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
                                        localKey = pr.localKey,
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
    private var pendingTabFromIntent by mutableStateOf<String?>(null)

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

    private fun setupPip() {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            try {
                // НЕ ставим setAutoEnterEnabled вообще — default = false.
                // PiP только явно через enterPictureInPictureMode() в onUserLeaveHint.
                val params = android.app.PictureInPictureParams.Builder()
                    .setAspectRatio(android.util.Rational(16, 9))
                    .build()
                setPictureInPictureParams(params)
                android.util.Log.d("JecWeb", "PiP configured: default autoEnter=false")
            } catch (e: Exception) {
                android.util.Log.e("JecWeb", "setupPip fail: ${e.message}")
            }
        }
    }

    fun updatePipForRoute(route: String) {
        // ТОЛЬКО запоминаем route. autoEnterEnabled НИКОГДА не ставим true —
        // иначе система автоматически входит в PiP на любом уходе (баг CP10.121).
        // Явный вход в PiP — только в onUserLeaveHint при route=="player".
        val prev = MainState.currentRoute
        MainState.currentRoute = route
        if (prev != route) {
            android.util.Log.d("JecWeb", "PiP route: $prev -> $route")
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (MainState.currentRoute != "player") {
            android.util.Log.d("JecWeb", "onUserLeaveHint: route=${MainState.currentRoute} — NO PiP")
            return
        }
        if (android.os.Build.VERSION.SDK_INT < 26) return
        try {
            val params = android.app.PictureInPictureParams.Builder()
                .setAspectRatio(android.util.Rational(16, 9))
                .build()
            val ok = enterPictureInPictureMode(params)
            android.util.Log.d("JecWeb", "enterPip explicit: route=player ok=$ok")
        } catch (e: Exception) {
            android.util.Log.e("JecWeb", "enterPip fail: ${e.message}")
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        android.util.Log.d("JecWeb", "onConfigurationChanged: orientation=${newConfig.orientation} screenW=${newConfig.screenWidthDp}dp")
        // Activity НЕ пересоздаётся (configChanges в манифесте) — надо переприменить hide bars
        window.decorView.post {
            try {
                val wic = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                wic.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                wic.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                android.util.Log.d("JecWeb", "onConfigChanged: bars re-hidden")
            } catch (e: Exception) {
                android.util.Log.e("JecWeb", "onConfigChanged hide fail: ${e.message}")
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPip: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPip, newConfig)
        com.garickbox.jecwebgo.ui.player.PipState.isInPip.value = isInPip
        android.util.Log.d("JecWeb", "PiP mode=$isInPip")
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            try {
                val wic = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                wic.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                wic.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } catch (_: Exception) {}
            // updatePipForRoute УБРАН: был источник PiP на всех окнах
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun getDisplayName(uri: android.net.Uri): String? {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
        } catch (e: Exception) {
            android.util.Log.e("JecWeb", "getDisplayName fail: ${e.message}")
            null
        }
    }

    private fun handleIntent(intent: Intent?) {
        val openTab = intent?.getStringExtra("open_tab")
        if (openTab != null) {
            pendingTabFromIntent = openTab
            intent.removeExtra("open_tab")
        }
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
            return
        }

        // Открытие видео извне (файловый менеджер, Telegram, браузер и т.д.)
        val mime = intent.type ?: try { contentResolver.getType(data) } catch (_: Exception) { null } ?: ""
        if ((scheme == "content" || scheme == "file") && mime.startsWith("video/")) {
            val fileName = getDisplayName(data) ?: data.lastPathSegment?.substringAfterLast("/") ?: "Video"
            try {
                if (scheme == "content") {
                    contentResolver.takePersistableUriPermission(
                        data, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    android.util.Log.d("JecWeb", "video intent: persistable OK")
                }
            } catch (e: Exception) {
                android.util.Log.d("JecWeb", "video intent: no persistable (${e.message})")
            }
            android.util.Log.d("JecWeb", "video intent: uri=$data mime=$mime name=$fileName")
            com.garickbox.jecwebgo.ui.player.PendingPlayer.openReady(
                url = data.toString(),
                title = fileName,
                hash = "",
                currentIndex = 1,
                fileName = fileName
            )
            return
        }
    }
}
