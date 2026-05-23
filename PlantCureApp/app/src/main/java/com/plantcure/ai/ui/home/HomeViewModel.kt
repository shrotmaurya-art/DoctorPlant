package com.plantcure.ai.ui.home

import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantcure.ai.data.local.dao.TreatmentScheduleDao
import com.plantcure.ai.data.local.entity.TreatmentSchedule
import com.plantcure.ai.data.repository.WeatherRepository
import com.plantcure.ai.domain.classifier.PlantDiseaseClassifier
import com.plantcure.ai.domain.model.DetectionResult
import com.plantcure.ai.domain.model.WeatherData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Home screen.
 * Manages TFLite classification state and weather data.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val classifier: PlantDiseaseClassifier,
    private val weatherRepository: WeatherRepository,
    private val treatmentDao: TreatmentScheduleDao
) : ViewModel() {

    private val _isProcessing = MutableLiveData(false)
    val isProcessing: LiveData<Boolean> = _isProcessing

    private val _weatherData = MutableStateFlow<WeatherData?>(null)
    val weatherData: StateFlow<WeatherData?> = _weatherData

    private val _weatherLoading = MutableStateFlow(false)
    val weatherLoading: StateFlow<Boolean> = _weatherLoading

    private val _nextTreatment = MutableStateFlow<TreatmentSchedule?>(null)
    val nextTreatment: StateFlow<TreatmentSchedule?> = _nextTreatment

    fun setProcessing(loading: Boolean) {
        _isProcessing.value = loading
    }

    /**
     * Run TFLite inference on a bitmap.
     * Must be called from a background thread (Dispatchers.IO).
     */
    fun classify(bitmap: Bitmap): List<DetectionResult> {
        return classifier.runInference(bitmap)
    }

    /**
     * Load weather data for the given location.
     */
    fun loadWeather(lat: Double, lon: Double) {
        viewModelScope.launch {
            _weatherLoading.value = true
            try {
                _weatherData.value = weatherRepository.getWeather(lat, lon)
            } catch (e: Exception) {
                e.printStackTrace()
                _weatherData.value = null
            } finally {
                _weatherLoading.value = false
            }
        }
    }

    fun refreshNextTreatment() {
        viewModelScope.launch {
            _nextTreatment.value = treatmentDao.getNextTreatment()
        }
    }
}
