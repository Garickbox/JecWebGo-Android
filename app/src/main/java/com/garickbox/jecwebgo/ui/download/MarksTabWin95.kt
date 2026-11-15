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
import com.garickbox.jecwebgo.data.store.Bookmark
import com.garickbox.jecwebgo.data.store.BookmarkStore
import com.garickbox.jecwebgo.ui.common.DownloadFromMagnetFlow
import com.garickbox.jecwebgo.ui.common.PlayActions
import com.garickbox.jecwebgo.ui.common.win95.win95Bevel
import com.garickbox.jecwebgo.ui.theme.Win95Colors
import kotlinx.coroutines.launch

@Composable
fun MarksTabWin95() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<Bookmark>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var downloadMagnet by remember { mutableStateOf<String?>(null) }
    var downloadTitle by remember { mutableStateOf("") }

    suspend fun reload() {
        loading = true
        items = BookmarkStore.list(ctx)
        loading = false
    }
    LaunchedEffect(Unit) { reload() }

    downloadMagnet?.let { m ->
        DownloadFromMagnetFlow(m, downloadTitle, onDismiss = { downloadMagnet = null })
    }

    if (loading) return

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
                "🔖 Метки (${items.size})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Win95Colors.Text
            )
            Spacer(Modifier.weight(1f))
            if (items.isNotEmpty()) {
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
                            scope.launch { BookmarkStore.clear(ctx); reload() }
                        }
                        .padding(4.dp)
                )
            }
        }

        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize())
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(items, key = { idx, b -> "bm_${b.added}_${b.title.hashCode()}_$idx" }) { idx, b ->
                    MarksRowWin95(
                        b = b,
                        onPlay = {
                            if (b.magnet.isBlank()) {
                                ctx.toast("Торрент мёртв, удалён из меток")
                                scope.launch {
                                    BookmarkStore.removeAt(ctx, idx)
                                    reload()
                                }
                            } else {
                                PlayActions.playPhone(ctx, scope, b.magnet, b.title)
                            }
                        },
                        onDownload = {
                            downloadMagnet = b.magnet
                            downloadTitle = b.title
                        },
                        onRemove = {
                            scope.launch {
                                BookmarkStore.removeAt(ctx, idx)
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
private fun MarksRowWin95(
    b: Bookmark,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
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
            Text("🔖", fontSize = 11.sp)
            Spacer(Modifier.width(4.dp))
            Text(
                b.title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Win95Colors.Text,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            MarksSmallBtn("▶", onPlay)
            MarksSmallBtn("⬇", onDownload)
            MarksSmallBtn("✕", onRemove)
        }
    }
}

@Composable
private fun MarksSmallBtn(label: String, onClick: () -> Unit) {
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
