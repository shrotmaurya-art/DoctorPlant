package com.plantcure.ai.data.local.dao

import androidx.room.*
import com.plantcure.ai.data.local.entity.ScanHistory
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for scan history operations.
 * All queries return Flow for reactive UI updates.
 */
@Dao
interface ScanHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(scan: ScanHistory): Long

    @Update
    suspend fun update(scan: ScanHistory)

    @Delete
    suspend fun delete(scan: ScanHistory)

    @Query("DELETE FROM scan_history WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT * FROM scan_history ORDER BY timestamp DESC")
    fun getAllScans(): Flow<List<ScanHistory>>

    @Query("SELECT * FROM scan_history WHERE id = :id")
    suspend fun getScanById(id: Int): ScanHistory?

    @Query("SELECT * FROM scan_history WHERE disease_name = :name ORDER BY timestamp DESC")
    fun getScansByDisease(name: String): Flow<List<ScanHistory>>

    @Query("SELECT * FROM scan_history WHERE severity_level = :level ORDER BY timestamp DESC")
    fun getScansBySeverity(level: String): Flow<List<ScanHistory>>

    @Query("SELECT * FROM scan_history WHERE cause_type = :causeType ORDER BY timestamp DESC")
    fun getScansByCauseType(causeType: String): Flow<List<ScanHistory>>

    @Query("""
        SELECT * FROM scan_history 
        WHERE disease_name LIKE '%' || :query || '%' 
           OR crop_name LIKE '%' || :query || '%'
           OR location_name LIKE '%' || :query || '%'
        ORDER BY timestamp DESC
    """)
    fun searchScans(query: String): Flow<List<ScanHistory>>

    @Query("""
        SELECT * FROM scan_history 
        WHERE timestamp BETWEEN :startDate AND :endDate 
        ORDER BY timestamp DESC
    """)
    fun getScansByDateRange(startDate: Long, endDate: Long): Flow<List<ScanHistory>>

    @Query("SELECT disease_name FROM scan_history GROUP BY disease_name ORDER BY COUNT(*) DESC LIMIT 1")
    suspend fun getMostCommonDisease(): String?

    @Query("SELECT COUNT(*) FROM scan_history WHERE timestamp > :monthStart")
    suspend fun getScansThisMonth(monthStart: Long): Int

    @Query("SELECT COUNT(*) FROM scan_history")
    suspend fun getTotalScans(): Int

    @Query("SELECT COUNT(*) FROM scan_history WHERE timestamp BETWEEN :weekStart AND :weekEnd")
    suspend fun getWeeklyCount(weekStart: Long, weekEnd: Long): Int

    @Query("SELECT * FROM scan_history WHERE latitude IS NOT NULL ORDER BY timestamp DESC")
    fun getGeoTaggedScans(): Flow<List<ScanHistory>>
}
