package com.plantcure.ai.di

import android.content.Context
import androidx.room.Room
import com.plantcure.ai.data.local.PlantCureDatabase
import com.plantcure.ai.data.local.dao.MarketPriceDao
import com.plantcure.ai.data.local.dao.ScanHistoryDao
import com.plantcure.ai.data.local.dao.TreatmentScheduleDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing Room database and DAO instances.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PlantCureDatabase {
        return Room.databaseBuilder(
            context,
            PlantCureDatabase::class.java,
            "plantcure_db"
        )
            // Add migrations here as schema evolves:
            // .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
    }

    @Provides
    fun provideScanHistoryDao(database: PlantCureDatabase): ScanHistoryDao {
        return database.scanHistoryDao()
    }

    @Provides
    fun provideMarketPriceDao(database: PlantCureDatabase): MarketPriceDao {
        return database.marketPriceDao()
    }

    @Provides
    fun provideTreatmentScheduleDao(database: PlantCureDatabase): TreatmentScheduleDao {
        return database.treatmentScheduleDao()
    }
}
