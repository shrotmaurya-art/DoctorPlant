package com.plantcure.ai.ui.market

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

/**
 * ViewModel for Market Prices screen.
 * Manages commodity, state, and district selection + price data.
 */
@HiltViewModel
class MarketViewModel @Inject constructor(
    private val marketRepository: MarketRepository
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

    private val statesAndDistricts = marketRepository.getStatesAndDistricts()
    val statesList = statesAndDistricts.keys.toList().sorted()

    enum class SortOption { NONE, PRICE_HIGH, PRICE_LOW, NAME }

    private val _sortOption = MutableStateFlow(SortOption.NONE)
    val sortOption: StateFlow<SortOption> = _sortOption

    /** Reactive price list that updates when commodity, state, district, or sort changes */
    @OptIn(ExperimentalCoroutinesApi::class)
    val prices: StateFlow<List<MarketPrice>> = combine(
        _selectedCommodity, _selectedState, _selectedDistrict, _sortOption
    ) { commodity, state, district, sort ->
        DataParams(commodity, state, district, sort)
    }
        .flatMapLatest { params ->
            val flow = if (params.district == null || params.district == "All Districts") {
                marketRepository.getPricesForCommodityAndState(params.commodity, params.state)
            } else {
                marketRepository.getPricesForCommodityStateAndDistrict(params.commodity, params.state, params.district)
            }
            // Apply sorting
            flow.map { list ->
                when (params.sort) {
                    SortOption.PRICE_HIGH -> list.sortedByDescending { it.modalPrice }
                    SortOption.PRICE_LOW -> list.sortedBy { it.modalPrice }
                    SortOption.NAME -> list.sortedBy { it.market }
                    SortOption.NONE -> list
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

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

    fun refreshPrices() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _refreshFailed.value = false
            try {
                val success = marketRepository.refreshPrices(
                    commodity = _selectedCommodity.value,
                    state = _selectedState.value,
                    district = _selectedDistrict.value
                )
                _refreshFailed.value = !success
            } catch (e: Exception) {
                _refreshFailed.value = true
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun getDistrictsForState(state: String): List<String> {
        return statesAndDistricts[state] ?: listOf("All Districts")
    }
}
