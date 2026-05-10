package com.plantcure.ai.data.repository

import com.plantcure.ai.data.local.dao.ScanHistoryDao
import com.plantcure.ai.data.local.entity.ScanHistory
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stats data class for the History dashboard.
 */
data class HistoryStats(
    val totalScans: Int,
    val mostCommonDisease: String?,
    val scansThisMonth: Int
)

/**
 * Repository for scan history operations.
 * Wraps ScanHistoryDao with a clean API for ViewModels.
 */
@Singleton
class ScanHistoryRepository @Inject constructor(
    private val scanHistoryDao: ScanHistoryDao
) {
    /**
     * Save a new scan to history.
     * @return The row ID of the inserted scan.
     */
    suspend fun saveScan(
        imagePath: String,
        diseaseName: String,
        diseaseNameHindi: String? = null,
        cropName: String,
        causeType: String,
        confidenceScore: Float,
        severityLevel: String,
        latitude: Double? = null,
        longitude: Double? = null,
        locationName: String? = null
    ): Long {
        val scan = ScanHistory(
            imagePath = imagePath,
            diseaseName = diseaseName,
            diseaseNameHindi = diseaseNameHindi,
            cropName = cropName,
            causeType = causeType,
            confidenceScore = confidenceScore,
            severityLevel = severityLevel,
            timestamp = System.currentTimeMillis(),
            latitude = latitude,
            longitude = longitude,
            locationName = locationName
        )
        return scanHistoryDao.insert(scan)
    }

    fun getAllScans(): Flow<List<ScanHistory>> = scanHistoryDao.getAllScans()

    fun searchScans(query: String): Flow<List<ScanHistory>> = scanHistoryDao.searchScans(query)

    fun getScansByCauseType(causeType: String): Flow<List<ScanHistory>> =
        scanHistoryDao.getScansByCauseType(causeType)

    fun getScansBySeverity(severity: String): Flow<List<ScanHistory>> =
        scanHistoryDao.getScansBySeverity(severity)

    suspend fun getScanById(id: Int): ScanHistory? = scanHistoryDao.getScanById(id)

    suspend fun deleteScan(scan: ScanHistory) = scanHistoryDao.delete(scan)

    suspend fun undoDelete(scan: ScanHistory) = scanHistoryDao.insert(scan)

    suspend fun getStats(): HistoryStats {
        val now = System.currentTimeMillis()
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.DAY_OF_MONTH, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val monthStart = calendar.timeInMillis

        return HistoryStats(
            totalScans = scanHistoryDao.getTotalScans(),
            mostCommonDisease = scanHistoryDao.getMostCommonDisease(),
            scansThisMonth = scanHistoryDao.getScansThisMonth(monthStart)
        )
    }
}
