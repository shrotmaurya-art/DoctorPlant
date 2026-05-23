package com.plantcure.ai.data.repository

import com.plantcure.ai.BuildConfig
import com.plantcure.ai.data.local.dao.MarketPriceDao
import com.plantcure.ai.data.local.entity.MarketPrice
import com.plantcure.ai.data.remote.OpenAiApiService
import com.plantcure.ai.data.remote.OpenAiRequest
import com.plantcure.ai.data.remote.OpenAiMessage
import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for Mandi market prices.
 * Uses ChatGPT API to generate realistic location-aware prices,
 * caches to Room for offline access.
 */
@Singleton
class MarketRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val openAiApi: OpenAiApiService,
    private val marketPriceDao: MarketPriceDao
) {
    private val CACHE_DURATION_MS = 6 * 60 * 60 * 1000L // 6 hours

    /**
     * Get cached prices filtered by commodity only.
     */
    fun getPricesForCommodity(commodity: String): Flow<List<MarketPrice>> {
        return marketPriceDao.getPricesForCommodity(commodity)
    }

    /**
     * Get cached prices filtered by commodity AND state.
     */
    fun getPricesForCommodityAndState(commodity: String, state: String): Flow<List<MarketPrice>> {
        return marketPriceDao.getPricesForCommodityAndState(commodity, state)
    }

    /**
     * Get cached prices filtered by commodity, state AND district.
     */
    fun getPricesForCommodityStateAndDistrict(commodity: String, state: String, district: String): Flow<List<MarketPrice>> {
        return marketPriceDao.getPricesForCommodityStateAndDistrict(commodity, state, district)
    }

    /**
     * Parses the comprehensive list of states and districts from assets.
     */
    fun getStatesAndDistricts(): Map<String, List<String>> {
        return try {
            val inputStream = context.assets.open("india_states_districts.json")
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()
            val jsonString = String(buffer, Charsets.UTF_8)
            
            // The JSON has { "states": [ { "state": "Name", "districts": ["D1", "D2"] } ] }
            val root = org.json.JSONObject(jsonString)
            val statesArray = root.getJSONArray("states")
            
            val map = mutableMapOf<String, List<String>>()
            for (i in 0 until statesArray.length()) {
                val stateObj = statesArray.getJSONObject(i)
                val stateName = stateObj.getString("state")
                val districtsArray = stateObj.getJSONArray("districts")
                val districtsList = mutableListOf<String>()
                districtsList.add("All Districts")
                for (j in 0 until districtsArray.length()) {
                    districtsList.add(districtsArray.getString(j))
                }
                map[stateName] = districtsList
            }
            map
        } catch (e: Exception) {
            e.printStackTrace()
            emptyMap()
        }
    }

    suspend fun refreshPrices(
        commodity: String,
        state: String? = null,
        district: String? = null
    ): Boolean {
        val targetState = state ?: "Maharashtra"

        // Normalize district name for GPS → Agmarknet mapping
        val normalizedDistrict = if (district != null && district != "All Districts") {
            normalizeDistrict(district)
        } else {
            district
        }
        
        android.util.Log.d("PlantCure_Market", "refreshPrices: commodity=$commodity, state=$targetState, district=$district, normalized=$normalizedDistrict")

        // Check if cache is fresh enough for this commodity+state combo (4 hours)
        val lastCache = marketPriceDao.getLastCacheTimeForState(commodity, targetState)
        val cacheDuration4Hrs = 4 * 60 * 60 * 1000L
        if (lastCache != null && System.currentTimeMillis() - lastCache < cacheDuration4Hrs) {
            android.util.Log.d("PlantCure_Market", "Using cached data (age: ${(System.currentTimeMillis() - lastCache) / 60000}min)")
            return true
        }

        val apiKey = BuildConfig.OPENAI_API_KEY
        if (apiKey.isBlank() || apiKey.startsWith("your_")) {
            android.util.Log.e("PlantCure_Market", "OpenAI API key missing or placeholder")
            return false
        }

        return try {
            val targetDistrict = normalizedDistrict ?: "All Districts"
            android.util.Log.d("PlantCure_Market", "Requesting prices from OpenAI for: $commodity in $targetDistrict, $targetState")
            val prompt = """
                You are an agricultural market data API. Provide current, realistic APMC (Mandi) market prices in India.
                Return ONLY a JSON array of objects. No markdown formatting, no code blocks, just raw JSON.
                Commodity: $commodity
                State: $targetState
                District: $targetDistrict

                Generate 3 to 5 realistic market records.
                If the district is "All Districts", generate major markets across the state.
                If a specific district is provided, generate realistic APMC names for that specific district.
                Each JSON object must have:
                - market (String: name of the APMC or Mandi)
                - district (String: the district name)
                - state (String: the state name)
                - commodity (String)
                - minPrice (Number: price in INR per kg)
                - maxPrice (Number: price in INR per kg)
                - modalPrice (Number: price in INR per kg)
                - trend (String: "up", "down", or "stable")
            """.trimIndent()

            val request = OpenAiRequest(
                model = "gpt-3.5-turbo",
                messages = listOf(
                    OpenAiMessage(role = "system", content = "You return only JSON arrays. No explanation."),
                    OpenAiMessage(role = "user", content = prompt)
                )
            )

            val response = openAiApi.getChatCompletion(
                authHeader = "Bearer $apiKey",
                request = request
            )

            if (response.isSuccessful) {
                val content = response.body()?.choices?.firstOrNull()?.message?.content
                android.util.Log.d("PlantCure_Market", "API response content length: ${content?.length ?: 0}")
                if (content != null) {
                    val cleanJson = content.removePrefix("```json").removeSuffix("```").trim()
                    
                    val listType = object : TypeToken<List<Map<String, Any>>>() {}.type
                    val records: List<Map<String, Any>> = Gson().fromJson(cleanJson, listType)
                    android.util.Log.d("PlantCure_Market", "Parsed ${records.size} market records")
                    
                    if (records.isNotEmpty()) {
                        val today = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date())
                        val entities = records.map { record ->
                            MarketPrice(
                                market = record["market"].toString(),
                                district = record["district"].toString(),
                                state = record["state"].toString(),
                                commodity = record["commodity"].toString(),
                                minPrice = (record["minPrice"] as? Double)?.toFloat() ?: 0f,
                                maxPrice = (record["maxPrice"] as? Double)?.toFloat() ?: 0f,
                                modalPrice = (record["modalPrice"] as? Double)?.toFloat() ?: 0f,
                                priceDate = today,
                                trend = record["trend"]?.toString() ?: "stable"
                            )
                        }
                        marketPriceDao.insertAll(entities)
                        return true
                    }
                }
            } else {
                android.util.Log.e("PlantCure_Market", "API Error: ${response.code()} ${response.errorBody()?.string()}")
            }
            return false
        } catch (e: Exception) {
            android.util.Log.e("PlantCure_Market", "Exception: ${e.message}", e)
            return false
        }
    }

    /**
     * Normalizes common spelling variations of Indian districts to match Agmarknet.
     * First checks the GPS-to-Agmarknet map, then checks known spelling variations.
     */
    private fun normalizeDistrict(district: String): String {
        // Try GPS-to-Agmarknet mapper first (handles Virar→Palghar etc.)
        val mapped = DistrictNameMapper.normalize(district)
        if (mapped != district) {
            android.util.Log.d("PlantCure_Market", "DistrictNameMapper: '$district' → '$mapped'")
            return mapped
        }
        
        val d = district.trim().lowercase()
        return when (d) {
            "nasik" -> "Nashik"
            "bengaluru", "bangalore" -> "Bangalore"
            "mysuru", "mysore" -> "Mysore"
            "belagavi", "belgaum" -> "Belgaum"
            "tumakuru", "tumkur" -> "Tumkur"
            "gurugram", "gurgaon" -> "Gurgaon"
            "prayagraj", "allahabad" -> "Allahabad"
            "varanasi", "banaras" -> "Varanasi"
            "kanpur nagar", "kanpur" -> "Kanpur"
            "pune", "poona" -> "Pune"
            "mumbai", "bombay" -> "Mumbai"
            "chennai", "madras" -> "Chennai"
            "thiruvananthapuram", "trivandrum" -> "Thiruvananthapuram"
            "kochi", "cochin", "ernakulam" -> "Ernakulam"
            "gautam buddha nagar", "noida" -> "Gautam Buddha Nagar"
            else -> district
        }
    }

    /**
     * Clear old cached prices.
     */
    suspend fun clearOldCache() {
        val cutoff = System.currentTimeMillis() - (4 * 60 * 60 * 1000L) // 4 hours
        marketPriceDao.deleteOldCache(cutoff)
    }


}
