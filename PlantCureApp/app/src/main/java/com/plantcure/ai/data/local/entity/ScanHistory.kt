package com.plantcure.ai.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for storing plant disease scan history.
 * Each scan from the camera or gallery is auto-saved here.
 */
@Entity(
    tableName = "scan_history",
    indices = [
        Index(value = ["disease_name"]),
        Index(value = ["timestamp"]),
        Index(value = ["crop_name"]),
        Index(value = ["severity_level"])
    ]
)
data class ScanHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "image_path")
    val imagePath: String,               // Path to saved JPEG in internal storage

    @ColumnInfo(name = "disease_name")
    val diseaseName: String,

    @ColumnInfo(name = "disease_name_hindi")
    val diseaseNameHindi: String? = null,

    @ColumnInfo(name = "crop_name")
    val cropName: String,

    @ColumnInfo(name = "cause_type")
    val causeType: String,               // Fungal / Bacterial / Viral / Pest

    @ColumnInfo(name = "confidence_score")
    val confidenceScore: Float,

    @ColumnInfo(name = "severity_level")
    val severityLevel: String,           // Low / Medium / High

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,                 // System.currentTimeMillis()

    @ColumnInfo(name = "latitude")
    val latitude: Double? = null,

    @ColumnInfo(name = "longitude")
    val longitude: Double? = null,

    @ColumnInfo(name = "location_name")
    val locationName: String? = null,    // Human-readable area name

    @ColumnInfo(name = "user_notes")
    val userNotes: String? = null,

    @ColumnInfo(name = "is_shared_to_community")
    val isSharedToCommunity: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
