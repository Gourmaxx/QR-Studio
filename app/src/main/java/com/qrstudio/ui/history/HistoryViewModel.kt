package com.qrstudio.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qrstudio.core.data.HistoryItem
import com.qrstudio.core.data.HistoryOrigin
import com.qrstudio.core.data.HistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class HistoryFilter(val label: String) {
    ALL("Tout"),
    GENERATED("Créés"),
    SCANNED("Scannés")
}

data class HistoryUiState(
    val items: List<HistoryItem> = emptyList(),
    val filter: HistoryFilter = HistoryFilter.ALL,
    val query: String = "",
    val hasAnyEntry: Boolean = false
)

class HistoryViewModel : ViewModel() {

    private val filter = MutableStateFlow(HistoryFilter.ALL)
    private val query = MutableStateFlow("")

    val state: StateFlow<HistoryUiState> =
        combine(HistoryRepository.items, filter, query) { items, activeFilter, activeQuery ->
            val byOrigin = when (activeFilter) {
                HistoryFilter.ALL -> items
                HistoryFilter.GENERATED -> items.filter { it.origin == HistoryOrigin.GENERATED }
                HistoryFilter.SCANNED -> items.filter { it.origin == HistoryOrigin.SCANNED }
            }
            val searched = if (activeQuery.isBlank()) byOrigin else byOrigin.filter {
                it.content.contains(activeQuery, ignoreCase = true) ||
                    it.label?.contains(activeQuery, ignoreCase = true) == true
            }
            // Stable sort: pinned entries first, chronological order preserved within groups.
            HistoryUiState(
                items = searched.sortedByDescending { it.pinned },
                filter = activeFilter,
                query = activeQuery,
                hasAnyEntry = items.isNotEmpty()
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            // Seeded from the repository (already loaded synchronously) so the
            // screen doesn't flash its empty state for one frame.
            initialValue = HistoryUiState(
                items = HistoryRepository.items.value.sortedByDescending { it.pinned },
                hasAnyEntry = HistoryRepository.items.value.isNotEmpty()
            )
        )

    fun setFilter(value: HistoryFilter) {
        filter.value = value
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun togglePinned(item: HistoryItem) = HistoryRepository.setPinned(item.id, !item.pinned)

    fun delete(id: String) = HistoryRepository.delete(id)

    fun clearAll() = HistoryRepository.clear()

    fun exportJson(): String = HistoryRepository.exportJson()

    /** Returns the number of imported entries, or null when the text is not a valid export. */
    fun importJson(json: String): Int? = HistoryRepository.importJson(json)
}
