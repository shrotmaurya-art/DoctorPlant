package com.plantcure.ai.domain.model

/**
 * Domain model for a plant disease loaded from diseases.json.
 * This is the app's core data model — used across all screens.
 */
data class Disease(
    val id: String,
    val name: String,
    val nameHindi: String = "",
    val affectedCrop: String,
    val affectedCropHindi: String = "",
    val causeType: String,                  // Fungal / Bacterial / Viral / Pest
    val weatherTrigger: String,
    val about: String,
    val symptoms: List<String>,
    val preventionOrganic: List<String>,
    val preventionChemical: List<String>,
    val dosageSafety: String,
    val severityDefault: String,            // Low / Medium / High
    val yieldLoss: String,
    val marketImpact: String,
    val recoveryWeeks: Int = 3
)

/**
 * Detection result from the TFLite classifier.
 */
data class DetectionResult(
    val label: String,
    val confidence: Float,
    val severity: String                    // Computed from confidence: High/Medium/Low
) {
    companion object {
        fun computeSeverity(confidence: Float): String = when {
            confidence >= 0.85f -> "High"
            confidence >= 0.60f -> "Medium"
            else -> "Low"
        }
    }
}

/**
 * Weather data model for the home screen risk card.
 */
data class WeatherData(
    val temperature: Float,
    val humidity: Int,
    val windSpeed: Float,
    val description: String,
    val cityName: String,
    val riskLevel: RiskLevel,
    val riskMessage: String,
    val lastUpdated: Long = System.currentTimeMillis()
)

/**
 * Disease risk level based on weather conditions.
 */
enum class RiskLevel {
    LOW, MEDIUM, HIGH;

    val displayColor: String
        get() = when (this) {
            LOW -> "#2E7D32"
            MEDIUM -> "#F9A825"
            HIGH -> "#B71C1C"
        }
}

/**
 * Chat message for the AI chatbot.
 */
data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: String,                       // "user" or "assistant"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isLoading: Boolean = false
)

/**
 * Community disease report for the feed.
 */
data class CommunityPost(
    val postId: String = "",
    val anonymousUserId: String = "",
    val diseaseName: String = "",
    val cropName: String = "",
    val causeType: String = "",
    val severityLevel: String = "",
    val latitude: Double = 0.0,             // Rounded to ~1km accuracy for privacy
    val longitude: Double = 0.0,
    val districtName: String = "",
    val stateName: String = "",
    val timestamp: Long = 0L
)

/**
 * Nearby shop from Google Places API.
 */
data class ShopResult(
    val placeId: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val rating: Float = 0f,
    val userRatingsTotal: Int = 0,
    val phoneNumber: String? = null,
    val isOpen: Boolean? = null,
    val distanceKm: Float = 0f
)
