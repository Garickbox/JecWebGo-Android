package com.garickbox.jecwebgo.ui.download

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.garickbox.jecwebgo.ui.common.win95.Win95Button
import com.garickbox.jecwebgo.ui.common.win95.Win95TabsStrip
import com.garickbox.jecwebgo.ui.common.win95.Win95SubTab
import com.garickbox.jecwebgo.ui.common.win95.Win95Progress
import com.garickbox.jecwebgo.ui.common.win95.Win95TitleBar
import com.garickbox.jecwebgo.ui.common.win95.win95Bevel
import com.garickbox.jecwebgo.ui.common.win95.win95Border
import com.garickbox.jecwebgo.ui.theme.Win95Colors
import androidx.compose.ui.draw.alpha
import com.garickbox.jecwebgo.ui.common.win95.Win95Checkbox
import com.garickbox.jecwebgo.data.download.DownloadItem
import androidx.compose.foundation.lazy.itemsIndexed
import com.garickbox.jecwebgo.data.download.DownloadedItem
import com.garickbox.jecwebgo.ui.common.win95.Win95Text
import androidx.compose.foundation.verticalScroll

@Composable
fun DownloadScreenWin95(
    vm: DownloadViewModel = viewModel(),
    onNavigate: (String) -> Unit = {},
    bottomBarHeight: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp(0f)
) {
    val state by vm.state.collectAsState()
    val ctx = LocalContext.current
    var helpDialog by remember { mutableStateOf(false) }
    var currentTab by remember { mutableStateOf("downloads") }

    Column(
        Modifier
            .fillMaxSize()
            .background(Win95Colors.Teal)
            .navigationBarsPadding()
            .padding(4.dp)
            .padding(bottom = bottomBarHeight)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Win95Colors.Face)
                .win95Bevel(raised = true, thickness = 2.dp)
                .padding(2.dp)
        ) {
            // ═══ TITLE BAR ═══
            val activePct = state.active?.let { (it.progress * 100).toInt() }
            Win95TitleBar(
                title = if (activePct != null) "Загрузки [$activePct%] — JecWeb" else "Загрузки — JecWeb",
                icon = "📥"
            )

            // ═══ MENU BAR ═══
            Win95MenuBar(
                onOpenFolder = {
                    val authority = "com.android.externalstorage.documents"
                    val docId = "primary:Download/JecWeb"
                    var launched = false
                    if (!launched) {
                        try {
                            val uri = android.provider.DocumentsContract.buildDocumentUri(authority, docId)
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, android.provider.DocumentsContract.Document.MIME_TYPE_DIR)
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            ctx.startActivity(intent)
                            launched = true
                        } catch (_: Exception) {}
                    }
                    if (!launched) {
                        try {
                            val uri = android.net.Uri.parse("content://" + authority + "/root/primary")
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, android.provider.DocumentsContract.Document.MIME_TYPE_DIR)
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            ctx.startActivity(intent)
                            launched = true
                        } catch (_: Exception) {}
                    }
                    if (!launched) {
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                setDataAndType(
                                    android.net.Uri.parse("file:///storage/emulated/0/Download/JecWeb"),
                                    "resource/folder"
                                )
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            ctx.startActivity(intent)
                            launched = true
                        } catch (_: Exception) {}
                    }
                    if (!launched) {
                        android.widget.Toast.makeText(
                            ctx, "Папка: /storage/emulated/0/Download/JecWeb", android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                },
                onSettings = { onNavigate("settings") },
                onRefresh = { vm.onSearch(state.search) },
                onPauseAll = { vm.pauseAll() },
                onResumeAll = { vm.resumeAll() },
                onSelectAll = { vm.selectAll() },
                onClearSelection = { vm.clearSelection() },
                onClearCompleted = { vm.clearHistory(ctx) },
                onExpandAll = { vm.expandAllFolders() },
                onCollapseAll = { vm.collapseAllFolders() },
                onSort = { vm.onSort(it) },
                onAbout = { onNavigate("about") },
                onHelp = { helpDialog = true }
            )

            // ═══ SUB-TABS: Загрузки / История / Метки ═══
            Win95TabsStrip(
                tabs = listOf(
                    Win95SubTab("downloads", "Загрузки"),
                    Win95SubTab("history", "История"),
                    Win95SubTab("marks", "Метки")
                ),
                selectedId = currentTab,
                onSelect = { currentTab = it }
            )

            // ═══ TOOLBAR (только на downloads) ═══
            if (currentTab == "downloads") {
                Win95Toolbar(
                    search = state.search,
                    onSearchChange = { vm.onSearch(it) },
                    filter = state.filter,
                    onFilterChange = { vm.onFilter(it) }
                )
            }

            // ═══ SELECTION PANEL (условно) ═══
            if (state.selection.isNotEmpty()) {
                Win95SelectionPanel(
                    count = state.selection.size,
                    onPause = { vm.pauseSelected() },
                    onResume = { vm.resumeSelected() },
                    onRemove = { vm.removeSelected(ctx) },
                    onClear = { vm.clearSelection() }
                )
            }

            // ═══ CONTENT (Блок 4) ═══
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (currentTab) {
                    "downloads" -> DownloadContentWin95(state, vm, ctx)
                    "history" -> HistoryTabWin95()
                    "marks" -> MarksTabWin95()
                    else -> DownloadContentWin95(state, vm, ctx)
                }
            }

            // ═══ STATUS BAR ═══
            Win95StatusBar(
                left = "C:\\Downloads\\JecWeb\\",
                right = "${state.queueGroups.size + (if (state.active != null) 1 else 0)} в очереди · ${state.history.size} готово"
            )
        }
    }

    // Dialogs (Блок 5) - TODO
    if (state.deleteDialogFor != null) {
        DeleteHistoryDialogWin95(
            item = state.deleteDialogFor!!,
            onCancel = { vm.hideDeleteDialog() },
            onRemoveFromList = { vm.removeFromList(ctx, state.deleteDialogFor!!) },
            onDeleteFile = { vm.deleteHistoryAndFile(ctx, state.deleteDialogFor!!) }
        )
    }
    if (state.propsFor != null) {
        PropertiesDialogWin95(
            target = state.propsFor!!,
            state = state,
            onClose = { vm.hideProps() }
        )
    }

    if (helpDialog) {
        HelpDialogWin95(onClose = { helpDialog = false })
    }
}

