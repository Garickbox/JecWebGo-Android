package com.garickbox.jecwebgo.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
    val items: List<TorrentItem> = emptyList(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val lastUpdate: String = "",
    val error: String? = null
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = _state.value.items.isEmpty())
            val items = DataRepository.load(getApplication())
            _state.value = _state.value.copy(
                items = items,
                loading = false,
                error = if (items.isEmpty()) "База пуста. Обновите данные." else null
            )
            updateDate()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(refreshing = true, error = null)
            val items = DataRepository.refresh(getApplication())
            _state.value = _state.value.copy(
                items = items,
                refreshing = false,
                error = if (items.isEmpty()) "Не удалось загрузить базу" else null
            )
            updateDate()
        }
    }

    private fun updateDate() {
        val age = DataRepository.cacheAge(getApplication())
        val fmt = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
        val text = if (age < Long.MAX_VALUE / 2) fmt.format(Date(System.currentTimeMillis() - age)) else "—"
        _state.value = _state.value.copy(lastUpdate = text)
    }
}
