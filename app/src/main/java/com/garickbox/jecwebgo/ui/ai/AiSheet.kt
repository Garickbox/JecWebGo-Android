package com.garickbox.jecwebgo.ui.ai

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun AiSheet(
    onDismiss: () -> Unit,
    onSearch: (query: String, type: String) -> Unit,
    vm: AiViewModel = viewModel()
) {
    val s by vm.state.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ctx = LocalContext.current
    var input by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🤖 AI Помощник", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.clear() }) { Text("Очистить") }
            }

            if (s.descText.isNotEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(s.descTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(s.descText, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { onSearch(s.descTitle, "-1"); onDismiss() }) { Text("🔍 Найти") }
                            TextButton(onClick = { vm.clearDesc() }) { Text("✕") }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (s.messages.isNotEmpty()) {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 180.dp)) {
                    items(s.messages) { msg ->
                        Card(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (msg.role == "user")
                                    MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(msg.text, Modifier.padding(8.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (s.movies.isEmpty() && s.chips.isEmpty() && !s.loading) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(onClick = { vm.quickAction("random", ctx) }, label = { Text("🎲 Случайный") })
                    AssistChip(onClick = { vm.quickAction("popular", ctx) }, label = { Text("🔥 Популярное") })
                    AssistChip(onClick = { vm.quickAction("new", ctx) }, label = { Text("🆕 Новинки") })
                    AssistChip(onClick = { vm.quickAction("genre", ctx) }, label = { Text("🎭 Жанр") })
                }
                Spacer(Modifier.height(8.dp))
            }

            if (s.movies.isNotEmpty()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(s.movies) { movie ->
                        Card(
                            Modifier.fillMaxWidth().combinedClickable(
                                onClick = { onSearch(movie.title, "-1"); onDismiss() },
                                onLongClick = { vm.describe(movie.title) }
                            )
                        ) {
                            Column(Modifier.padding(8.dp)) {
                                Text(movie.title, style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2, fontWeight = FontWeight.SemiBold)
                                if (movie.rating.isNotBlank())
                                    Text("★ ${movie.rating}", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (s.chips.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    s.chips.forEach { chip ->
                        AssistChip(
                            onClick = { vm.recommendFresh(ctx, null, AiViewModel.tmdbGenreId(chip)) },
                            label = { Text(chip) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (s.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Введите запрос...") },
                    singleLine = true
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { if (input.isNotBlank()) { vm.send(input, ctx); input = "" } }) {
                    Text("▶")
                }
            }
        }
    }
}