@Composable
private fun HelpDialogWin95(onClose: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier
                .width(340.dp)
                .background(Win95Colors.Face)
                .win95Bevel(raised = true, thickness = 2.dp)
                .padding(2.dp)
        ) {
            Win95TitleBar(title = "Справка — JecWeb Загрузки", icon = "❓")
            Spacer(Modifier.height(10.dp))
            Column(Modifier.padding(horizontal = 12.dp)) {
                Win95Text("Файл → Открыть папку", bold = true, fontSize = 11)
                Win95Text("Переход в Downloads/JecWeb", fontSize = 10, color = Win95Colors.TextDim)
                Spacer(Modifier.height(8.dp))
                Win95Text("Правка → Пауза / Возобновить", bold = true, fontSize = 11)
                Win95Text("Управление очередью загрузок", fontSize = 10, color = Win95Colors.TextDim)
                Spacer(Modifier.height(8.dp))
                Win95Text("Вид → Сортировка", bold = true, fontSize = 11)
                Win95Text("По имени / размеру / умолчанию", fontSize = 10, color = Win95Colors.TextDim)
                Spacer(Modifier.height(8.dp))
                Win95Text("Выбор файлов", bold = true, fontSize = 11)
                Win95Text("Галочка слева — выбрать. Панель сверху — действия", fontSize = 10, color = Win95Colors.TextDim)
                Spacer(Modifier.height(8.dp))
                Win95Text("Долгое нажатие", bold = true, fontSize = 11)
                Win95Text("Контекстное меню на папке (в CP-C7c)", fontSize = 10, color = Win95Colors.TextDim)
            }
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Win95Button(onClick = onClose, modifier = Modifier.width(90.dp).height(24.dp)) {
                    Text("OK", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// MENU BAR (Файл / Правка / Вид / Справка)
// ═══════════════════════════════════════════════════════════

@Composable
private fun Win95MenuBar(
    onOpenFolder: () -> Unit,
    onSettings: () -> Unit,
    onRefresh: () -> Unit,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onClearCompleted: () -> Unit,
    onExpandAll: () -> Unit,
    onCollapseAll: () -> Unit,
    onSort: (DownloadUiState.Sort) -> Unit,
    onAbout: () -> Unit,
    onHelp: () -> Unit
) {
    var openMenu by remember { mutableStateOf<String?>(null) }
    var menuX by remember { mutableIntStateOf(0) }

    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Win95Colors.Face)
                .win95Border(raised = true)
                .padding(horizontal = 2.dp)
        ) {
            MenuItem("Файл", openMenu == "file") { menuX = 4; openMenu = "file" }
            MenuItem("Правка", openMenu == "edit") { menuX = 56; openMenu = "edit" }
            MenuItem("Вид", openMenu == "view") { menuX = 116; openMenu = "view" }
            MenuItem("Справка", openMenu == "help") { menuX = 164; openMenu = "help" }
        }

        if (openMenu != null) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(menuX, 24),
                onDismissRequest = { openMenu = null },
                properties = PopupProperties(focusable = true)
            ) {
                Win95DropdownMenu {
                    when (openMenu) {
                        "file" -> {
                            DropdownOption("📂 Открыть папку", "Ctrl+O") { openMenu = null; onOpenFolder() }
                            DropdownOption("⚙ Настройки", "Ctrl+,") { openMenu = null; onSettings() }
                            DropdownSep()
                            DropdownOption("🔄 Обновить список", "F5") { openMenu = null; onRefresh() }
                        }
                        "edit" -> {
                            DropdownOption("⏸ Пауза всех", "") { openMenu = null; onPauseAll() }
                            DropdownOption("▶ Возобновить всех", "") { openMenu = null; onResumeAll() }
                            DropdownSep()
                            DropdownOption("☑ Выбрать всё", "Ctrl+A") { openMenu = null; onSelectAll() }
                            DropdownOption("✕ Снять выбор", "") { openMenu = null; onClearSelection() }
                            DropdownSep()
                            DropdownOption("🗑 Очистить завершённые", "") { openMenu = null; onClearCompleted() }
                        }
                        "view" -> {
                            DropdownOption("📂 Развернуть все папки", "") { openMenu = null; onExpandAll() }
                            DropdownOption("📁 Свернуть все папки", "") { openMenu = null; onCollapseAll() }
                            DropdownSep()
                            DropdownOption("🔤 Сортировать по имени", "") { openMenu = null; onSort(DownloadUiState.Sort.NAME) }
                            DropdownOption("📏 По размеру", "") { openMenu = null; onSort(DownloadUiState.Sort.SIZE) }
                            DropdownOption("📅 По дате", "") { openMenu = null; onSort(DownloadUiState.Sort.DATE) }
                            DropdownOption("↺ По умолчанию", "") { openMenu = null; onSort(DownloadUiState.Sort.DEFAULT) }
                        }
                        "help" -> {
                            DropdownOption("ℹ О программе JecWeb", "") { openMenu = null; onAbout() }
                            DropdownOption("❓ Справка", "") { openMenu = null; onHelp() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuItem(label: String, isOpen: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .background(if (isOpen) Win95Colors.Navy else Win95Colors.Face)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            label,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = if (isOpen) Win95Colors.TextLight else Win95Colors.Text
        )
    }
}

@Composable
private fun Win95DropdownMenu(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .widthIn(min = 200.dp)
            .background(Win95Colors.Face)
            .win95Bevel(raised = true, thickness = 2.dp)
            .padding(2.dp),
        content = content
    )
}

@Composable
private fun DropdownOption(label: String, shortcut: String, onClick: () -> Unit) {
    var hovered by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (hovered) Win95Colors.Navy else Win95Colors.Face)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = if (hovered) Win95Colors.TextLight else Win95Colors.Text,
            modifier = Modifier.weight(1f)
        )
        if (shortcut.isNotBlank()) {
            Text(
                shortcut,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = if (hovered) Win95Colors.TextLight else Win95Colors.TextDim
            )
        }
    }
}

