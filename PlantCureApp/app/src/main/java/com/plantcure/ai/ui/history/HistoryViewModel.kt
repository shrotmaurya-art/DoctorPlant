package com.plantcure.ai.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantcure.ai.data.local.entity.ScanHistory
import com.plantcure.ai.data.repository.HistoryStats
import com.plantcure.ai.data.repository.ScanHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: ScanHistoryRepository
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val filterSeverity = MutableStateFlow<String?>(null)
    private val filterCauseType = MutableStateFlow<String?>(null)

    private val _stats = MutableStateFlow<HistoryStats?>(null)
    val stats: StateFlow<HistoryStats?> = _stats

    init {
        loadStats()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val scans: StateFlow<List<ScanHistory>> = combine(
        searchQuery, filterSeverity, filterCauseType
    ) { query, severity, cause ->
        Triple(query, severity, cause)
    }.flatMapLatest { (query, severity, cause) ->
        when {
            query.isNotEmpty() -> repository.searchScans(query)
            severity != null -> repository.getScansBySeverity(severity)
            cause != null -> repository.getScansByCauseType(cause)
            else -> repository.getAllScans()
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun search(query: String) {
        searchQuery.value = query
        filterSeverity.value = null
        filterCauseType.value = null
    }

    fun filterBySeverity(severity: String?) {
        filterSeverity.value = severity
        filterCauseType.value = null
        searchQuery.value = ""
    }

    fun filterByCause(cause: String?) {
        filterCauseType.value = cause
        filterSeverity.value = null
        searchQuery.value = ""
    }

    fun clearFilters() {
        searchQuery.value = ""
        filterSeverity.value = null
        filterCauseType.value = null
    }

    fun deleteScan(scan: ScanHistory) {
        viewModelScope.launch {
            repository.deleteScan(scan)
            loadStats()
        }
    }

    fun undoDelete(scan: ScanHistory) {
        viewModelScope.launch {
            repository.undoDelete(scan)
            loadStats()
        }
    }

    private fun loadStats() {
        viewModelScope.launch {
            _stats.value = repository.getStats()
        }
    }
}
