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
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import com.garickbox.jecwebgo.domain.TorrentRepository
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
    val onPlayPhone: (SearchItem) -> Unit = { it ->
        scope.launch {
            val h = TorrentRepository.add(it.magnet)
            if (h == null) Toast.makeText(ctx, "TorrServer недоступен", Toast.LENGTH_SHORT).show()
            else runCatching {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TorrentRepository.streamM3U(h))).setType("video/*"))
            }.onFailure { Toast.makeText(ctx, "Нет видеоплеера", Toast.LENGTH_SHORT).show() }
        }
    }
    val onPlayTV: (SearchItem) -> Unit = { it ->
        scope.launch {
            val h = TorrentRepository.add(it.magnet)
            if (h == null) Toast.makeText(ctx, "TorrServer недоступен", Toast.LENGTH_SHORT).show()
            else {
                val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("m3u", TorrentRepository.streamM3U(h)))
                Toast.makeText(ctx, "M3U скопирован для ТВ", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val onBookmark: (SearchItem) -> Unit = { Toast.makeText(ctx, "Закладка: ${it.title}", Toast.LENGTH_SHORT).show() }
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