@Composable
private fun DropdownSep() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp, horizontal = 3.dp)
            .height(2.dp)
            .background(Win95Colors.Gray)
    )
}

// ═══════════════════════════════════════════════════════════
// TOOLBAR (search + filter)
// ═══════════════════════════════════════════════════════════

@Composable
private fun Win95Toolbar(
    search: String,
    onSearchChange: (String) -> Unit,
    filter: DownloadUiState.Filter,
    onFilterChange: (DownloadUiState.Filter) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Win95Colors.Face)
            .win95Border(raised = true)
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Search input
        Box(
            Modifier
                .weight(1f)
                .height(22.dp)
                .background(Win95Colors.White)
                .win95Bevel(raised = false, thickness = 2.dp)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (search.isEmpty()) {
                Text(
                    "Поиск...",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Win95Colors.TextDisabled
                )
            }
            BasicTextField(
                value = search,
                onValueChange = onSearchChange,
                textStyle = TextStyle(
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Win95Colors.Text
                ),
                singleLine = true,
                cursorBrush = SolidColor(Win95Colors.Text),
                modifier = Modifier.fillMaxWidth()
            )
        }
        // Filter dropdown
        FilterDropdownWin95(filter, onFilterChange)
    }
}

@Composable
private fun FilterDropdownWin95(current: DownloadUiState.Filter, onSelect: (DownloadUiState.Filter) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = when (current) {
        DownloadUiState.Filter.ALL -> "Всё"
        DownloadUiState.Filter.ACTIVE -> "Активные"
        DownloadUiState.Filter.QUEUE -> "Очередь"
        DownloadUiState.Filter.PAUSED -> "Отложенные"
        DownloadUiState.Filter.DONE -> "Скачано"
    }
    Box {
        Box(
            Modifier
                .height(22.dp)
                .background(Win95Colors.Face)
                .win95Bevel(raised = true, thickness = 2.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { open = !open }
                )
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.width(4.dp))
                Text("▼", fontSize = 8.sp, fontFamily = FontFamily.Monospace)
            }
        }
        if (open) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, 24),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true)
            ) {
                Win95DropdownMenu {
                    listOf(
                        "Всё" to DownloadUiState.Filter.ALL,
                        "Активные" to DownloadUiState.Filter.ACTIVE,
                        "Очередь" to DownloadUiState.Filter.QUEUE,
                        "Отложенные" to DownloadUiState.Filter.PAUSED,
                        "Скачано" to DownloadUiState.Filter.DONE
                    ).forEach { (lbl, f) ->
                        DropdownOption(
                            (if (current == f) "✓ " else "  ") + lbl, ""
                        ) { open = false; onSelect(f) }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// SELECTION PANEL
// ═══════════════════════════════════════════════════════════

@Composable
private fun Win95SelectionPanel(
    count: Int,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Win95Colors.Navy)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "$count",
            fontSize = 11.sp, fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace, color = Win95Colors.TextLight
        )
        Text("выбрано", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Win95Colors.TextLight)
        Spacer(Modifier.weight(1f))
        Win95SmallBtn("⏸", onPause)
        Win95SmallBtn("▶", onResume)
        Win95SmallBtn("✕", onRemove)
        Win95SmallBtn("Снять", onClear)
    }
}

