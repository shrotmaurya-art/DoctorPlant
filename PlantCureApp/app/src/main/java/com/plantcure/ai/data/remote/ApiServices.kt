package com.plantcure.ai.data.remote

import retrofit2.Response
import retrofit2.http.*

// ══════════════════════════════════════════════════════
// Claude (Anthropic) API
// ══════════════════════════════════════════════════════

data class ClaudeRequest(
    val model: String = "claude-sonnet-4-20250514",
    val max_tokens: Int = 400,
    val system: String,
    val messages: List<ClaudeMessage>
)

data class ClaudeMessage(
    val role: String,       // "user" or "assistant"
    val content: String
)

data class ClaudeResponse(
    val content: List<ClaudeContent>,
    val usage: ClaudeUsage
)

data class ClaudeContent(
    val type: String,
    val text: String
)

data class ClaudeUsage(
    val input_tokens: Int,
    val output_tokens: Int
)

interface ClaudeApiService {
    @POST("v1/messages")
    @Headers("anthropic-version: 2023-06-01", "content-type: application/json")
    suspend fun sendMessage(
        @Header("x-api-key") apiKey: String,
        @Body request: ClaudeRequest
    ): Response<ClaudeResponse>
}

// ══════════════════════════════════════════════════════
// OpenWeatherMap API
// ══════════════════════════════════════════════════════

data class WeatherResponse(
    val main: WeatherMain,
    val weather: List<WeatherCondition>,
    val wind: WeatherWind,
    val name: String,
    val rain: WeatherRain? = null
)

data class WeatherMain(val temp: Float, val humidity: Int)
data class WeatherCondition(val main: String, val description: String, val icon: String)
data class WeatherWind(val speed: Float)
data class WeatherRain(val `1h`: Float? = null)

interface WeatherApiService {
    @GET("data/2.5/weather")
    suspend fun getCurrentWeather(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric"
    ): Response<WeatherResponse>
}

// ══════════════════════════════════════════════════════
// Agmarknet (Indian Government Market Prices) API
// ══════════════════════════════════════════════════════

data class AgmarknetResponse(
    val records: List<AgmarknetRecord>
)

data class AgmarknetRecord(
    val state: String,
    val district: String,
    val market: String,
    val commodity: String,
    val min_price: String,
    val max_price: String,
    val modal_price: String,
    val arrival_date: String? = null
) {
    /** Parse price string, handling commas (e.g., "1,500" → 1500f) */
    fun parseMinPrice(): Float = min_price.replace(",", "").toFloatOrNull() ?: 0f
    fun parseMaxPrice(): Float = max_price.replace(",", "").toFloatOrNull() ?: 0f
    fun parseModalPrice(): Float = modal_price.replace(",", "").toFloatOrNull() ?: 0f
}

interface AgmarknetApiService {
    @GET("resource/9ef84268-d588-465a-a308-a864a43d0070")
    suspend fun getMarketPrices(
        @Query("api-key") apiKey: String,
        @Query("format") format: String = "json",
        @Query("filters[commodity]") commodity: String,
        @Query("filters[state]") state: String? = null,
        @Query("limit") limit: Int = 30,
        @Query("offset") offset: Int = 0
    ): Response<AgmarknetResponse>
}
