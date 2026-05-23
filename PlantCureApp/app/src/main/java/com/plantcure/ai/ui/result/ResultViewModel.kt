package com.plantcure.ai.ui.result

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantcure.ai.data.repository.DiseaseRepository
import com.plantcure.ai.data.repository.ScanHistoryRepository
import com.plantcure.ai.domain.model.DetectionResult
import com.plantcure.ai.domain.model.Disease
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ResultViewModel @Inject constructor(
    private val diseaseRepository: DiseaseRepository,
    private val historyRepository: ScanHistoryRepository,
    private val treatmentDao: com.plantcure.ai.data.local.dao.TreatmentScheduleDao
) : ViewModel() {

    private val _disease = MutableLiveData<Disease?>()
    val disease: LiveData<Disease?> = _disease

    private val _detectionResult = MutableLiveData<DetectionResult>()
    val detectionResult: LiveData<DetectionResult> = _detectionResult

    private val _scheduleCreated = MutableLiveData<Boolean>()
    val scheduleCreated: LiveData<Boolean> = _scheduleCreated

    private var hasSaved = false

    fun loadResult(imagePath: String, diseaseLabel: String, confidence: Float, scanId: Int = -1) {
        val loadedDisease = diseaseRepository.getDiseaseByLabel(diseaseLabel)
        _disease.value = loadedDisease
        
        val severity = loadedDisease?.severityDefault ?: DetectionResult.computeSeverity(confidence)
        _detectionResult.value = DetectionResult(diseaseLabel, confidence, severity)

        // Only auto-save if this is a new scan (scanId == -1) and we haven't saved it yet
        if (scanId == -1 && !hasSaved) {
            saveScan(imagePath, diseaseLabel, loadedDisease, confidence, severity)
        }
    }

    private fun saveScan(
        imagePath: String, 
        diseaseLabel: String, 
        disease: Disease?, 
        confidence: Float, 
        severity: String
    ) {
        viewModelScope.launch {
            val diseaseName = disease?.name ?: diseaseRepository.getDisplayName(diseaseLabel)
            val cropName = disease?.affectedCrop ?: diseaseRepository.getCropName(diseaseLabel)
            val causeType = disease?.causeType ?: "Unknown"

            val scanId = historyRepository.saveScan(
                imagePath = imagePath,
                diseaseName = diseaseName,
                diseaseNameHindi = disease?.nameHindi,
                cropName = cropName,
                causeType = causeType,
                confidenceScore = confidence,
                severityLevel = severity
            )

            if (disease != null) {
                val schedule = com.plantcure.ai.util.TreatmentScheduleGenerator.generateSchedule(
                    scanId = scanId.toInt(),
                    disease = disease,
                    severity = severity
                )
                treatmentDao.insertAll(schedule)
                _scheduleCreated.postValue(true)
            }
            hasSaved = true
        }
    }
}