@Composable
private fun Win95SmallBtn(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .height(18.dp)
            .background(Win95Colors.Face)
            .win95Bevel(raised = true, thickness = 1.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Win95Colors.Text)
    }
}

// ═══════════════════════════════════════════════════════════
// STATUS BAR
// ═══════════════════════════════════════════════════════════

@Composable
private fun Win95StatusBar(left: String, right: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Win95Colors.Face)
            .win95Border(raised = true)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        StatusSegment(left, Modifier.weight(2f))
        StatusSegment(right, Modifier.weight(1f))
    }
}

@Composable
private fun StatusSegment(text: String, modifier: Modifier) {
    Box(
        modifier
            .height(18.dp)
            .background(Win95Colors.Face)
            .win95Bevel(raised = false, thickness = 1.dp)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(text, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Win95Colors.Text, maxLines = 1)
    }
}

// ═══════════════════════════════════════════════════════════
// ЗАГЛУШКИ (Блок 4, 5)
// ═══════════════════════════════════════════════════════════

@Composable
private fun DownloadContentWin95(
    state: DownloadUiState,
    vm: DownloadViewModel,
    ctx: android.content.Context
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    androidx.compose.foundation.lazy.LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(Win95Colors.Face).padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // ═══ СЕКЦИЯ: Сейчас качается ═══
        if (state.showActiveSection) {
            item(key = "hdr_active") {
                SectionHeaderWin95(
                    title = "Сейчас качается",
                    count = if (state.active != null) "1" else "0",
                    extra = state.active?.let {
                        if (it.status == DownloadItem.Status.PAUSED) "⏸"
                        else com.garickbox.jecwebgo.util.Helpers.formatSpeed(it.speedBps)
                    } ?: "",
                    collapsed = "active" in state.collapsedSections,
                    onClick = { vm.toggleSection("active") }
                )
            }
            if ("active" !in state.collapsedSections) {
                item(key = "active_card") {
                    val a = state.active
                    if (a != null) {
                        ActiveCardWin95(
                            item = a,
                            onPause = { vm.pauseActive() },
                            onResume = { vm.resumeActive() },
                            onStop = { vm.stopActive() },
                            onWatch = { vm.watchActive(ctx) },
                            onProps = { vm.showProps(DownloadUiState.PropsTarget("active", a.id, a.request.title)) }
                        )
                    }
                }
            }
        }

        // ═══ СЕКЦИЯ: В очереди ═══
        val groups = state.queueGroups
        val activeGroups = groups.filter { !it.isPaused }
        val pausedGroups = groups.filter { it.isPaused }

        if (activeGroups.isNotEmpty()) {
            item(key = "hdr_queue") {
                SectionHeaderWin95(
                    title = "В очереди",
                    count = "(${activeGroups.size} ${if (activeGroups.size == 1) "группа" else "групп"})",
                    extra = "",
                    collapsed = "queue" in state.collapsedSections,
                    onClick = { vm.toggleSection("queue") }
                )
            }
            if ("queue" !in state.collapsedSections) {
                itemsIndexed(activeGroups, key = { idx, g -> "q_${g.key}_$idx" }) { idx, group ->
                    FolderCardWin95(
                        group = group,
                        isExpanded = group.key in state.expandedFolders,
                        isSelected = group.key in state.selection,
                        canMoveUp = idx > 0,
                        canMoveDown = idx < activeGroups.size - 1,
                        onToggleExpand = { vm.toggleFolder(group.key) },
                        onToggleSelect = { vm.toggleSelection(group.key) },
                        onMoveUp = { vm.moveGroupUp(group.key) },
                        onMoveDown = { vm.moveGroupDown(group.key) },
                        onPause = { vm.pauseGroup(group.key) },
                        onResume = { vm.resumeGroup(group.key) },
                        onRemove = { vm.removeGroup(group.key) },
                        onFilePause = { vm.pauseFile(it) },
                        onFileResume = { vm.resumeFile(it) },
                        onFileRemove = { vm.removeFile(it) },
                        onProps = { vm.showProps(DownloadUiState.PropsTarget("group", group.key, group.title)) }
                    )
                }
            }
        }

        // ═══ СЕКЦИЯ: Отложенные ═══
        if (pausedGroups.isNotEmpty()) {
            item(key = "hdr_paused") {
                SectionHeaderWin95(
                    title = "Отложенные",
                    count = "(${pausedGroups.size})",
                    extra = "",
                    collapsed = "paused" in state.collapsedSections,
                    onClick = { vm.toggleSection("paused") }
                )
            }
            if ("paused" !in state.collapsedSections) {
                itemsIndexed(pausedGroups, key = { idx, g -> "p_${g.key}_$idx" }) { _, group ->
                    FolderCardWin95(
                        group = group,
                        isExpanded = group.key in state.expandedFolders,
                        isSelected = group.key in state.selection,
                        canMoveUp = false,
                        canMoveDown = false,
                        onToggleExpand = { vm.toggleFolder(group.key) },
                        onToggleSelect = { vm.toggleSelection(group.key) },
                        onMoveUp = {},
                        onMoveDown = {},
                        onPause = { vm.pauseGroup(group.key) },
                        onResume = { vm.resumeGroup(group.key) },
                        onRemove = { vm.removeGroup(group.key) },
                        onFilePause = { vm.pauseFile(it) },
                        onFileResume = { vm.resumeFile(it) },
                        onFileRemove = { vm.removeFile(it) },
                        onProps = { vm.showProps(DownloadUiState.PropsTarget("group", group.key, group.title)) }
                    )
                }
            }
        }

        // ═══ СЕКЦИЯ: Скачано ═══
        val hist = state.filteredHistory
        if (state.filter == DownloadUiState.Filter.ALL || state.filter == DownloadUiState.Filter.DONE) {
            item(key = "hdr_history") {
                SectionHeaderWin95(
                    title = "Скачано",
                    count = "(${hist.size})",
                    extra = if (hist.isNotEmpty()) "Очистить" else "",
                    collapsed = "history" in state.collapsedSections,
                    onClick = { vm.toggleSection("history") },
                    onExtraClick = { if (hist.isNotEmpty()) vm.clearHistory(ctx) }
                )
            }
            if ("history" !in state.collapsedSections) {
                if (hist.isNotEmpty()) {
                    itemsIndexed(hist, key = { idx, h -> "h_${h.id}_$idx" }) { _, item ->
                        HistoryRowWin95(
                            item = item,
                            isSelected = item.id in state.selection,
                            onToggleSelect = { vm.toggleSelection(item.id) },
                            onPlay = { vm.openItem(ctx, item) },
                            onDelete = { vm.showDeleteDialog(item) },
                            onProps = { vm.showProps(DownloadUiState.PropsTarget("history", item.id, item.fileName)) }
                        )
                    }
                }
            }
        }

        // ═══ Общий пустой стейт ═══
        if (state.active == null && groups.isEmpty() && hist.isEmpty()) {
            item(key = "all_empty") {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📥", fontSize = 36.sp)
                        Spacer(Modifier.height(6.dp))
                        Win95Text("Здесь появятся загрузки", bold = true)
                        Spacer(Modifier.height(4.dp))
                        Win95Text("Открой фильм → ⬇ Скачать", fontSize = 10, color = Win95Colors.TextDim)
                    }
                }
            }
        }

        item(key = "spacer") { Spacer(Modifier.height(8.dp)) }
    }
}

