package com.plantcure.ai.data.repository

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.plantcure.ai.data.local.ApiKeyManager
import com.plantcure.ai.data.local.entity.MarketPrice
import com.plantcure.ai.data.remote.OpenAiClient
import com.plantcure.ai.data.remote.OpenAiMessage
import com.plantcure.ai.data.remote.OpenAiRequest
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException

@Singleton
class MarketRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    // In-memory cache: crop → (timestamp, prices)
    private val cache = mutableMapOf<String, Pair<Long, List<MarketPrice>>>()

    suspend fun getPrices(
        commodity: String,
        state: String = "Maharashtra"
    ): Result<List<MarketPrice>> {

        Log.d("MKT", "getPrices called: $commodity")

        // 1. Return cache if fresh (< 1 hour)
        cache[commodity]?.let { (time, prices) ->
            val age = System.currentTimeMillis() - time
            if (age < 3_600_000L && prices.isNotEmpty()) {
                Log.d("MKT", "Cache hit: ${prices.size}")
                return Result.success(prices)
            }
        }

        // 2. Get API key
        val groqKey = ApiKeyManager.getGroqKey()
        if (groqKey.isNullOrBlank()) {
            Log.e("MKT", "No Groq key found")
            return Result.failure(
                Exception("no_key")
            )
        }
        Log.d("MKT", "Key length: ${groqKey.length}")

        // 3. Build simple prompt
        val prompt = "Give me 5 realistic Indian " +
            "wholesale mandi prices for $commodity " +
            "in $state today. Return ONLY a JSON " +
            "array starting with [ and ending with ] " +
            "No markdown. No explanation. " +
            "Each object must have exactly these " +
            "fields: market, district, state, " +
            "minPrice, maxPrice, modalPrice, trend. " +
            "trend must be up, down, or stable. " +
            "Prices in rupees per quintal."

        Log.d("MKT", "Calling Groq...")

        return try {
            withTimeout(25_000L) {
                // 4. Call Groq
                Log.d("MKT", "About to call API...")
                val response = com.plantcure.ai.data.remote.GroqClient.api
                    .sendMessage(
                        auth = "Bearer $groqKey",
                        request = com.plantcure.ai.data.remote.GroqChatRequest(
                            model = "llama-3.3-70b-versatile",
                            messages = listOf(
                                com.plantcure.ai.data.remote.GroqMessage(
                                    role = "user",
                                    content = prompt
                                )
                            ),
                            max_tokens = 600
                        )
                    )

                Log.d("MKT", "API returned: ${response.code()}")

                if (!response.isSuccessful) {
                    val err = response.errorBody()
                        ?.string() ?: "unknown"
                    Log.e("MKT", "API error: $err")
                    return@withTimeout Result.failure(
                        Exception("Error ${response.code()}")
                    )
                }

                // 5. Get raw content
                Log.d("MKT", "Getting body...")
                val body = response.body()
                Log.d("MKT", "Body null: ${body == null}")

                Log.d("MKT", "Getting choices...")
                val raw = body?.choices?.firstOrNull()
                    ?.message?.content?.trim()
                Log.d("MKT", "Raw null: ${raw == null}")
                Log.d("MKT", "Raw value: $raw")

                if (raw.isNullOrBlank()) {
                    Log.e("MKT", "Empty response")
                    return@withTimeout Result.failure(
                        Exception("Empty response")
                    )
                }

                // 6. Clean and parse JSON
                val clean = raw
                    .replace("```json", "")
                    .replace("```", "")
                    .trim()

                Log.d("MKT", "Clean JSON: $clean")

                val today = SimpleDateFormat(
                    "dd/MM/yyyy",
                    Locale.getDefault()
                ).format(Date())

                val mapType = object : TypeToken<List<Map<String, Any>>>() {}.type
                val maps: List<Map<String, Any>> =
                    Gson().fromJson(clean, mapType)

                Log.d("MKT", "Parsed ${maps.size} records")

                val prices = maps.map { m ->
                    MarketPrice(
                        market = str(m, "market", "Local Mandi"),
                        district = str(m, "district", "Unknown"),
                        state = str(m, "state", state),
                        commodity = commodity,
                        minPrice = num(m, "minPrice", "min_price", "min"),
                        maxPrice = num(m, "maxPrice", "max_price", "max"),
                        modalPrice = num(m, "modalPrice", "modal_price", "modal", "price"),
                        priceDate = today,
                        trend = str(m, "trend", "stable")
                    )
                }

                if (prices.isEmpty()) {
                    return@withTimeout Result.failure(
                        Exception("No prices parsed")
                    )
                }

                // 7. Save to cache
                cache[commodity] = Pair(
                    System.currentTimeMillis(),
                    prices
                )

                Log.d("MKT", "Success: ${prices.size}")
                Result.success(prices)
            }
        } catch (e: TimeoutCancellationException) {
            Log.e("MKT", "Request timed out!")
            Result.failure(Exception("Request timed out. Try again."))
        } catch (e: Exception) {
            Log.e("MKT", "Exception: ${e.message}")
            Result.failure(e)
        }
    }

    // Helper: get String from map with default fallback
    private fun str(
        map: Map<String, Any>,
        key: String,
        default: String
    ): String = map[key]?.toString() ?: default

    // Helper: get Float from map with fallbacks
    private fun num(
        map: Map<String, Any>,
        vararg keys: String
    ): Float {
        for (key in keys) {
            val v = map[key]
            if (v != null) {
                return (v as? Number)?.toFloat()
                    ?: v.toString()
                        .replace(",", "")
                        .toFloatOrNull()
                    ?: 0f
            }
        }
        return 0f
    }

    fun getStatesAndDistricts(): Map<String, List<String>> {
        return try {
            val inputStream = context.assets.open("india_states_districts.json")
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()
            val jsonString = String(buffer, Charsets.UTF_8)
            
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
}
