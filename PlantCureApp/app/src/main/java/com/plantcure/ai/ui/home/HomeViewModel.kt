package com.plantcure.ai.ui.home

import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.plantcure.ai.domain.classifier.PlantDiseaseClassifier
import com.plantcure.ai.domain.model.DetectionResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * ViewModel for the Home screen.
 * Manages TFLite classification state and weather data.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val classifier: PlantDiseaseClassifier
) : ViewModel() {

    private val _isProcessing = MutableLiveData(false)
    val isProcessing: LiveData<Boolean> = _isProcessing

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
}
