package com.plantcure.ai.ui.shops

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantcure.ai.data.repository.ShopsRepository
import com.plantcure.ai.domain.model.ShopResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for nearby agricultural shops screen.
 */
@HiltViewModel
class ShopsViewModel @Inject constructor(
    private val shopsRepository: ShopsRepository
) : ViewModel() {

    private val _shops = MutableStateFlow<List<ShopResult>>(emptyList())
    val shops: StateFlow<List<ShopResult>> = _shops

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    /**
     * Load nearby shops for the given GPS location.
     */
    fun loadNearby(lat: Double, lon: Double) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _shops.value = shopsRepository.findNearbyShops(lat, lon)
            } catch (e: Exception) {
                e.printStackTrace()
                _shops.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
