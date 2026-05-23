package com.plantcure.ai.util

import com.plantcure.ai.data.local.entity.TreatmentSchedule
import com.plantcure.ai.domain.model.Disease

object TreatmentScheduleGenerator {

    /**
     * Generate treatment schedule steps based on disease details and detection severity.
     * Rules:
     * - Low severity: 3 organic treatment entries, 7 days apart.
     * - Medium severity: 4 treatment entries (2 organic + 2 chemical), 5 days apart.
     * - High severity: 5 treatment entries (1 organic + 4 chemical), 3 days apart.
     */
    fun generateSchedule(
        scanId: Int,
        disease: Disease,
        severity: String
    ): List<TreatmentSchedule> {
        val steps = mutableListOf<TreatmentSchedule>()
        val baseTime = System.currentTimeMillis()

        val count: Int
        val intervalDays: Int
        val distribution: List<String> // "Organic" or "Chemical"

        when (severity.lowercase()) {
            "high" -> {
                count = 5
                intervalDays = 3
                distribution = listOf("Organic", "Chemical", "Chemical", "Chemical", "Chemical")
            }
            "medium" -> {
                count = 4
                intervalDays = 5
                distribution = listOf("Organic", "Chemical", "Organic", "Chemical")
            }
            else -> { // low
                count = 3
                intervalDays = 7
                distribution = listOf("Organic", "Organic", "Organic")
            }
        }

        for (i in 0 until count) {
            val treatmentDate = baseTime + (i.toLong() * intervalDays * 24 * 60 * 60 * 1000)
            val type = distribution.getOrElse(i) { "Organic" }

            val description = if (type == "Organic") {
                disease.preventionOrganic.firstOrNull() ?: "Apply organic neem oil spray and monitor crop."
            } else {
                disease.preventionChemical.firstOrNull() ?: "Apply recommended chemical fungicide/pesticide."
            }

            val chemicalName = if (type == "Chemical") {
                disease.preventionChemical.firstOrNull()?.split(" ")?.firstOrNull() ?: "Fungicide"
            } else null

            steps.add(
                TreatmentSchedule(
                    scanId = scanId,
                    diseaseName = disease.name,
                    cropName = disease.affectedCrop,
                    treatmentDate = treatmentDate,
                    actionDescription = "Step ${i + 1}: $description",
                    treatmentType = type,
                    chemicalName = chemicalName,
                    dosage = if (type == "Chemical") disease.dosageSafety else null,
                    isCompleted = false,
                    isSkipped = false
                )
            )
        }

        return steps
    }
}
