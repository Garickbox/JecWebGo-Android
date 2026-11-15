package com.garickbox.jecwebgo.ui.download

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garickbox.jecwebgo.data.download.DownloadItem
import com.garickbox.jecwebgo.data.download.DownloadRepository
import com.garickbox.jecwebgo.data.download.DownloadedItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════
// UI МОДЕЛИ
// ═══════════════════════════════════════════════════════════

data class QueueGroup(
    val key: String,
    val title: String,
    val parentDir: String,
    val items: List<DownloadItem>,
    val totalBytes: Long,
    val isPaused: Boolean,
    val downloadedBytes: Long = 0L
) {
    val isFolder: Boolean get() = items.size > 1 || parentDir.isNotBlank()
    val fileCount: Int get() = items.size
    val progress: Float
        get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
}

data class DownloadUiState(
    // raw from repo
    val active: DownloadItem? = null,
    val queue: List<DownloadItem> = emptyList(),
    val history: List<DownloadedItem> = emptyList(),
    // ui controls
    val search: String = "",
    val filter: Filter = Filter.ALL,
    val sort: Sort = Sort.DEFAULT,
    val selection: Set<String> = emptySet(),
    val collapsedSections: Set<String> = emptySet(),
    val expandedFolders: Set<String> = emptySet(),
    // dialogs
    val deleteDialogFor: DownloadedItem? = null,
    val propsFor: PropsTarget? = null
) {
    enum class Filter { ALL, ACTIVE, QUEUE, PAUSED, DONE }
    enum class Sort { DEFAULT, NAME, SIZE, DATE }

    data class PropsTarget(
        val type: String,   // "active" | "group" | "history"
        val key: String,    // id или groupKey
        val title: String
    )

    // ═══ Derived: queue groups ═══
    val queueGroups: List<QueueGroup>
        get() {
            if (filter == Filter.DONE || filter == Filter.ACTIVE) return emptyList()
            val grouped = queue.groupBy { "${it.request.title}|${it.request.relPath.substringBeforeLast("/", "")}" }
            var result = grouped.map { (key, items) ->
                val first = items.first()
                QueueGroup(
                    key = key,
                    title = first.request.title,
                    parentDir = first.request.relPath.substringBeforeLast("/", ""),
                    items = items,
                    totalBytes = items.sumOf { it.request.totalBytes },
                    isPaused = items.all { it.status == DownloadItem.Status.PAUSED },
                    downloadedBytes = items.sumOf { it.downloadedBytes }
                )
            }
            if (filter == Filter.QUEUE) result = result.filter { !it.isPaused }
            if (filter == Filter.PAUSED) result = result.filter { it.isPaused }
            if (search.isNotBlank()) {
                val q = search.lowercase()
                result = result.filter { g ->
                    g.title.lowercase().contains(q) ||
                        g.items.any { it.request.fileName.lowercase().contains(q) }
                }
            }
            return when (sort) {
                Sort.NAME -> result.sortedBy { it.title.lowercase() }
                Sort.SIZE -> result.sortedByDescending { it.totalBytes }
                else -> result  // DEFAULT = порядок из repository
            }
        }

    // ═══ Derived: filtered history ═══
    val filteredHistory: List<DownloadedItem>
        get() {
            if (filter != Filter.ALL && filter != Filter.DONE) return emptyList()
            var r = history
            if (search.isNotBlank()) {
                val q = search.lowercase()
                r = r.filter { it.fileName.lowercase().contains(q) || it.title.lowercase().contains(q) }
            }
            return when (sort) {
                Sort.NAME -> r.sortedBy { it.fileName.lowercase() }
                Sort.DATE -> r.sortedByDescending { it.completedAt }
                else -> r
            }
        }

    // ═══ Derived: показать активную секцию ═══
    val showActiveSection: Boolean
        get() = (filter == Filter.ALL || filter == Filter.ACTIVE) &&
            (search.isBlank() || (active?.request?.title?.lowercase()?.contains(search.lowercase()) == true))
}

// ═══════════════════════════════════════════════════════════
// VIEWMODEL
// ═══════════════════════════════════════════════════════════

