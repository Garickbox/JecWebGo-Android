package com.garickbox.jecwebgo.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.garickbox.jecwebgo.data.model.GroupedTorrent
import com.garickbox.jecwebgo.data.network.DataRepository
import com.garickbox.jecwebgo.data.network.TorrentItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HomeState(
    val items: List<GroupedTorrent> = emptyList(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val lastUpdate: String = "",
    val error: String? = null
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()
    @Volatile private var cachedGroups: List<GroupedTorrent>? = null
    @Volatile private var cachedFromHash: Int = 0
    @Volatile private var loadingInProgress: Boolean = false

    fun load() {
        if (loadingInProgress) {
            android.util.Log.d("JecWeb", "HomeVM: load() skipped (already in progress)")
            return
        }
        loadingInProgress = true
        viewModelScope.launch {
            try {
                val t0 = System.currentTimeMillis()
                _state.update { it.copy(loading = it.items.isEmpty()) }
                val raw = DataRepository.load(getApplication())
                val key = raw.size
                android.util.Log.d("JecWeb", "HomeVM: raw=$key cache=${cachedGroups?.size} cachedKey=$cachedFromHash")
                val grouped = cachedGroups?.takeIf { cachedFromHash == key } ?: run {
                    android.util.Log.d("JecWeb", "HomeVM: computing group() on Default for $key items")
                    val g = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { group(raw) }
                    cachedGroups = g
                    cachedFromHash = key
                    g
                }
                android.util.Log.d("JecWeb", "HomeVM: grouped=${grouped.size} in ${System.currentTimeMillis() - t0}ms")
                _state.value = _state.value.copy(
                    items = grouped, loading = false,
                    error = if (grouped.isEmpty()) "База пуста. Обновите данные." else null
                )
                updateDateFromMeta()
            } catch (e: Exception) {
                android.util.Log.e("JecWeb", "HomeVM load fail: ${e.message}")
                _state.value = _state.value.copy(loading = false, error = e.message)
            } finally {
                loadingInProgress = false
            }
        }
    }

    fun refresh() {
        if (loadingInProgress) {
            android.util.Log.d("JecWeb", "HomeVM: refresh() skipped (load in progress)")
            return
        }
        loadingInProgress = true
        viewModelScope.launch {
            try {
                _state.update { it.copy(refreshing = true, error = null) }
                val raw = DataRepository.refresh(getApplication())
                val grouped = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { group(raw) }
                cachedGroups = grouped
                cachedFromHash = raw.size
            _state.value = _state.value.copy(
                items = grouped, refreshing = false,
                error = if (grouped.isEmpty()) "Не удалось загрузить базу" else null
            )
            updateDateFromMeta()
            } catch (e: Exception) {
                android.util.Log.e("JecWeb", "HomeVM refresh fail: ${e.message}")
                _state.value = _state.value.copy(refreshing = false, error = e.message)
            } finally {
                loadingInProgress = false
            }
        }
    }

    private suspend fun updateDateFromMeta() {
        val meta = DataRepository.loadMeta(getApplication())
        if (meta != null && meta.generated_at.isNotBlank()) {
            try {
                val instant = java.time.Instant.parse(meta.generated_at)
                val local = instant.atZone(java.time.ZoneId.systemDefault())
                val text = java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm").format(local)
                _state.value = _state.value.copy(lastUpdate = text)
                return
            } catch (_: Exception) {}
        }
        val age = DataRepository.cacheAge(getApplication())
        val fmt = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
        val text = if (age < Long.MAX_VALUE / 2) fmt.format(Date(System.currentTimeMillis() - age)) else "—"
        _state.value = _state.value.copy(lastUpdate = text)
    }

    private fun cleanTitle(raw: String): String = com.garickbox.jecwebgo.util.TitleUtil.cleanTitle(raw)
    private fun mediaType(category: String): String = com.garickbox.jecwebgo.util.TitleUtil.mediaType(category)

    private fun group(items: List<TorrentItem>): List<GroupedTorrent> {
        return items
            .groupBy { "${cleanTitle(it.title)}|${it.year}|${mediaType(it.category)}" }
            .map { (_, list) ->
                val withPoster = list.firstOrNull { it.poster.isNotBlank() } ?: list.first()
                val withDesc = list.firstOrNull { it.description.isNotBlank() } ?: withPoster
                GroupedTorrent(
                    cleanTitle = cleanTitle(withPoster.title),
                    year = withPoster.year,
                    poster = withPoster.poster,
                    rating = list.maxOfOrNull { it.rating } ?: 0.0,
                    genre = withDesc.genre,
                    description = withDesc.description,
                    director = withDesc.director,
                    actors = withDesc.actors,
                    mediaType = mediaType(withPoster.category),
                    items = list.sortedByDescending { it.seeds }
                )
            }
            .sortedByDescending { it.maxSeeds * it.count }
    }
}
