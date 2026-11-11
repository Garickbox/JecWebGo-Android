package com.garickbox.jecwebgo.ui.search
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garickbox.jecwebgo.domain.SearchRepository
import com.garickbox.jecwebgo.domain.model.SearchItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
data class SearchState(
    val query: String = "", val loading: Boolean = false,
    val items: List<SearchItem> = emptyList(), val error: String? = null
)
class SearchViewModel : ViewModel() {
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()
    fun onQuery(q: String) { _state.value = _state.value.copy(query = q) }
    fun search() {
        val q = _state.value.query.trim()
        if (q.length < 2) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null, items = emptyList())
            try {
                val r = SearchRepository.jacred(q)
                _state.value = _state.value.copy(loading = false, items = r)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message ?: "error")
            }
        }
    }
}
