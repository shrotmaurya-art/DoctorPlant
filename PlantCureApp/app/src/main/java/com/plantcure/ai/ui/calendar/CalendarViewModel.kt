package com.plantcure.ai.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantcure.ai.data.local.dao.TreatmentScheduleDao
import com.plantcure.ai.data.local.entity.TreatmentSchedule
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Treatment Schedule screen.
 * Reads treatment data from Room and exposes it reactively.
 */
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val treatmentDao: TreatmentScheduleDao
) : ViewModel() {

    private val now = System.currentTimeMillis()
    private val thirtyDaysLater = now + (30L * 24 * 60 * 60 * 1000)

    /**
     * All upcoming treatments for the next 30 days, sorted by date ascending.
     */
    val treatments: StateFlow<List<TreatmentSchedule>> =
        treatmentDao.getUpcomingTreatments(now - (7L * 24 * 60 * 60 * 1000), thirtyDaysLater)
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    /**
     * Mark a treatment step as completed.
     */
    fun markCompleted(treatmentId: Int) {
        viewModelScope.launch {
            treatmentDao.markCompleted(treatmentId)
        }
    }

    /**
     * Mark a treatment step as skipped.
     */
    fun markSkipped(treatmentId: Int) {
        viewModelScope.launch {
            treatmentDao.markSkipped(treatmentId)
        }
    }

    /**
     * Get the next upcoming treatment (for the "Next Action" card).
     */
    private val _nextTreatment = MutableStateFlow<TreatmentSchedule?>(null)
    val nextTreatment: StateFlow<TreatmentSchedule?> = _nextTreatment

    init {
        viewModelScope.launch {
            _nextTreatment.value = treatmentDao.getNextTreatment()
        }
    }
}
