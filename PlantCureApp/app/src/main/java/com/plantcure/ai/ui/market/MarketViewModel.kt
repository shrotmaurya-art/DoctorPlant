package com.plantcure.ai.ui.market

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantcure.ai.data.local.ApiKeyManager
import com.plantcure.ai.data.local.entity.MarketPrice
import com.plantcure.ai.data.repository.MarketRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log

sealed class MarketUiState {
    object Idle : MarketUiState()
    object NoApiKey : MarketUiState()
}

sealed class MarketState {
    object Loading : MarketState()
    object NoKey : MarketState()
    data class Success(val records: List<MarketPrice>) : MarketState()
    data class Error(val message: String) : MarketState()
}

/**
 * ViewModel for Market Prices screen.
 * Manages commodity, state, and district selection + price data.
 */
@HiltViewModel
class MarketViewModel @Inject constructor(
    private val repository: MarketRepository
) : ViewModel() {

    private val _selectedCommodity = MutableStateFlow("Tomato")
    val selectedCommodity: StateFlow<String> = _selectedCommodity

    private val _selectedState = MutableStateFlow("Maharashtra")
    val selectedState: StateFlow<String> = _selectedState

    private val _selectedDistrict = MutableStateFlow<String?>(null)
    val selectedDistrict: StateFlow<String?> = _selectedDistrict

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _refreshFailed = MutableStateFlow(false)
    val refreshFailed: StateFlow<Boolean> = _refreshFailed

    private val _refreshStatusCode = MutableStateFlow<Int?>(null)
    val refreshStatusCode: StateFlow<Int?> = _refreshStatusCode

    private val _uiState = MutableStateFlow<MarketUiState>(MarketUiState.Idle)
    val uiState: StateFlow<MarketUiState> = _uiState

    private val _state = MutableStateFlow<MarketState>(MarketState.Success(emptyList()))
    val state: StateFlow<MarketState> = _state

    private val statesAndDistricts = repository.getStatesAndDistricts()
    val statesList = statesAndDistricts.keys.toList().sorted()

    enum class SortOption { NONE, PRICE_HIGH, PRICE_LOW, NAME }

    private val _sortOption = MutableStateFlow(SortOption.NONE)
    val sortOption: StateFlow<SortOption> = _sortOption

    /** Reactive price list that updates when commodity, state, district, or sort changes */
    @OptIn(ExperimentalCoroutinesApi::class)
    val prices: StateFlow<List<MarketPrice>> = combine(
        state, _selectedDistrict, _sortOption
    ) { marketState, district, sort ->
        val list = when (marketState) {
            is MarketState.Success -> marketState.records
            else -> emptyList()
        }
        val filtered = if (district == null || district == "All Districts") {
            list
        } else {
            list.filter { it.district.contains(district, ignoreCase = true) || district.contains(it.district, ignoreCase = true) }
        }
        when (sort) {
            SortOption.PRICE_HIGH -> filtered.sortedByDescending { it.modalPrice }
            SortOption.PRICE_LOW -> filtered.sortedBy { it.modalPrice }
            SortOption.NAME -> filtered.sortedBy { it.market }
            SortOption.NONE -> filtered
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private data class DataParams(
        val commodity: String,
        val state: String,
        val district: String?,
        val sort: SortOption
    )

    init {
        refreshPrices()
    }

    fun selectCommodity(commodity: String) {
        _selectedCommodity.value = commodity
        refreshPrices()
    }

    fun selectState(state: String) {
        _selectedState.value = state
        _selectedDistrict.value = null // reset district on state change
        refreshPrices()
    }

    fun selectDistrict(district: String?) {
        _selectedDistrict.value = district
        refreshPrices()
    }

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
    }

    fun checkApiKey() {
        if (!ApiKeyManager.hasGroqKey()) {
            _uiState.value = MarketUiState.NoApiKey
        } else {
            _uiState.value = MarketUiState.Idle
            if (prices.value.isEmpty()) refreshPrices()
        }
    }

    fun refreshPrices() {
        loadPrices(_selectedCommodity.value, _selectedState.value)
    }

    fun loadPrices(
        commodity: String,
        state: String = "Maharashtra"
    ) {
        Log.d("MKT", "ViewModel function called!")
        viewModelScope.launch { // ← NO Dispatchers.IO here
            _isRefreshing.value = true
            _refreshFailed.value = false
            _refreshStatusCode.value = null
            _state.value = MarketState.Loading
            Log.d("MKT_VM", "Loading $commodity in $state")
            
            // Switch to IO only for the repository call
            val result = withContext(Dispatchers.IO) {
                repository.getPrices(
                    commodity = commodity,
                    state = state
                )
            }
            
            // Back on Main thread here
            _isRefreshing.value = false
            Log.d("MKT", "Repository returned")
            Log.d("MKT", "Result success: ${result.isSuccess}")
            Log.d("MKT", "Result failure: ${result.isFailure}")
            
            result.fold(
                onSuccess = { prices ->
                    Log.d("MKT_VM", "Got ${prices.size} prices")
                    Log.d("MKT", "Fold success: ${prices.size}")
                    _state.value = MarketState.Success(prices)
                },
                onFailure = { e ->
                    Log.e("MKT_VM", "Failed: ${e.message}")
                    Log.e("MKT", "Fold failure: ${e.message}")
                    if (e.message == "no_key") {
                        _uiState.value = MarketUiState.NoApiKey
                        _state.value = MarketState.NoKey
                    } else if (e.message?.contains("429") == true) {
                        _state.value = MarketState.Error(
                            "Too many requests.\n" +
                            "Please wait 1 minute."
                        )
                    } else if (e.message?.contains("500") == true) {
                        _state.value = MarketState.Error(
                            "AI service error.\n" +
                            "Please try again."
                        )
                    } else {
                        _state.value = MarketState.Error(
                            e.message ?: "Unknown error"
                        )
                    }
                }
            )
        }
    }

    fun getDistrictsForState(state: String): List<String> {
        return statesAndDistricts[state] ?: listOf("All Districts")
    }
}
