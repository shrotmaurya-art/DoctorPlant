package com.plantcure.ai.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for crop treatment schedule items.
 * Each disease scan generates a set of scheduled treatments.
 */
@Entity(
    tableName = "treatment_schedule",
    indices = [
        Index(value = ["scan_id"]),
        Index(value = ["treatment_date"])
    ]
)
data class TreatmentSchedule(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "scan_id")
    val scanId: Int,                     // Links to ScanHistory

    @ColumnInfo(name = "disease_name")
    val diseaseName: String,

    @ColumnInfo(name = "crop_name")
    val cropName: String,

    @ColumnInfo(name = "treatment_date")
    val treatmentDate: Long,             // Epoch millis of treatment day

    @ColumnInfo(name = "action_description")
    val actionDescription: String,       // "Apply neem oil spray"

    @ColumnInfo(name = "treatment_type")
    val treatmentType: String,           // "Organic" or "Chemical"

    @ColumnInfo(name = "chemical_name")
    val chemicalName: String? = null,

    @ColumnInfo(name = "dosage")
    val dosage: String? = null,

    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean = false,

    @ColumnInfo(name = "is_skipped")
    val isSkipped: Boolean = false,

    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null,

    @ColumnInfo(name = "notes")
    val notes: String? = null
)
