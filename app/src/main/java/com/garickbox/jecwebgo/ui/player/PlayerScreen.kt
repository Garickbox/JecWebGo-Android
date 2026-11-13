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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

@Composable
fun PlayerScreen(
    url: String,
    title: String,
    hash: String = "",
    currentIndex: Int = 1,
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
    hwMode: String,
    onBack: () -> Unit,
    onError: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val useTextureView = hwMode == "auto"

    val activity = remember(context) { context.findActivity() }
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
            "--network-caching=10000",
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
                MediaPlayer.Event.Playing -> isPlaying = true
                MediaPlayer.Event.Paused -> isPlaying = false
                MediaPlayer.Event.Stopped -> isPlaying = false
                MediaPlayer.Event.EndReached -> onBack()
                MediaPlayer.Event.TimeChanged -> position = event.timeChanged
                MediaPlayer.Event.LengthChanged -> duration = event.lengthChanged
                MediaPlayer.Event.EncounteredError -> onError()
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

    LaunchedEffect(currentUrl, reloadKey) {
        runCatching { mediaPlayer.stop() }
        isPlaying = false
        position = 0L
        duration = 0L
        val media = Media(libVLC, Uri.parse(currentUrl)).apply {
            when (hwMode) {
                "full" -> setHWDecoderEnabled(true, true)
                "auto" -> setHWDecoderEnabled(true, false)
                "sw" -> setHWDecoderEnabled(false, false)
            }
            val isAvi = currentUrl.contains(".avi", ignoreCase = true)
            if (isAvi) {
                addOption(":network-caching=20000")
                addOption(":demux=avformat")
            } else {
                addOption(":network-caching=10000")
            }
            addOption(":clock-jitter=0")
            addOption(":clock-synchro=0")
        }
        mediaPlayer.media = media
        mediaPlayer.play()
        media.release()
    }

    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            delay(4000)
            controlsVisible = false
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            delay(600)
            val a = mediaPlayer.audioTracks
            audioTracks = (a?.toList() ?: emptyList())
                .filter { it.id >= 0 }
                .map { it.id to (it.name ?: "Дорожка ${it.id}") }
            val sp = mediaPlayer.spuTracks
            subTracks = (sp?.toList() ?: emptyList())
                .filter { it.id >= 0 }
                .map { it.id to (it.name ?: "Субтитры ${it.id}") }
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

        Box(
            Modifier.fillMaxSize().clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { controlsVisible = !controlsVisible }
        )

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
                Row(
                    Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { mediaPlayer.time = (mediaPlayer.time - 10000).coerceAtLeast(0) }) {
                        Icon(Icons.Filled.Replay10, contentDescription = "-10с", tint = Color.White)
                    }
                    IconButton(onClick = { if (audioTracks.isNotEmpty()) audioSheet = true else Toast.makeText(context, "Нет аудиодорожек", Toast.LENGTH_SHORT).show() }) {
                        Icon(Icons.Filled.Audiotrack, contentDescription = "Аудио", tint = Color.White)
                    }
                    IconButton(onClick = { subSheet = true }) {
                        Icon(Icons.Filled.Subtitles, contentDescription = "Субтитры", tint = Color.White)
                    }
                    IconButton(onClick = { openSeriesSheet() }) {
                        Icon(Icons.Filled.VideoLibrary, contentDescription = "Серии", tint = Color.White)
                    }
                    IconButton(onClick = { mediaPlayer.time = mediaPlayer.time + 10000 }) {
                        Icon(Icons.Filled.Forward10, contentDescription = "+10с", tint = Color.White)
                    }
                }
            }
        }
    }

    if (audioSheet) {
        ModalBottomSheet(onDismissRequest = { audioSheet = false }) {
            Column(Modifier.padding(16.dp)) {
                Text("Аудиодорожка", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                audioTracks.forEach { (id, name) ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            mediaPlayer.audioTrack = id
                            audioSheet = false
                        }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = mediaPlayer.audioTrack == id,
                            onClick = { mediaPlayer.audioTrack = id; audioSheet = false })
                        Text(name, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }

    if (subSheet) {
        ModalBottomSheet(onDismissRequest = { subSheet = false }) {
            Column(Modifier.padding(16.dp)) {
                Text("Субтитры", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().clickable {
                        mediaPlayer.spuTrack = -1
                        subSheet = false
                    }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = mediaPlayer.spuTrack == -1,
                        onClick = { mediaPlayer.spuTrack = -1; subSheet = false })
                    Text("Выключены", modifier = Modifier.padding(start = 8.dp))
                }
                subTracks.forEach { (id, name) ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            mediaPlayer.spuTrack = id
                            subSheet = false
                        }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = mediaPlayer.spuTrack == id,
                            onClick = { mediaPlayer.spuTrack = id; subSheet = false })
                        Text(name, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }

    if (seriesSheet) {
        ModalBottomSheet(onDismissRequest = { seriesSheet = false }) {
            Column(Modifier.padding(16.dp)) {
                Text("Выбор серии", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
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
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
