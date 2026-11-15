package com.garickbox.jecwebgo.ui.download

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garickbox.jecwebgo.data.store.BookmarkStore
import com.garickbox.jecwebgo.data.store.PlayLogEntry
import com.garickbox.jecwebgo.data.store.PlayLogStore
import com.garickbox.jecwebgo.ui.common.DownloadFromMagnetFlow
import com.garickbox.jecwebgo.ui.common.PlayActions
import com.garickbox.jecwebgo.ui.common.win95.win95Bevel
import com.garickbox.jecwebgo.ui.theme.Win95Colors
import com.garickbox.jecwebgo.util.toast
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryTabWin95() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var playlog by remember { mutableStateOf<List<PlayLogEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var downloadMagnet by remember { mutableStateOf<String?>(null) }
    var downloadTitle by remember { mutableStateOf("") }

    suspend fun reload() {
        loading = true
        playlog = PlayLogStore.list(ctx).reversed()
        loading = false
    }
    LaunchedEffect(Unit) { reload() }

    downloadMagnet?.let { m ->
        DownloadFromMagnetFlow(m, downloadTitle, onDismiss = { downloadMagnet = null })
    }

    if (loading) return

    val fmt = remember { SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()) }

    Column(Modifier.fillMaxSize().background(Win95Colors.Face)) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Win95Colors.Face)
                .win95Bevel(raised = false, thickness = 1.dp)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "🕐 История просмотров (${playlog.size})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Win95Colors.Text
            )
            Spacer(Modifier.weight(1f))
            if (playlog.isNotEmpty()) {
                Text(
                    "Очистить",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Win95Colors.Navy,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            scope.launch { PlayLogStore.clear(ctx); reload() }
                        }
                        .padding(4.dp)
                )
            }
        }

        if (playlog.isEmpty()) {
            Box(Modifier.fillMaxSize())
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(playlog, key = { idx, p -> "pl_${p.time}_${p.title.hashCode()}_$idx" }) { idx, p ->
                    HistoryRowWin95(
                        entry = p,
                        dateStr = fmt.format(Date(p.time * 1000)),
                        onPlay = {
                            val effectiveMagnet = p.magnet.ifBlank {
                                if (p.hash.isNotBlank()) "magnet:?xt=urn:btih:${p.hash}" else ""
                            }
                            if (effectiveMagnet.isBlank()) {
                                ctx.toast("Торрент мёртв, удалён из истории")
                                scope.launch {
                                    PlayLogStore.removeAt(ctx, playlog.size - 1 - idx)
                                    reload()
                                }
                            } else {
                                PlayActions.playPhone(ctx, scope, effectiveMagnet, p.title)
                            }
                        },
                        onDownload = {
                            downloadMagnet = p.magnet
                            downloadTitle = p.title
                        },
                        onBookmark = {
                            scope.launch {
                                BookmarkStore.addRaw(ctx, p.title, p.magnet)
                                ctx.toast("В закладки: ${p.title}")
                            }
                        },
                        onRemove = {
                            scope.launch {
                                PlayLogStore.removeAt(ctx, playlog.size - 1 - idx)
                                reload()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryRowWin95(
    entry: PlayLogEntry,
    dateStr: String,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onBookmark: () -> Unit,
    onRemove: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Win95Colors.Face)
            .win95Bevel(raised = true, thickness = 2.dp)
            .padding(5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("▶", fontSize = 11.sp, color = Win95Colors.Navy)
            Spacer(Modifier.width(4.dp))
            Text(
                entry.title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Win95Colors.Text,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            dateStr,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            color = Win95Colors.TextDim
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Win95SmallBtn("▶", onPlay)
            Win95SmallBtn("⬇", onDownload)
            Win95SmallBtn("🔖", onBookmark)
            Win95SmallBtn("✕", onRemove)
        }
    }
}

@Composable
private fun Win95SmallBtn(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .background(Win95Colors.Face)
            .win95Bevel(raised = true, thickness = 1.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = Win95Colors.Text)
    }
}
