package com.plantcure.ai.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.plantcure.ai.domain.model.Disease
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for plant disease data loaded from diseases.json in assets.
 * Provides lookup by TFLite classifier label.
 * Data is loaded lazily on first access and cached in memory.
 */
@Singleton
class DiseaseRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var diseases: List<Disease>? = null
    private var diseaseMap: Map<String, Disease>? = null

    /**
     * Map from TFLite output labels to standard diseases.json IDs.
     */
    private val LABEL_TO_ID = mapOf(
        "Pepper__bell___Bacterial_spot" to "Pepper,_bell___Bacterial_spot",
        "Pepper__bell___healthy" to "Pepper,_bell___healthy",
        "Potato___Early_blight" to "Potato___Early_blight",
        "Potato___Late_blight" to "Potato___Late_blight",
        "Potato___healthy" to "Potato___healthy",
        "Tomato_Bacterial_spot" to "Tomato___Bacterial_spot",
        "Tomato_Early_blight" to "Tomato___Early_blight",
        "Tomato_Late_blight" to "Tomato___Late_blight",
        "Tomato_Leaf_Mold" to "Tomato___Leaf_Mold",
        "Tomato_Septoria_leaf_spot" to "Tomato___Septoria_leaf_spot",
        "Tomato_Spider_mites_Two_spotted_spider_mite" to "Tomato___Spider_mites_Two-spotted_spider_mite",
        "Tomato__Target_Spot" to "Tomato___Target_Spot",
        "Tomato__Tomato_YellowLeaf__Curl_Virus" to "Tomato___Tomato_Yellow_Leaf_Curl_Virus",
        "Tomato__Tomato_mosaic_virus" to "Tomato___Tomato_mosaic_virus",
        "Tomato_healthy" to "Tomato___healthy",
        "rice Bacterial leaf blight" to "Rice___Bacterial_leaf_blight",
        "rice Brown spot" to "Rice___Brown_spot",
        "rice Leaf smut" to "Rice___Leaf_smut"
    )

    /**
     * Get all diseases from the database.
     */
    fun getAllDiseases(): List<Disease> {
        ensureLoaded()
        return diseases ?: emptyList()
    }

    /**
     * Look up a disease by its TFLite classifier label.
     * E.g., "Tomato_Late_blight" → Disease object with full info.
     */
    fun getDiseaseByLabel(label: String): Disease? {
        ensureLoaded()
        val mappedId = LABEL_TO_ID[label] ?: label
        return diseaseMap?.get(mappedId)
    }

    /**
     * Extract crop name from a classifier label.
     * E.g., "Tomato_Late_blight" → "Tomato"
     */
    fun getCropName(label: String): String {
        val mappedId = LABEL_TO_ID[label] ?: label
        val parts = mappedId.split("___")
        return parts.firstOrNull()
            ?.replace("_", " ")
            ?.replace("(", "(")
            ?.replace(")", ")")
            ?.trim()
            ?: label
    }

    /**
     * Check if a label represents a healthy plant (no disease).
     */
    fun isHealthy(label: String): Boolean {
        return label.lowercase().contains("healthy")
    }

    /**
     * Format a raw label into a human-readable disease name.
     * E.g., "Tomato___Late_blight" → "Late Blight"
     */
    fun getDisplayName(label: String): String {
        if (isHealthy(label)) return "Healthy"
        val disease = getDiseaseByLabel(label)
        if (disease != null) return disease.name
        // Fallback: parse from label
        val parts = label.split("___")
        return if (parts.size > 1) {
            parts[1].replace("_", " ")
        } else {
            label.replace("_", " ")
        }
    }

    @Synchronized
    private fun ensureLoaded() {
        if (diseases != null) return
        try {
            val json = context.assets.open("diseases.json")
                .bufferedReader()
                .use { it.readText() }
            val type = object : TypeToken<List<Disease>>() {}.type
            val list: List<Disease> = Gson().fromJson(json, type)
            diseases = list
            diseaseMap = list.associateBy { it.id }
        } catch (e: Exception) {
            e.printStackTrace()
            diseases = emptyList()
            diseaseMap = emptyMap()
        }
    }
}
