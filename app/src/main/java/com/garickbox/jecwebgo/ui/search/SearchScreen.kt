package com.garickbox.jecwebgo.ui.search
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import android.widget.Toast
import androidx.compose.runtime.*
import android.util.Log
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import com.garickbox.jecwebgo.domain.TorrentRepository
import com.garickbox.jecwebgo.ui.common.PlayActions
import com.garickbox.jecwebgo.data.store.BookmarkStore
import com.garickbox.jecwebgo.ui.ai.AiSheet
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.garickbox.jecwebgo.domain.model.SearchItem
@Composable
fun SearchScreen(vm: SearchViewModel = viewModel()) {
    val s by vm.state.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var showAi by remember { mutableStateOf(false) }
    val onPlayPhone: (SearchItem) -> Unit = { item ->
        PlayActions.playPhone(ctx, scope, item.magnet, item.title)
    }
    val onPlayTV: (SearchItem) -> Unit = { item ->
        PlayActions.playTV(ctx, scope, item.magnet, item.title)
    }
    val onBookmark: (SearchItem) -> Unit = { item ->
        scope.launch {
            BookmarkStore.add(ctx, item)
            Toast.makeText(ctx, "В закладки: ${item.title}", Toast.LENGTH_SHORT).show()
        }
    }
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        OutlinedTextField(
            value = s.query,
            onValueChange = vm::onQuery,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Поиск фильмов и сериалов…") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.search() }),
            trailingIcon = {
                IconButton(onClick = vm::search) { Icon(Icons.Filled.Search, "search") }
            },
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(Modifier.height(8.dp))
        when {
            s.loading -> Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator() }
            s.error != null -> Text("Ошибка: ${s.error}", modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error)
            s.items.isEmpty() && s.query.isNotBlank() -> Text("Ничего не найдено", Modifier.padding(12.dp))
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(s.items) { item -> SearchCard(item, onPlayPhone, onPlayTV, onBookmark) }
            }
        }
    }
        FloatingActionButton(
            onClick = { showAi = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) { Text("🤖", fontSize = 20.sp) }
        if (showAi) AiSheet(
            onDismiss = { showAi = false },
            onSearch = { q, _ -> vm.onQuery(q); vm.search(); showAi = false }
        )
    }
}
@Composable
fun SearchCard(item: SearchItem, onPlayPhone: (SearchItem) -> Unit, onPlayTV: (SearchItem) -> Unit, onBookmark: (SearchItem) -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 2)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.relased.isNotBlank()) Text("📅 ${item.relased}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.quality > 0) Text("${item.quality}p", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                if (item.videotype.isNotBlank()) Text(item.videotype.uppercase(), fontSize = 12.sp)
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("📦 ${item.sizeName}", fontSize = 12.sp)
                Text("🔼 ${item.sid}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                Text("🔽 ${item.pir}", fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = { onPlayPhone(item) }, modifier = Modifier.weight(1f).height(40.dp), contentPadding = PaddingValues(0.dp)) { Text("▶", fontSize = 16.sp, maxLines = 1) }
                Button(onClick = { onPlayTV(item) }, modifier = Modifier.weight(1f).height(40.dp), contentPadding = PaddingValues(0.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)) { Text("📺", fontSize = 16.sp, maxLines = 1) }
                OutlinedButton(onClick = { onBookmark(item) }, modifier = Modifier.weight(1f).height(40.dp), contentPadding = PaddingValues(0.dp)) { Text("🔖", fontSize = 16.sp, maxLines = 1) }
            }
        }
    }
}