// ═══════════════════════════════════════════════════════════
// SECTION HEADER (кликабельный)
// ═══════════════════════════════════════════════════════════

@Composable
private fun SectionHeaderWin95(
    title: String,
    count: String = "",
    extra: String = "",
    collapsed: Boolean = false,
    onClick: () -> Unit,
    onExtraClick: (() -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 3.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (collapsed) "▶" else "▼",
            fontSize = 9.sp, color = Win95Colors.Text,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(12.dp)
        )
        Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace, color = Win95Colors.Text)
        if (count.isNotBlank()) {
            Spacer(Modifier.width(4.dp))
            Text(count, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Win95Colors.Navy)
        }
        Spacer(Modifier.weight(1f))
        if (extra.isNotBlank()) {
            Text(
                extra, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                color = Win95Colors.Navy,
                modifier = if (onExtraClick != null) Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onExtraClick
                    ) else Modifier
            )
        }
    }
}

@Composable
private fun EmptySectionWin95(icon: String, text: String) {
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, fontSize = 24.sp)
            Spacer(Modifier.height(4.dp))
            Win95Text(text, fontSize = 10, color = Win95Colors.TextDim)
        }
    }
}

// ═══════════════════════════════════════════════════════════
// ACTIVE CARD
// ═══════════════════════════════════════════════════════════

