package com.garickbox.jecwebgo.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.garickbox.jecwebgo.data.model.GroupedTorrent
import com.garickbox.jecwebgo.data.network.DataRepository
import com.garickbox.jecwebgo.data.network.TorrentItem
import kotlinx.coroutines.flow.MutableStateFlow
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

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = _state.value.items.isEmpty())
            val raw = DataRepository.load(getApplication())
            val key = raw.size
            val grouped = cachedGroups?.takeIf { cachedFromHash == key } ?: run {
                val g = group(raw)
                cachedGroups = g
                cachedFromHash = key
                g
            }
            _state.value = _state.value.copy(
                items = grouped, loading = false,
                error = if (grouped.isEmpty()) "База пуста. Обновите данные." else null
            )
            updateDateFromMeta()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(refreshing = true, error = null)
            val raw = DataRepository.refresh(getApplication())
            val grouped = group(raw)
            cachedGroups = grouped
            cachedFromHash = raw.size
            _state.value = _state.value.copy(
                items = grouped, refreshing = false,
                error = if (grouped.isEmpty()) "Не удалось загрузить базу" else null
            )
            updateDateFromMeta()
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
                    items = list.sortedByDescending { it.seeds }
                )
            }
            .sortedByDescending { it.maxSeeds * it.count }
    }
}
