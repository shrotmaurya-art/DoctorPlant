package com.plantcure.ai.data.repository

object DistrictNameMapper {
    
    // Maps GPS district name → Agmarknet/Standard district name
    val districtMap = mapOf(
        // Maharashtra
        "Virar" to "Palghar",
        "Vasai" to "Palghar", 
        "Mira Road" to "Thane",
        "Bhiwandi" to "Thane",
        "Kalyan" to "Thane",
        "Dombivli" to "Thane",
        "Panvel" to "Raigad",
        "Kharghar" to "Raigad",
        "Navi Mumbai" to "Thane",
        "Aurangabad" to "Chhatrapati Sambhajinagar",
        "Ahmednagar" to "Nagar"
    )
    
    fun normalize(gpsDistrictName: String): String {
        // Check direct match first
        districtMap[gpsDistrictName]?.let { return it }
        
        // Check case-insensitive match
        districtMap.entries.find { 
            it.key.equals(gpsDistrictName, ignoreCase = true) 
        }?.let { return it.value }
        
        // Return original if no mapping found
        return gpsDistrictName
    }
}