@Composable
private fun ActiveCardWin95(
    item: DownloadItem,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onWatch: () -> Unit,
    onProps: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Win95Colors.Face)
            .win95Bevel(raised = true, thickness = 2.dp)
            .padding(5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("📥", fontSize = 12.sp)
            Spacer(Modifier.width(4.dp))
            Text(
                item.request.title,
                fontSize = 11.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace, color = Win95Colors.Text,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Box(
                Modifier
                    .size(16.dp, 14.dp)
                    .background(Win95Colors.Face)
                    .win95Bevel(raised = true, thickness = 1.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onProps
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("⋯", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            item.request.fileName,
            fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Win95Colors.TextDim,
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Win95Progress(progress = item.progress, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${(item.progress * 100).toInt()}%", fontSize = 10.sp,
                fontFamily = FontFamily.Monospace, color = Win95Colors.Text)
            Spacer(Modifier.weight(1f))
            Text(
                "${com.garickbox.jecwebgo.util.Helpers.formatSize(item.downloadedBytes)} / ${com.garickbox.jecwebgo.util.Helpers.formatSize(item.totalBytes)}",
                fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Win95Colors.Text
            )
            Spacer(Modifier.width(8.dp))
            if (item.status == DownloadItem.Status.PAUSED) {
                Text("⏸", fontSize = 10.sp, color = Win95Colors.Olive, fontFamily = FontFamily.Monospace)
            } else {
                Text(com.garickbox.jecwebgo.util.Helpers.formatSpeed(item.speedBps),
                    fontSize = 10.sp, color = Win95Colors.Green,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(5.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Win95Button(onClick = if (item.status == DownloadItem.Status.PAUSED) onResume else onPause,
                modifier = Modifier.weight(1f).height(24.dp)) {
                Text(if (item.status == DownloadItem.Status.PAUSED) "▶ Дальше" else "⏸ Пауза",
                    fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
            Win95Button(onClick = onStop, modifier = Modifier.weight(1f).height(24.dp)) {
                Text("⏹ Стоп", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
            if (item.downloadedBytes > 5L * 1024 * 1024) {
                Win95Button(onClick = onWatch, modifier = Modifier.weight(1f).height(24.dp)) {
                    Text("▶ Смотреть", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// FOLDER CARD (раскрывающаяся папка или одиночный файл)
// ═══════════════════════════════════════════════════════════

@Composable
private fun FolderCardWin95(
    group: QueueGroup,
    isExpanded: Boolean,
    isSelected: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggleExpand: () -> Unit,
    onToggleSelect: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit,
    onFilePause: (String) -> Unit,
    onFileResume: (String) -> Unit,
    onFileRemove: (String) -> Unit,
    onProps: () -> Unit
) {
    val isFolder = group.isFolder
    val alpha = if (group.isPaused) 0.65f else 1f

    Column(
        Modifier
            .fillMaxWidth()
            .background(if (isSelected) Win95Colors.Navy.copy(alpha = 0.15f) else Win95Colors.Face)
            .win95Border(raised = true)
            .padding(0.dp)
    ) {
        // Header
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = if (isFolder) onToggleExpand else onProps
                )
                .padding(horizontal = 4.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            com.garickbox.jecwebgo.ui.common.win95.Win95Checkbox(
                checked = isSelected,
                onToggle = onToggleSelect
            )
            Spacer(Modifier.width(4.dp))
            if (isFolder) {
                Text(if (isExpanded) "▼" else "▶",
                    fontSize = 8.sp, color = Win95Colors.Text,
                    fontFamily = FontFamily.Monospace, modifier = Modifier.width(10.dp))
                Text("📁", fontSize = 14.sp)
            } else {
                Spacer(Modifier.width(10.dp))
                Text("📄", fontSize = 14.sp)
            }
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        group.title,
                        fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace, color = Win95Colors.Text,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isFolder) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "(${group.fileCount} ${if (group.fileCount == 1) "файл" else if (group.fileCount < 5) "файла" else "файлов"})",
                            fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Win95Colors.TextDim
                        )
                    }
                }
                if (group.parentDir.isNotBlank()) {
                    Text(group.parentDir.substringAfterLast("/"),
                        fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Win95Colors.TextDim,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(4.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    com.garickbox.jecwebgo.util.Helpers.formatSize(group.totalBytes),
                    fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Win95Colors.TextDim
                )
                if (group.isPaused) {
                    Text("⏸", fontSize = 9.sp, color = Win95Colors.Olive, fontFamily = FontFamily.Monospace)
                }
            }
        }

        // Actions row (только если не single file)
        if (isFolder && isExpanded) {
            HorizontalDividerWin95()
            // Files list
            group.items.forEach { f ->
                val fPaused = f.status == DownloadItem.Status.PAUSED
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Win95Colors.FaceLight)
                        .padding(start = 26.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (f.request.fileName.endsWith(".srt") || f.request.fileName.endsWith(".ass")) "📄" else "🎬",
                        fontSize = 11.sp)
                    Spacer(Modifier.width(4.dp))
                    Column(Modifier.weight(1f)) {
                        Text(f.request.fileName,
                            fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Win95Colors.Text,
                            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.alpha(if (fPaused) 0.6f else 1f))
                        Text(com.garickbox.jecwebgo.util.Helpers.formatSize(f.request.totalBytes),
                            fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Win95Colors.TextDim)
                    }
                    Win95SmallBtn4(if (fPaused) "▶" else "⏸") { if (fPaused) onFileResume(f.id) else onFilePause(f.id) }
                    Win95SmallBtn4("✕") { onFileRemove(f.id) }
                }
            }
        }

        // Action bar (up/down/pause/remove)
        if (!isExpanded || !isFolder) {
            HorizontalDividerWin95()
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Win95Colors.Face)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Win95SmallBtn4("↑") { if (canMoveUp) onMoveUp() }
                Win95SmallBtn4("↓") { if (canMoveDown) onMoveDown() }
                Win95SmallBtn4(if (group.isPaused) "▶" else "⏸") {
                    if (group.isPaused) onResume() else onPause()
                }
                Win95SmallBtn4("⋯") { onProps() }
                Spacer(Modifier.weight(1f))
                Win95SmallBtn4("✕") { onRemove() }
            }
        }
    }
}

@Composable
private fun Win95SmallBtn4(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(22.dp, 18.dp)
            .background(Win95Colors.Face)
            .win95Bevel(raised = true, thickness = 1.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HorizontalDividerWin95() {
    Box(
        Modifier.fillMaxWidth().height(1.dp).background(Win95Colors.Gray)
    )
}

// ═══════════════════════════════════════════════════════════
// HISTORY ROW (компактная)
// ═══════════════════════════════════════════════════════════

@Composable
private fun HistoryRowWin95(
    item: com.garickbox.jecwebgo.data.download.DownloadedItem,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onProps: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (isSelected) Win95Colors.Navy.copy(alpha = 0.15f) else Win95Colors.Face)
            .win95Border(raised = true)
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        com.garickbox.jecwebgo.ui.common.win95.Win95Checkbox(
            checked = isSelected,
            onToggle = onToggleSelect
        )
        Spacer(Modifier.width(4.dp))
        Text("💾", fontSize = 12.sp)
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.fileName,
                fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Win95Colors.Text,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                "${com.garickbox.jecwebgo.util.Helpers.formatSize(item.sizeBytes)} · ${formatDate(item.completedAt)}",
                fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Win95Colors.TextDim
            )
        }
        Spacer(Modifier.width(4.dp))
        Win95SmallBtn4("▶") { onPlay() }
        Win95SmallBtn4("⋯") { onProps() }
        Win95SmallBtn4("✕") { onDelete() }
    }
}

private fun formatDate(ms: Long): String {
    val sdf = java.text.SimpleDateFormat("dd.MM HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(ms))
}


// ═══════════════════════════════════════════════════════════
// DELETE HISTORY DIALOG (Win95 стиль)
// ═══════════════════════════════════════════════════════════

@Composable
private fun DeleteHistoryDialogWin95(
    item: DownloadedItem,
    onCancel: () -> Unit,
    onRemoveFromList: () -> Unit,
    onDeleteFile: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onCancel,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier
                .width(300.dp)
                .background(Win95Colors.Face)
                .win95Bevel(raised = true, thickness = 2.dp)
                .padding(2.dp)
        ) {
            Win95TitleBar(title = "Удалить файл?", icon = "🗑")
            Spacer(Modifier.height(10.dp))
            Row(Modifier.padding(horizontal = 12.dp)) {
                Text("❓", fontSize = 24.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Win95Text(item.fileName, bold = true, maxLines = 2)
                    Spacer(Modifier.height(6.dp))
                    Win95Text("Файл: ${com.garickbox.jecwebgo.util.Helpers.formatSize(item.sizeBytes)}",
                        fontSize = 10, color = Win95Colors.TextDim)
                    Spacer(Modifier.height(6.dp))
                    Win95Text("Удалить только из списка или стереть файл с диска?",
                        fontSize = 10, color = Win95Colors.TextDim, maxLines = 3)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Win95Button(onClick = onDeleteFile, modifier = Modifier.weight(1f).height(24.dp)) {
                    Text("🗑 Файл", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
                Win95Button(onClick = onRemoveFromList, modifier = Modifier.weight(1f).height(24.dp)) {
                    Text("📋 Список", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
                Win95Button(onClick = onCancel, modifier = Modifier.weight(1f).height(24.dp)) {
                    Text("Отмена", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// PROPERTIES DIALOG (Win95 стиль)
// ═══════════════════════════════════════════════════════════

@Composable
private fun PropertiesDialogWin95(
    target: DownloadUiState.PropsTarget,
    state: DownloadUiState,
    onClose: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier
                .width(340.dp)
                .heightIn(max = 500.dp)
                .background(Win95Colors.Face)
                .win95Bevel(raised = true, thickness = 2.dp)
                .padding(2.dp)
        ) {
            Win95TitleBar(
                title = "Свойства: ${target.title}",
                icon = "📋",
                onClose = onClose
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(androidx.compose.foundation.rememberScrollState())
                    .padding(8.dp)
            ) {
                when (target.type) {
                    "active" -> {
                        val a = state.active
                        if (a != null) {
                            PropsHeaderWin95(icon = "📥", title = a.request.title, sub = a.request.fileName)
                            PropsRow("Статус:", if (a.status == DownloadItem.Status.PAUSED) "⏸ Пауза" else "⬇ Загрузка")
                            PropsRow("Прогресс:", "${(a.progress * 100).toInt()}%")
                            PropsRow("Скачано:", com.garickbox.jecwebgo.util.Helpers.formatSize(a.downloadedBytes))
                            PropsRow("Всего:", com.garickbox.jecwebgo.util.Helpers.formatSize(a.totalBytes))
                            PropsRow("Скорость:", if (a.status == DownloadItem.Status.PAUSED) "—" else com.garickbox.jecwebgo.util.Helpers.formatSpeed(a.speedBps))
                            PropsRow("Hash:", a.request.hash.take(16) + "...")
                            PropsRow("Magnet:", a.request.magnet.take(40) + "...")
                        }
                    }
                    "group" -> {
                        val group = state.queueGroups.firstOrNull { it.key == target.key }
                        if (group != null) {
                            val isFolder = group.isFolder
                            PropsHeaderWin95(
                                icon = if (isFolder) "📁" else "📄",
                                title = group.title,
                                sub = if (isFolder) "${group.fileCount} файлов" else "Одиночный файл"
                            )
                            PropsRow("Размер:", com.garickbox.jecwebgo.util.Helpers.formatSize(group.totalBytes))
                            PropsRow("Статус:", if (group.isPaused) "⏸ Отложено" else "⏳ В очереди")
                            if (group.parentDir.isNotBlank()) {
                                PropsRow("Папка:", group.parentDir.substringAfterLast("/"))
                            }
                            Spacer(Modifier.height(8.dp))
                            HorizontalDividerWin95()
                            Spacer(Modifier.height(6.dp))
                            Win95Text("Файлы (${group.fileCount}):", bold = true, fontSize = 11)
                            Spacer(Modifier.height(4.dp))
                            group.items.forEach { f ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                    Text(if (f.request.fileName.endsWith(".srt")) "📄" else "🎬", fontSize = 11.sp)
                                    Spacer(Modifier.width(4.dp))
                                    Column(Modifier.weight(1f)) {
                                        Win95Text(f.request.fileName, fontSize = 10)
                                        Win95Text(
                                            com.garickbox.jecwebgo.util.Helpers.formatSize(f.request.totalBytes),
                                            fontSize = 9, color = Win95Colors.TextDim
                                        )
                                    }
                                }
                            }
                        }
                    }
                    "history" -> {
                        val h = state.history.firstOrNull { it.id == target.key }
                        if (h != null) {
                            PropsHeaderWin95(icon = "💾", title = h.fileName, sub = "Скачано ${formatDate(h.completedAt)}")
                            PropsRow("Размер:", com.garickbox.jecwebgo.util.Helpers.formatSize(h.sizeBytes))
                            PropsRow("Дата:", formatDate(h.completedAt))
                            PropsRow("Title:", h.title)
                            PropsRow("URI:", h.uri.take(50) + "...")
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Win95Button(onClick = onClose, modifier = Modifier.width(80.dp).height(24.dp)) {
                    Text("OK", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PropsHeaderWin95(icon: String, title: String, sub: String) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(48.dp)
                .background(Win95Colors.FaceLight)
                .win95Border(raised = true),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, fontSize = 28.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Win95Text(title, bold = true, fontSize = 12, maxLines = 3)
            if (sub.isNotBlank()) {
                Win95Text(sub, fontSize = 10, color = Win95Colors.TextDim, maxLines = 2)
            }
        }
    }
    HorizontalDividerWin95()
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun PropsRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) {
        Win95Text(label, fontSize = 10, color = Win95Colors.TextDim, modifier = Modifier.width(80.dp))
        Spacer(Modifier.width(4.dp))
        Win95Text(value, fontSize = 10, maxLines = 2, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Win95PlaceholderTab(title: String, subtitle: String, emoji: String) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Win95Colors.Face)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 36.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Win95Colors.Text
            )
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = Win95Colors.TextDim
            )
        }
    }
}
