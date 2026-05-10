package com.plantcure.ai.data.local.dao

import androidx.room.*
import com.plantcure.ai.data.local.entity.TreatmentSchedule
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for treatment schedule operations.
 */
@Dao
interface TreatmentScheduleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(treatments: List<TreatmentSchedule>)

    @Query("SELECT * FROM treatment_schedule WHERE scan_id = :scanId ORDER BY treatment_date ASC")
    fun getScheduleForScan(scanId: Int): Flow<List<TreatmentSchedule>>

    @Query("""
        SELECT * FROM treatment_schedule 
        WHERE treatment_date BETWEEN :fromDate AND :toDate
        ORDER BY treatment_date ASC
    """)
    fun getUpcomingTreatments(fromDate: Long, toDate: Long): Flow<List<TreatmentSchedule>>

    @Query("UPDATE treatment_schedule SET is_completed = 1, completed_at = :completedAt WHERE id = :id")
    suspend fun markCompleted(id: Int, completedAt: Long = System.currentTimeMillis())

    @Query("UPDATE treatment_schedule SET is_skipped = 1 WHERE id = :id")
    suspend fun markSkipped(id: Int)

    @Query("""
        SELECT * FROM treatment_schedule 
        WHERE is_completed = 0 AND is_skipped = 0 AND treatment_date > :now
        ORDER BY treatment_date ASC LIMIT 1
    """)
    suspend fun getNextTreatment(now: Long = System.currentTimeMillis()): TreatmentSchedule?

    @Query("DELETE FROM treatment_schedule WHERE scan_id = :scanId")
    suspend fun deleteByScenId(scanId: Int)
}
