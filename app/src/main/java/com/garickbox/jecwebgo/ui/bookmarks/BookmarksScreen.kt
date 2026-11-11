package com.garickbox.jecwebgo.ui.bookmarks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garickbox.jecwebgo.data.store.Bookmark
import com.garickbox.jecwebgo.data.store.BookmarkStore
import com.garickbox.jecwebgo.ui.common.PlayActions

@Composable
fun BookmarksScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<Bookmark>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    suspend fun reload() {
        loading = true
        items = BookmarkStore.list(ctx)
        loading = false
    }
    LaunchedEffect(Unit) { reload() }

    if (loading) { Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }; return }
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Нет закладок", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        return
    }

    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(items) { idx, b ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(b.title, fontWeight = FontWeight.SemiBold, maxLines = 3)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(onClick = {
                            PlayActions.playPhone(ctx, scope, b.magnet, b.title)
                        }, modifier = Modifier.weight(1f).height(40.dp), contentPadding = PaddingValues(0.dp)) { Text("▶", fontSize = 16.sp) }
                        Button(onClick = {
                            PlayActions.playTV(ctx, scope, b.magnet, b.title)
                        }, modifier = Modifier.weight(1f).height(40.dp), contentPadding = PaddingValues(0.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)) { Text("📺", fontSize = 16.sp) }
                        OutlinedButton(onClick = {
                            scope.launch {
                                BookmarkStore.removeAt(ctx, idx)
                                reload()
                            }
                        }, modifier = Modifier.weight(1f).height(40.dp), contentPadding = PaddingValues(0.dp)) { Text("✕", fontSize = 16.sp) }
                    }
                }
            }
        }
    }
}
