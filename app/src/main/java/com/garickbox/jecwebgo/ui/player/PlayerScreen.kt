package com.garickbox.jecwebgo.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.garickbox.jecwebgo.data.store.SettingsStore
import com.garickbox.jecwebgo.domain.TorrentFile
import com.garickbox.jecwebgo.domain.TorrentRepository
import com.garickbox.jecwebgo.util.TitleUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import android.media.AudioManager
import android.os.PowerManager
import android.app.admin.DevicePolicyManager
import com.garickbox.jecwebgo.admin.AdminReceiver
import com.garickbox.jecwebgo.admin.TouchBlockerService
import kotlin.math.abs
import com.garickbox.jecwebgo.data.store.PlayLogStore
import com.garickbox.jecwebgo.data.store.PlayLogEntry
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@Composable
fun PlayerScreen(
    url: String,
    title: String,
    hash: String = "",
    currentIndex: Int = 1,
    fileName: String = "",
    poster: String = "",
    description: String = "",
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val savedMode = remember { runCatching { SettingsStore.load(ctx).hw_mode }.getOrDefault("full") }
    var effectiveMode by remember { mutableStateOf(savedMode) }
    val cleanMainTitle = remember(title) { TitleUtil.cleanTitle(title) }

    fun fallback() {
        val next = when (effectiveMode) {
            "full" -> "auto"
            "auto" -> "sw"
            else -> null
        }
        if (next != null) {
            Toast.makeText(ctx, "Кодек не поддержан HW, переключаю на ${next.uppercase()}", Toast.LENGTH_LONG).show()
            effectiveMode = next
        } else {
            Toast.makeText(ctx, "Ошибка воспроизведения", Toast.LENGTH_SHORT).show()
            onBack()
        }
    }

    key(effectiveMode) {
        PlayerScreenImpl(
            url = url,
            mainTitle = cleanMainTitle,
            hash = hash,
            currentIndex = currentIndex,
            fileName = fileName,
            poster = poster,
            description = description,
            hwMode = effectiveMode,
            onBack = onBack,
            onError = { fallback() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerScreenImpl(
    url: String,
    mainTitle: String,
    hash: String,
    currentIndex: Int,
    fileName: String,
    poster: String,
    description: String,
    hwMode: String,
    onBack: () -> Unit,
    onError: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val useTextureView = hwMode == "auto"

    val activity = remember(context) { context.findActivity() }
    val dpm = remember(context) { context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager }
    DisposableEffect(Unit) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                if (i?.action == TouchBlockerService.ACTION_UNLOCK) {
                    uiLocked = false
                    lockIconPulse = false
                    try { activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED } catch (_: Exception) {}
                    android.util.Log.d("JecWeb", "TouchBlocker unlock broadcast -> uiLocked=false")
                }
            }
        }
        val filter = android.content.IntentFilter(TouchBlockerService.ACTION_UNLOCK)
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
        } catch (_: Exception) {}
        onDispose { try { context.unregisterReceiver(receiver) } catch (_: Exception) {} }
    }
    DisposableEffect(activity) {
        onDispose {
            try {
                activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            } catch (_: Exception) {}
        }
    }
    DisposableEffect(activity) {
        activity?.let {
            val wic = WindowCompat.getInsetsController(it.window, it.window.decorView)
            wic.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            wic.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            activity?.let {
                val wic = WindowCompat.getInsetsController(it.window, it.window.decorView)
                wic.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    BackHandler { onBack() }

    val vlcView = remember {
        VLCVideoLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }
    val libVLC = remember(hwMode) {
        LibVLC(context, arrayListOf(
            "--audio-time-stretch",
            "--audio-resampler=soxr",
            "--pitch-shift",
            "--network-caching=15000",
            "--http-reconnect",
            "--no-drop-late-frames",
            "--no-skip-frames"
        ))
    }
    val mediaPlayer = remember(libVLC) { MediaPlayer(libVLC) }

    var currentUrl by remember { mutableStateOf(url) }
    var currentTitle by remember { mutableStateOf(mainTitle) }
    var currentIdx by remember { mutableIntStateOf(currentIndex) }
    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var backgroundedAt by remember { mutableLongStateOf(0L) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var audioSheet by remember { mutableStateOf(false) }
    var subSheet by remember { mutableStateOf(false) }
    var seriesSheet by remember { mutableStateOf(false) }
    var audioTracks by remember { mutableStateOf<List<Pair<Int, String>>>(emptyList()) }
    var subTracks by remember { mutableStateOf<List<Pair<Int, String>>>(emptyList()) }
    var seriesList by remember { mutableStateOf<List<TorrentFile>>(emptyList()) }
    var loadingSeries by remember { mutableStateOf(false) }
    var firstFrameReady by remember { mutableStateOf(false) }
    var bufferingPct by remember { mutableIntStateOf(0) }
    var playbackRate by remember { mutableFloatStateOf(1.0f) }
    var aspectIndex by remember { mutableIntStateOf(0) }
    var userSelectedAudio by remember { mutableStateOf(false) }
    var userSelectedSubs by remember { mutableStateOf(false) }
    var tracksLoadedOnce by remember { mutableStateOf(false) }
    var optionsSheet by remember { mutableStateOf(false) }
    var isSeriesTransition by remember { mutableStateOf(false) }
    var preloadedNextFor by remember { mutableLongStateOf(-1L) }
    var gestureHint by remember { mutableStateOf("") }
    var gestureVisible by remember { mutableStateOf(false) }
    var dragMode by remember { mutableIntStateOf(0) }
    var seekTarget by remember { mutableLongStateOf(0L) }
    var dragStartTimeMs by remember { mutableLongStateOf(0L) }
    var dragStartVolume by remember { mutableIntStateOf(0) }
    var dragStartBrightness by remember { mutableFloatStateOf(0.5f) }
    var dragStartX by remember { mutableFloatStateOf(0f) }
    var dragStartY by remember { mutableFloatStateOf(0f) }
    var sleepTimerEndMs by remember { mutableLongStateOf(0L) }
    var sleepTimerMinutes by remember { mutableIntStateOf(0) }
    var stopAtFileEnd by remember { mutableStateOf(false) }
    var sleepSheet by remember { mutableStateOf(false) }
    var sleepTimerRemaining by remember { mutableLongStateOf(0L) }
    var uiLocked by remember { mutableStateOf(false) }
    var lockIconPulse by remember { mutableStateOf(false) }

    DisposableEffect(mediaPlayer, vlcView, useTextureView) {
        mediaPlayer.attachViews(vlcView, null, false, useTextureView)
        if (!useTextureView) {
            vlcView.post {
                for (i in 0 until vlcView.childCount) {
                    (vlcView.getChildAt(i) as? android.view.SurfaceView)?.setZOrderMediaOverlay(true)
                }
            }
        }
        val listener = MediaPlayer.EventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing -> { isPlaying = true; android.util.Log.d("JecWeb", "VLC Playing") }
                MediaPlayer.Event.Paused -> {
                    isPlaying = false
                    if (playbackRate != 1.0f) {
                        mediaPlayer.rate = 1.0f
                        playbackRate = 1.0f
                        android.util.Log.d("JecWeb", "VLC rate reset to 1.0 after pause")
                    }
                }
                MediaPlayer.Event.Stopped -> isPlaying = false
                MediaPlayer.Event.EndReached -> {
                    android.util.Log.d("JecWeb", "EndReached seriesList=${seriesList.size} curIdx=$currentIdx stopAtFileEnd=$stopAtFileEnd")
                    if (stopAtFileEnd) {
                        android.util.Log.d("JecWeb", "EndReached: sleep timer stopAtFileEnd -> pause")
                        runCatching { mediaPlayer.pause() }
                        return@EventListener
                    }
                    if (seriesList.size > 1) {
                        val idx = seriesList.indexOfFirst { it.id == currentIdx }
                        val next = seriesList.getOrNull(idx + 1)
                        if (next != null) {
                            isSeriesTransition = true
                            val fName = next.path.substringAfterLast("/")
                            currentUrl = "http://127.0.0.1:8090/stream?link=$hash&index=${next.id}&play&t=${System.currentTimeMillis()}"
                            currentTitle = "$mainTitle · $fName"
                            currentIdx = next.id
                            android.util.Log.d("JecWeb", "auto-next: $fName id=${next.id}")
                        } else {
                            android.util.Log.d("JecWeb", "auto-next: last, back")
                            onBack()
                        }
                    } else {
                        onBack()
                    }
                }
                MediaPlayer.Event.TimeChanged -> {
                    position = event.timeChanged
                    if (event.timeChanged > 100 && !firstFrameReady) {
                        firstFrameReady = true
                        android.util.Log.d("JecWeb", "VLC firstFrameReady at ${event.timeChanged}ms")
                    }
                    // Предзагрузка следующей серии за 60с до конца
                    if (duration > 0 && position > 0 && seriesList.size > 1 && (duration - position) in 1..300000L) {
                        val curI = seriesList.indexOfFirst { it.id == currentIdx }
                        val nxt = seriesList.getOrNull(curI + 1)
                        if (nxt != null && preloadedNextFor != nxt.id.toLong()) {
                            preloadedNextFor = nxt.id.toLong()
                            android.util.Log.d("JecWeb", "preload next серия: idx=${nxt.id} за ${(duration-position)/1000}s до конца")
                            scope.launch {
                                try {
                                    TorrentRepository.preloadFile(hash, nxt.id, 30, 2)
                                    android.util.Log.d("JecWeb", "preload done: idx=${nxt.id} (30MB)")
                                } catch (e: Exception) {
                                    android.util.Log.e("JecWeb", "preload err: ${e.message}")
                                }
                            }
                        }
                    }
                }
                MediaPlayer.Event.LengthChanged -> duration = event.lengthChanged
                MediaPlayer.Event.Buffering -> {
                    bufferingPct = event.buffering.toInt()
                    if (bufferingPct % 10 == 0) android.util.Log.d("JecWeb", "VLC buffering $bufferingPct%")
                }
                MediaPlayer.Event.EncounteredError -> {
                    android.util.Log.e("JecWeb", "VLC EncounteredError")
                    firstFrameReady = false
                    onError()
                }
            }
        }
        mediaPlayer.setEventListener(listener)
        onDispose {
            val mp = mediaPlayer
            val lv = libVLC
            val vv = vlcView
            Thread {
                runCatching { mp.setEventListener(null) }
                runCatching { mp.stop() }
                runCatching { mp.detachViews() }
                runCatching { mp.release() }
                runCatching { lv.release() }
                runCatching { vv.removeAllViews() }
            }.apply { isDaemon = true }.start()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    backgroundedAt = System.currentTimeMillis()
                    runCatching { mediaPlayer.pause() }
                }
                Lifecycle.Event.ON_START -> {
                    if (backgroundedAt > 0L) {
                        val away = System.currentTimeMillis() - backgroundedAt
                        if (away > 5 * 60 * 1000L) reloadKey++
                        else runCatching { mediaPlayer.play() }
                        backgroundedAt = 0L
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    var wakeLock by remember { mutableStateOf<PowerManager.WakeLock?>(null) }

    fun startSleepTimer(minutes: Int, toEnd: Boolean) {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        if (!toEnd && minutes <= 0) {
            sleepTimerEndMs = 0L
            sleepTimerMinutes = 0
            stopAtFileEnd = false
            android.util.Log.d("JecWeb", "sleep timer off")
            return
        }
        val ms = if (toEnd) (duration - position).coerceAtLeast(0L) else minutes * 60_000L
        if (ms <= 0) return
        sleepTimerEndMs = System.currentTimeMillis() + ms
        sleepTimerMinutes = if (toEnd) -1 else minutes
        stopAtFileEnd = toEnd
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "JecWeb:SleepTimer").apply {
            setReferenceCounted(false)
            acquire(ms + 120_000L)
        }
        android.util.Log.d("JecWeb", "sleep timer: toEnd=$toEnd minutes=$minutes ms=$ms")
    }

    fun gotoSeries(targetIdx: Int) {
        val f = seriesList.getOrNull(targetIdx) ?: return
        val fName = f.path.substringAfterLast("/")
        isSeriesTransition = true
        currentUrl = "http://127.0.0.1:8090/stream?link=$hash&index=${f.id}&play&t=${System.currentTimeMillis()}"
        currentTitle = "$mainTitle · $fName"
        currentIdx = f.id
        android.util.Log.d("JecWeb", "gotoSeries: $fName id=${f.id} (transition)")
    }

    fun prevSeries() {
        val idx = seriesList.indexOfFirst { it.id == currentIdx }
        if (idx > 0) gotoSeries(idx - 1) else Toast.makeText(context, "Первая серия", Toast.LENGTH_SHORT).show()
    }

    fun nextSeries() {
        val idx = seriesList.indexOfFirst { it.id == currentIdx }
        if (idx >= 0 && idx + 1 < seriesList.size) gotoSeries(idx + 1) else Toast.makeText(context, "Последняя серия", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(sleepTimerEndMs) {
        if (sleepTimerEndMs <= 0L) {
            sleepTimerRemaining = 0L
            return@LaunchedEffect
        }
        while (true) {
            val rem = sleepTimerEndMs - System.currentTimeMillis()
            sleepTimerRemaining = rem.coerceAtLeast(0L)
            if (rem <= 0L) break
            delay(1000L)
        }
        // Сработал — reset ПОСЛЕ finish, иначе LaunchedEffect(sleepTimerEndMs) перезапустится
        // и убьёт корутину ДО finishAndRemoveTask()
        android.util.Log.d("JecWeb", "sleep timer FIRED")
        runCatching { mediaPlayer.pause() }
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        Toast.makeText(context, "🌙 Таймер сна: пауза", Toast.LENGTH_LONG).show()
        delay(500L)
        val act = activity ?: context.findActivity()
        android.util.Log.d("JecWeb", "sleep timer: activity=$act")
        if (AdminReceiver.isActive(context)) {
            try {
                dpm.lockNow()
                android.util.Log.d("JecWeb", "sleep timer: lockNow OK")
            } catch (e: Exception) {
                android.util.Log.e("JecWeb", "sleep timer: lockNow fail: ${e.message}")
            }
        }
        try {
            act?.finishAndRemoveTask()
            android.util.Log.d("JecWeb", "sleep timer: finishAndRemoveTask OK")
        } catch (e: Exception) {
            android.util.Log.e("JecWeb", "sleep timer: finish fail: ${e.message}")
            try { act?.finishAffinity() } catch (_: Exception) {}
        }
        sleepTimerEndMs = 0L
        sleepTimerMinutes = 0
        stopAtFileEnd = false
    }

    DisposableEffect(Unit) {
        onDispose {
            wakeLock?.let { if (it.isHeld) it.release() }
        }
    }

    LaunchedEffect(currentUrl, reloadKey) {
        if (isSeriesTransition) {
            android.util.Log.d("JecWeb", "series transition — БЕЗ stop/firstFrameReady сброса")
            isSeriesTransition = false
        } else {
            runCatching { mediaPlayer.stop() }
            isPlaying = false
            position = 0L
            duration = 0L
            firstFrameReady = false
            bufferingPct = 0
            playbackRate = 1.0f
            userSelectedAudio = false
            userSelectedSubs = false
            tracksLoadedOnce = false
        }
        val isAvi = fileName.endsWith(".avi", ignoreCase = true)
        android.util.Log.d("JecWeb", "PlayerScreen play file=$fileName hw=$hwMode isAvi=$isAvi url=$currentUrl")
        val media = Media(libVLC, Uri.parse(currentUrl)).apply {
            // AVI (DivX/XviD) не поддерживается MediaCodec — принудительно SW
            if (isAvi) {
                setHWDecoderEnabled(false, false)
                addOption(":avcodec-hw=disabled")
            } else {
                when (hwMode) {
                    "full" -> setHWDecoderEnabled(true, true)
                    "auto" -> setHWDecoderEnabled(true, false)
                    "sw" -> setHWDecoderEnabled(false, false)
                }
            }
            if (isAvi) {
                addOption(":network-caching=20000")
                addOption(":demux=avformat")
                addOption(":no-seektable")
            } else {
                addOption(":network-caching=15000")
            }
            addOption(":file-caching=15000")
            addOption(":start-time=0")
            addOption(":clock-jitter=0")
            addOption(":clock-synchro=0")
        }
        mediaPlayer.media = media
        media.release()
        val playOk = mediaPlayer.play()
        android.util.Log.d("JecWeb", "VLC play() ok=$playOk isAvi=$isAvi hw=$hwMode")

        // Prefetch seriesList для auto-next
        if (hash.isNotBlank()) {
            scope.launch {
                try {
                    seriesList = TorrentRepository.getFiles(hash)
                        .filter {
                            val ext = it.path.substringAfterLast(".", "").lowercase()
                            ext in setOf("mp4","mkv","avi","mov","wmv","webm","flv","m4v","ts","m2ts")
                        }
                        .sortedBy { it.path }
                    android.util.Log.d("JecWeb", "prefetch seriesList=${seriesList.size}")
                } catch (e: Exception) {
                    android.util.Log.e("JecWeb", "prefetch err: ${e.message}")
                }
            }
        }

        // Resume позиции (после 1.5с — когда duration появится)
        if (hash.isNotBlank()) {
            scope.launch {
                delay(1500)
                try {
                    val log = PlayLogStore.list(context).firstOrNull { it.hash == hash }
                    if (log != null && log.position > 60000 && duration > 0 && log.position < duration * 0.95) {
                        mediaPlayer.time = log.position
                        android.util.Log.d("JecWeb", "resume from ${log.position}/${duration}")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("JecWeb", "resume err: ${e.message}")
                }
            }
        }
    }

    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            delay(4000)
            controlsVisible = false
        }
    }

    LaunchedEffect(firstFrameReady, currentUrl) {
        if (!firstFrameReady) {
            delay(90000)
            if (!firstFrameReady) {
                android.util.Log.e("JecWeb", "VLC first frame timeout 90s, back")
                onBack()
            }
        }
    }

    // При открытии любого sheet — скрываем system bars (immersive)
    LaunchedEffect(optionsSheet, audioSheet, subSheet, seriesSheet) {
        if (optionsSheet || audioSheet || subSheet || seriesSheet) {
            delay(150)
            activity?.let {
                val wic = WindowCompat.getInsetsController(it.window, it.window.decorView)
                wic.hide(WindowInsetsCompat.Type.systemBars())
                wic.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    // Сохраняем позицию каждые 20с
    LaunchedEffect(isPlaying) {
        if (isPlaying && hash.isNotBlank()) {
            while (true) {
                delay(20000)
                if (position > 60000 && duration > 0) {
                    try {
                        PlayLogStore.add(context, PlayLogEntry(
                            "phone", mainTitle, "", hash,
                            System.currentTimeMillis() / 1000, position, duration
                        ))
                        android.util.Log.d("JecWeb", "save pos $position/$duration")
                    } catch (e: Exception) { }
                }
            }
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying && !tracksLoadedOnce) {
            // ждём появления треков до 6 сек
            var waited = 0
            while (waited < 6000) {
                val a = mediaPlayer.audioTracks
                val sp = mediaPlayer.spuTracks
                if ((a?.size ?: 0) > 0 || (sp?.size ?: 0) > 0) break
                delay(300)
                waited += 300
            }
            delay(500)
            val a = mediaPlayer.audioTracks
            audioTracks = (a?.toList() ?: emptyList())
                .filter { it.id >= 0 }
                .map { it.id to (it.name ?: "Дорожка ${it.id}") }
            val sp = mediaPlayer.spuTracks
            subTracks = (sp?.toList() ?: emptyList())
                .filter { it.id >= 0 }
                .map { it.id to (it.name ?: "Субтитры ${it.id}") }

            // АВТО-ВЫБОР РУССКОЙ АУДИОДОРОЖКИ (если пользователь не выбрал вручную)
            if (!userSelectedAudio && audioTracks.isNotEmpty()) {
                val rusAudio = audioTracks.firstOrNull { (_, name) -> isRussianTrack(name) }
                if (rusAudio != null && mediaPlayer.audioTrack != rusAudio.first) {
                    mediaPlayer.audioTrack = rusAudio.first
                    android.util.Log.d("JecWeb", "auto audio: ${rusAudio.second} (id=${rusAudio.first})")
                } else if (rusAudio == null) {
                    android.util.Log.d("JecWeb", "auto audio: русской нет, оставляем id=${mediaPlayer.audioTrack}")
                }
            }

            // АВТО-ВЫБОР РУССКИХ СУБТИТРОВ (только если есть русские, не навязывать)
            if (!userSelectedSubs && subTracks.isNotEmpty() && mediaPlayer.spuTrack == -1) {
                val rusSubs = subTracks.firstOrNull { (_, name) -> isRussianTrack(name) }
                if (rusSubs != null) {
                    mediaPlayer.spuTrack = rusSubs.first
                    android.util.Log.d("JecWeb", "auto subs: ${rusSubs.second} (id=${rusSubs.first})")
                }
            }

            tracksLoadedOnce = true
        }
    }

    fun openSeriesSheet() {
        if (hash.isBlank()) {
            Toast.makeText(context, "Нет данных о сериях", Toast.LENGTH_SHORT).show()
            return
        }
        loadingSeries = true
        seriesSheet = true
        scope.launch {
            val files = TorrentRepository.getFiles(hash)
            seriesList = files.filter {
                val ext = it.path.substringAfterLast(".", "").lowercase()
                ext in setOf("mp4", "mkv", "avi", "mov", "wmv", "webm", "flv", "m4v", "ts", "m2ts")
            }
            loadingSeries = false
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { vlcView }, modifier = Modifier.fillMaxSize())

        val configuration = LocalConfiguration.current
        val density = LocalDensity.current
        val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
        val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
        val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
        val actForGest = remember(context) { context.findActivity() }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible },
                        onDoubleTap = {
                            if (isPlaying) mediaPlayer.pause() else mediaPlayer.play()
                        },
                        onLongPress = {
                            if (!uiLocked) {
                                uiLocked = true
                                controlsVisible = false
                                try {
                                    val v = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
                                    if (android.os.Build.VERSION.SDK_INT >= 26) {
                                        v.vibrate(android.os.VibrationEffect.createOneShot(30, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                                    } else {
                                        @Suppress("DEPRECATION") v.vibrate(30)
                                    }
                                } catch (_: Exception) {}
                                if (TouchBlockerService.isEnabled(context)) {
                                    TouchBlockerService.start(context)
                                    Toast.makeText(context, "🔒 Блокировка активна", Toast.LENGTH_SHORT).show()
                                    android.util.Log.d("JecWeb", "TouchBlocker started")
                                } else {
                                    Toast.makeText(context, "Разрешите Accessibility: Настройки → Специальные возможности → JecWeb Go", Toast.LENGTH_LONG).show()
                                    try {
                                        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        })
                                    } catch (_: Exception) {}
                                }
                                try {
                                    activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED
                                    android.util.Log.d("JecWeb", "orientation LOCKED")
                                } catch (_: Exception) {}
                                android.util.Log.d("JecWeb", "UI lock ON")
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            dragStartX = offset.x
                            dragStartY = offset.y
                            dragMode = 0
                            dragStartTimeMs = mediaPlayer.time
                            seekTarget = mediaPlayer.time
                            dragStartVolume = mediaPlayer.volume  // 0..200
                            dragStartBrightness = actForGest?.window?.attributes?.screenBrightness ?: -1f
                            if (dragStartBrightness < 0f) dragStartBrightness = 0.5f
                            gestureHint = ""
                            gestureVisible = true
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val totalDx = change.position.x - dragStartX
                            val totalDy = change.position.y - dragStartY
                            if (dragMode == 0) {
                                dragMode = if (abs(totalDx) > abs(totalDy)) 1
                                           else if (dragStartX < screenWidthPx / 2) 3
                                           else 2
                            }
                            when (dragMode) {
                                1 -> {
                                    val deltaMs = (totalDx / 200f * 10000f).toLong()
                                    seekTarget = (dragStartTimeMs + deltaMs).coerceIn(0, duration)
                                    gestureHint = "${formatTime(seekTarget)} / ${formatTime(duration)}"
                                }
                                2 -> {
                                    val delta = (totalDy / 600f * 200).toInt()
                                    val newVol = (dragStartVolume - delta).coerceIn(0, 200)
                                    mediaPlayer.volume = newVol
                                    gestureHint = "🔊 $newVol%"
                                }
                                3 -> {
                                    val delta = -totalDy / 800f
                                    val newBr = (dragStartBrightness + delta).coerceIn(0.05f, 1.0f)
                                    actForGest?.window?.let { w ->
                                        val lp = w.attributes
                                        lp.screenBrightness = newBr
                                        w.attributes = lp
                                    }
                                    gestureHint = "☀ ${(newBr * 100).toInt()}%"
                                }
                            }
                        },
                        onDragEnd = {
                            if (dragMode == 1 && duration > 0) {
                                mediaPlayer.time = seekTarget
                                android.util.Log.d("JecWeb", "seek -> $seekTarget")
                            }
                            dragMode = 0
                            gestureVisible = false
                        },
                        onDragCancel = {
                            dragMode = 0
                            gestureVisible = false
                        }
                    )
                }
        )

        if (gestureVisible && gestureHint.isNotBlank()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    gestureHint,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
        }

        if (controlsVisible) {
            Row(
                Modifier.fillMaxWidth().align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
                }
                Text(currentTitle, color = Color.White, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 4.dp))
                TextButton(
                    onClick = {
                        playbackRate = when (playbackRate) {
                            1.0f -> 1.25f
                            1.25f -> 1.5f
                            1.5f -> 2.0f
                            2.0f -> 0.75f
                            else -> 1.0f
                        }
                        mediaPlayer.rate = playbackRate
                        android.util.Log.d("JecWeb", "VLC rate set=${playbackRate}x get=${mediaPlayer.rate}")
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text("${playbackRate}x", color = Color.White, fontSize = 13.sp)
                }
                TextButton(
                    onClick = { sleepSheet = true },
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    val txt = if (sleepTimerEndMs > 0L) {
                        val m = (sleepTimerRemaining / 60000).toInt()
                        val s = ((sleepTimerRemaining / 1000) % 60).toInt()
                        "🌙 %d:%02d".format(m, s)
                    } else "🌙"
                    Text(txt, color = Color.White, fontSize = 13.sp)
                }
                val aspectLabelsTop = listOf("Auto", "16:9", "4:3", "1:1", "21:9")
                TextButton(
                    onClick = {
                        aspectIndex = (aspectIndex + 1) % aspectLabelsTop.size
                        val newAspect = if (aspectIndex == 0) null else aspectLabelsTop[aspectIndex]
                        try {
                            val vout = mediaPlayer.vlcVout
                            vout.setWindowSize(vlcView.width, vlcView.height)
                            mediaPlayer.aspectRatio = newAspect
                            android.util.Log.d("JecWeb", "VLC aspect=${aspectLabelsTop[aspectIndex]} set=${mediaPlayer.aspectRatio} vout=${vlcView.width}x${vlcView.height}")
                        } catch (e: Exception) {
                            android.util.Log.e("JecWeb", "aspect fail: ${e.message}")
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text(aspectLabelsTop[aspectIndex], color = Color.White, fontSize = 12.sp)
                }
            }

            IconButton(
                onClick = { if (isPlaying) mediaPlayer.pause() else mediaPlayer.play() },
                modifier = Modifier.align(Alignment.Center).size(72.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null, tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }

            Column(
                Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatTime(position), color = Color.White, fontSize = 11.sp)
                    Slider(
                        value = if (duration > 0) position.toFloat() / duration.toFloat() else 0f,
                        onValueChange = { v -> if (duration > 0) mediaPlayer.time = (v * duration).toLong() },
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        )
                    )
                    Text(formatTime(duration), color = Color.White, fontSize = 11.sp)
                }
                val curIdxInList = seriesList.indexOfFirst { it.id == currentIdx }
                val showPrev = seriesList.size > 1 && curIdxInList > 0
                val showNext = seriesList.size > 1 && curIdxInList >= 0 && curIdxInList < seriesList.size - 1
                Row(
                    Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showPrev) {
                        IconButton(onClick = { prevSeries() }) {
                            Icon(Icons.Filled.SkipPrevious, contentDescription = "Предыдущая", tint = Color.White)
                        }
                    }
                    IconButton(onClick = { mediaPlayer.time = (mediaPlayer.time - 10000).coerceAtLeast(0) }) {
                        Icon(Icons.Filled.Replay10, contentDescription = "-10с", tint = Color.White)
                    }
                    if (audioTracks.size > 1) {
                        IconButton(onClick = { audioSheet = true }) {
                            Icon(Icons.Filled.Audiotrack, contentDescription = "Аудио", tint = Color.White)
                        }
                    }
                    if (subTracks.isNotEmpty()) {
                        IconButton(onClick = { subSheet = true }) {
                            Icon(Icons.Filled.Subtitles, contentDescription = "Субтитры", tint = Color.White)
                        }
                    }
                    IconButton(onClick = { openSeriesSheet() }) {
                        Icon(Icons.Filled.VideoLibrary, contentDescription = "Серии", tint = Color.White)
                    }
                    IconButton(onClick = { mediaPlayer.time = mediaPlayer.time + 10000 }) {
                        Icon(Icons.Filled.Forward10, contentDescription = "+10с", tint = Color.White)
                    }
                    if (showNext) {
                        IconButton(onClick = { nextSeries() }) {
                            Icon(Icons.Filled.SkipNext, contentDescription = "Следующая", tint = Color.White)
                        }
                    }
                }
            }
        }

        if (uiLocked) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                lockIconPulse = !lockIconPulse
                            },
                            onLongPress = {
                                uiLocked = false
                                lockIconPulse = false
                                try {
                                    val v = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
                                    if (android.os.Build.VERSION.SDK_INT >= 26) {
                                        v.vibrate(android.os.VibrationEffect.createOneShot(30, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                                    } else {
                                        @Suppress("DEPRECATION") v.vibrate(30)
                                    }
                                } catch (_: Exception) {}
                                TouchBlockerService.stop(context)
                                try {
                                    activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                    android.util.Log.d("JecWeb", "orientation UNSPECIFIED")
                                } catch (_: Exception) {}
                                android.util.Log.d("JecWeb", "UI lock OFF")
                            }
                        )
                    }
            ) {
                // Маленький 🔒 — только при tap (1.5 сек)
                if (lockIconPulse) {
                    Text(
                        "🔒",
                        fontSize = 24.sp,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = 80.dp, start = 16.dp)
                    )
                }
                if (lockIconPulse) {
                    LaunchedEffect(lockIconPulse) {
                        delay(1500)
                        lockIconPulse = false
                    }
                }
            }
        }

        if (!firstFrameReady) {
            LoadingView(
                title = mainTitle,
                description = description,
                poster = poster,
                status = if (bufferingPct > 0) "Буферизация $bufferingPct%" else "Ожидание потока...",
                onCancel = onBack
            )
        }

        if (firstFrameReady && sleepTimerEndMs > 0L && sleepTimerRemaining in 1L..60000L) {
            val mm = (sleepTimerRemaining / 60000L).toInt()
            val ss = ((sleepTimerRemaining / 1000L) % 60L).toInt()
            Text(
                "🌙 %d:%02d".format(mm, ss),
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            )
        }
        if (firstFrameReady && sleepTimerEndMs > 0L && sleepTimerRemaining > 60000L && !controlsVisible) {
            val mm = (sleepTimerRemaining / 60000L).toInt()
            val ss = ((sleepTimerRemaining / 1000L) % 60L).toInt()
            Text(
                "🌙 %d:%02d".format(mm, ss),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 80.dp, end = 16.dp)
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }

    if (audioSheet) {
        PlayerSheet(title = "Аудиодорожка", onDismiss = { audioSheet = false }) {
            audioTracks.forEach { (id, name) ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        mediaPlayer.audioTrack = id
                        userSelectedAudio = true
                        audioSheet = false
                    }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = mediaPlayer.audioTrack == id,
                        onClick = { mediaPlayer.audioTrack = id; userSelectedAudio = true; audioSheet = false })
                    Text(name, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }

    if (subSheet) {
        PlayerSheet(title = "Субтитры", onDismiss = { subSheet = false }) {
            Row(
                Modifier.fillMaxWidth().clickable {
                    mediaPlayer.spuTrack = -1
                    userSelectedSubs = true
                    subSheet = false
                }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = mediaPlayer.spuTrack == -1,
                    onClick = { mediaPlayer.spuTrack = -1; userSelectedSubs = true; subSheet = false })
                Text("Выключены", modifier = Modifier.padding(start = 8.dp))
            }
            subTracks.forEach { (id, name) ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        mediaPlayer.spuTrack = id
                        userSelectedSubs = true
                        subSheet = false
                    }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = mediaPlayer.spuTrack == id,
                        onClick = { mediaPlayer.spuTrack = id; userSelectedSubs = true; subSheet = false })
                    Text(name, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }

    if (sleepSheet) {
        PlayerSheet(title = "🌙 Таймер сна", onDismiss = { sleepSheet = false }) {
            val canEnd = duration > position && duration > 0L
            var minutes by remember {
                mutableIntStateOf(
                    if (sleepTimerMinutes > 0) sleepTimerMinutes
                    else 30
                )
            }
            var toEnd by remember { mutableStateOf(stopAtFileEnd && canEnd) }
            fun fmtMin(m: Int): String = if (m >= 60) {
                val h = m / 60; val r = m % 60
                if (r == 0) "${h}ч" else "${h}ч ${r}м"
            } else "${m}м"
            Text(
                if (toEnd) "До конца (${formatTime(duration - position)})"
                else fmtMin(minutes),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Spacer(Modifier.height(8.dp))
            Slider(
                value = minutes.toFloat(),
                onValueChange = { minutes = it.toInt().coerceIn(1, 180); toEnd = false },
                valueRange = 1f..180f,
                enabled = !toEnd,
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(1, 30, 60, 90, 120, 180).forEach { m ->
                    Text(fmtMin(m), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (canEnd) {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().clickable { toEnd = !toEnd }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = toEnd, onCheckedChange = { toEnd = it })
                    Spacer(Modifier.width(8.dp))
                    Text("До конца файла (${formatTime(duration - position)})")
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        startSleepTimer(0, false)
                        sleepSheet = false
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Выключить") }
                Button(
                    onClick = {
                        if (toEnd) startSleepTimer(-1, true)
                        else startSleepTimer(minutes, false)
                        sleepSheet = false
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Готово") }
            }
        }
    }

    if (seriesSheet) {
        PlayerSheet(title = "Выбор серии", onDismiss = { seriesSheet = false }) {
            if (loadingSeries) {
                Box(Modifier.fillMaxWidth().height(120.dp), Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (seriesList.isEmpty()) {
                Text("Файлы не найдены", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(seriesList) { f ->
                        val isCurrent = f.id == currentIdx
                        val fName = f.path.substringAfterLast("/")
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                seriesSheet = false
                                if (!isCurrent) {
                                    currentUrl = "http://127.0.0.1:8090/stream?link=$hash&index=${f.id}&play&t=${System.currentTimeMillis()}"
                                    currentTitle = "$mainTitle · $fName"
                                    currentIdx = f.id
                                }
                            }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isCurrent) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary)
                            } else {
                                Spacer(Modifier.width(24.dp))
                            }
                            Column(Modifier.padding(start = 8.dp)) {
                                Text(fName,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal)
                                Text("${(f.length / 1024 / 1024)} MB",
                                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val dialogWindowProvider = LocalView.current.parent as? DialogWindowProvider
        DisposableEffect(Unit) {
            dialogWindowProvider?.window?.let { w ->
                val wic = WindowCompat.getInsetsController(w, w.decorView)
                wic.hide(WindowInsetsCompat.Type.systemBars())
                wic.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            onDispose { }
        }
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                Modifier.fillMaxWidth().padding(16.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* swallow, не закрывать */ },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                    Text(title, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    content()
                }
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private fun isRussianTrack(name: String): Boolean {
    val n = name.lowercase()
    return n.contains("rus") || n.contains("рус") || n.contains("russian")
        || n.contains("[ru]") || n.contains("(ru)") || n.contains("ru ")
        || n.startsWith("ru") || n.contains("ru-") || n.contains("ru_")
        || n.contains("дуб") || n.contains("mvo") || n.contains("avo")
        || n.contains("мво") || n.contains("многоголос")
}

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
