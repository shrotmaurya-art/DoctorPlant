package com.plantcure.ai.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.plantcure.ai.data.local.dao.MarketPriceDao
import com.plantcure.ai.data.local.dao.ScanHistoryDao
import com.plantcure.ai.data.local.dao.TreatmentScheduleDao
import com.plantcure.ai.data.local.entity.MarketPrice
import com.plantcure.ai.data.local.entity.ScanHistory
import com.plantcure.ai.data.local.entity.TreatmentSchedule

/**
 * PlantCure AI Room Database.
 *
 * Version history:
 *   1 - Initial schema: scan_history, market_prices, treatment_schedule
 *
 * IMPORTANT: Never use fallbackToDestructiveMigration() in production!
 * Always write proper Migration objects when adding/changing columns.
 */
@Database(
    entities = [
        ScanHistory::class,
        MarketPrice::class,
        TreatmentSchedule::class
    ],
    version = 1,
    exportSchema = true
)
abstract class PlantCureDatabase : RoomDatabase() {

    abstract fun scanHistoryDao(): ScanHistoryDao
    abstract fun marketPriceDao(): MarketPriceDao
    abstract fun treatmentScheduleDao(): TreatmentScheduleDao
}
