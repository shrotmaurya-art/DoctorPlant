package com.plantcure.ai.util

import kotlin.math.*

/**
 * Pure Kotlin disease risk calculator based on weather conditions.
 * No Android imports — fully unit-testable.
 */
object RiskCalculator {

    /**
     * Calculate disease risk level from weather data.
     *
     * Rules:
     * - HIGH: humidity > 80% OR (rain > 0mm AND temp 15-28°C) — peak fungal conditions
     * - MEDIUM: humidity 60-80% OR temp outside 10-32°C range
     * - LOW: all other conditions
     */
    fun calculateRisk(
        tempCelsius: Float,
        humidityPercent: Int,
        rainMmPerHour: Float = 0f
    ): com.plantcure.ai.domain.model.RiskLevel {
        return when {
            humidityPercent > 80 -> com.plantcure.ai.domain.model.RiskLevel.HIGH
            rainMmPerHour > 0f && tempCelsius in 15.0f..28.0f -> com.plantcure.ai.domain.model.RiskLevel.HIGH
            humidityPercent in 60..80 -> com.plantcure.ai.domain.model.RiskLevel.MEDIUM
            tempCelsius !in 10.0f..32.0f -> com.plantcure.ai.domain.model.RiskLevel.MEDIUM
            else -> com.plantcure.ai.domain.model.RiskLevel.LOW
        }
    }

    /**
     * Get a farmer-friendly risk message.
     */
    fun getRiskMessage(
        riskLevel: com.plantcure.ai.domain.model.RiskLevel,
        tempCelsius: Float,
        humidityPercent: Int
    ): String {
        return when (riskLevel) {
            com.plantcure.ai.domain.model.RiskLevel.HIGH ->
                "High fungal disease risk today. Humidity $humidityPercent% and ${tempCelsius.toInt()}°C. Check your crops immediately."
            com.plantcure.ai.domain.model.RiskLevel.MEDIUM ->
                "Moderate disease risk. Keep monitoring your crops for early symptoms."
            com.plantcure.ai.domain.model.RiskLevel.LOW ->
                "Low disease risk today. Good conditions for your crops."
        }
    }

    /**
     * Calculate Haversine distance between two GPS coordinates in kilometers.
     * Used for filtering community posts by proximity.
     */
    fun haversineDistanceKm(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusKm * c
    }
}