class DownloadViewModel : ViewModel() {
    private val _state = MutableStateFlow(DownloadUiState())
    val state: StateFlow<DownloadUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                DownloadRepository.active,
                DownloadRepository.queue,
                DownloadRepository.history
            ) { a, q, h -> Triple(a, q, h) }
                .collect { (a, q, h) ->
                    _state.update { it.copy(active = a, queue = q, history = h) }
                }
        }
    }

    // ═══ UI controls ═══
    fun onSearch(s: String) { _state.update { it.copy(search = s) } }
    fun onFilter(f: DownloadUiState.Filter) { _state.update { it.copy(filter = f) } }
    fun onSort(s: DownloadUiState.Sort) { _state.update { it.copy(sort = s) } }

    fun toggleSection(id: String) {
        _state.update { st ->
            val c = st.collapsedSections
            st.copy(collapsedSections = if (id in c) c - id else c + id)
        }
    }

    fun toggleFolder(key: String) {
        _state.update { st ->
            val e = st.expandedFolders
            st.copy(expandedFolders = if (key in e) e - key else e + key)
        }
    }

    fun expandAllFolders() {
        _state.update { st ->
            st.copy(expandedFolders = st.queueGroups.map { it.key }.toSet())
        }
    }

    fun collapseAllFolders() {
        _state.update { it.copy(expandedFolders = emptySet()) }
    }

    // ═══ Selection ═══
    fun toggleSelection(id: String) {
        _state.update { st ->
            val s = st.selection
            st.copy(selection = if (id in s) s - id else s + id)
        }
    }

    fun clearSelection() { _state.update { it.copy(selection = emptySet()) } }

    fun selectAll() {
        _state.update { st ->
            val ids = mutableSetOf<String>()
            st.queueGroups.forEach { g -> ids.add(g.key) }
            st.filteredHistory.forEach { h -> ids.add(h.id) }
            st.copy(selection = ids)
        }
    }

    // ═══ Active ═══
    fun pauseActive() { DownloadRepository.pause() }
    fun resumeActive() { DownloadRepository.resume() }
    fun stopActive() { DownloadRepository.cancel() }

    fun watchActive(ctx: Context) {
        val a = _state.value.active ?: return
        com.garickbox.jecwebgo.ui.common.PlayActions.playHash(
            ctx, viewModelScope, a.request.hash, a.request.title
        )
    }

    // ═══ Groups ═══
    fun pauseGroup(key: String) { DownloadRepository.pauseGroup(key) }
    fun resumeGroup(key: String) { DownloadRepository.resumeGroup(key) }
    fun removeGroup(key: String) { DownloadRepository.removeGroupFromQueue(key) }

    fun moveGroupUp(key: String) {
        val groups = _state.value.queueGroups
        val idx = groups.indexOfFirst { it.key == key }
        if (idx > 0) DownloadRepository.moveGroupInQueue(key, groups[idx - 1].key)
    }

    fun moveGroupDown(key: String) {
        val groups = _state.value.queueGroups
        val idx = groups.indexOfFirst { it.key == key }
        if (idx >= 0 && idx < groups.size - 1) DownloadRepository.moveGroupInQueue(key, groups[idx + 1].key)
    }

    // ═══ Files ═══
    fun pauseFile(id: String) { DownloadRepository.pauseQueueItem(id) }
    fun resumeFile(id: String) { DownloadRepository.resumeQueueItem(id) }
    fun removeFile(id: String) { DownloadRepository.removeFromQueue(id) }

    // ═══ History ═══
    fun showDeleteDialog(item: DownloadedItem) {
        _state.update { it.copy(deleteDialogFor = item) }
    }

    fun hideDeleteDialog() {
        _state.update { it.copy(deleteDialogFor = null) }
    }

    fun deleteHistoryAndFile(ctx: Context, item: DownloadedItem) {
        runCatching { ctx.contentResolver.delete(android.net.Uri.parse(item.uri), null, null) }
        DownloadRepository.removeHistory(ctx, item.id)
        hideDeleteDialog()
    }

    fun removeFromList(ctx: Context, item: DownloadedItem) {
        DownloadRepository.removeHistory(ctx, item.id)
        hideDeleteDialog()
    }

    fun openItem(ctx: Context, item: DownloadedItem) {
        com.garickbox.jecwebgo.ui.player.PendingPlayer.openReady(
            url = item.uri, title = item.title,
            hash = "", currentIndex = 1,
            fileName = item.fileName, localKey = item.uri
        )
    }

    fun clearHistory(ctx: Context) {
        val list = _state.value.history
        list.forEach { DownloadRepository.removeHistory(ctx, it.id) }
        clearSelection()
    }

    // ═══ Batch actions ═══
    fun pauseSelected() {
        val st = _state.value
        st.selection.forEach { key ->
            if (st.queueGroups.any { it.key == key }) pauseGroup(key) else pauseFile(key)
        }
    }

    fun resumeSelected() {
        val st = _state.value
        st.selection.forEach { key ->
            if (st.queueGroups.any { it.key == key }) resumeGroup(key) else resumeFile(key)
        }
    }

    fun removeSelected(ctx: Context) {
        val st = _state.value
        st.selection.forEach { key ->
            when {
                st.queueGroups.any { it.key == key } -> removeGroup(key)
                st.history.any { it.id == key } -> {
                    val h = st.history.first { it.id == key }
                    DownloadRepository.removeHistory(ctx, h.id)
                }
                else -> removeFile(key)
            }
        }
        clearSelection()
    }

    fun pauseAll() {
        _state.value.queueGroups.forEach { g -> if (!g.isPaused) pauseGroup(g.key) }
        DownloadRepository.pause()
    }

    fun resumeAll() {
        _state.value.queueGroups.forEach { g -> if (g.isPaused) resumeGroup(g.key) }
        DownloadRepository.resume()
    }

    // ═══ Props dialog ═══
    fun showProps(target: DownloadUiState.PropsTarget) {
        _state.update { it.copy(propsFor = target) }
    }

    fun hideProps() {
        _state.update { it.copy(propsFor = null) }
    }
}
