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
// OpenAI API (Used by Market)
// ══════════════════════════════════════════════════════

data class OpenAiRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    val max_tokens: Int = 800,
    val temperature: Float = 0.3f
)

data class OpenAiMessage(
    val role: String,
    val content: String
)

data class OpenAiResponse(
    val choices: List<OpenAiChoice>?
)

data class OpenAiChoice(
    val message: OpenAiChoiceMessage?
)

data class OpenAiChoiceMessage(
    val role: String?,
    val content: String?
)

interface OpenAiApiService {
    @Headers("Content-Type: application/json")
    @POST("chat/completions")
    suspend fun getChatCompletion(
        @Header("Authorization") authHeader: String,
        @Body request: OpenAiRequest
    ): Response<OpenAiResponse>
}

// ══════════════════════════════════════════════════════
// Groq API (Used by Chat)
// ══════════════════════════════════════════════════════

data class GroqChatRequest(
    val model: String,
    val messages: List<GroqMessage>,
    val max_tokens: Int
)

data class GroqMessage(
    val role: String,
    val content: String
)

data class GroqResponse(
    val id: String?,
    val `object`: String?,
    val created: Long?,
    val model: String?,
    val choices: List<GroqChoice>?,
    val usage: GroqUsage?
)

data class GroqChoice(
    val index: Int?,
    val message: GroqMessage?,
    @com.google.gson.annotations.SerializedName("finish_reason")
    val finishReason: String?
)



data class GroqUsage(
    @com.google.gson.annotations.SerializedName("prompt_tokens")
    val promptTokens: Int?,
    @com.google.gson.annotations.SerializedName("completion_tokens")
    val completionTokens: Int?,
    @com.google.gson.annotations.SerializedName("total_tokens")
    val totalTokens: Int?
)

interface GroqApiService {
    @POST("chat/completions")
    suspend fun sendMessage(
        @Header("Authorization") auth: String,
        @Body request: GroqChatRequest
    ): Response<GroqResponse>
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
    val records: List<AgmarknetRecord>?,
    val total: Int?,
    val count: Int?,
    val limit: Int?,
    val offset: Int?
)

data class AgmarknetRecord(
    @com.google.gson.annotations.SerializedName("State") val state: String?,
    @com.google.gson.annotations.SerializedName("District") val district: String?,
    @com.google.gson.annotations.SerializedName("Market") val market: String?,
    @com.google.gson.annotations.SerializedName("Commodity") val commodity: String?,
    @com.google.gson.annotations.SerializedName("Min Price") val minPrice: String?,
    @com.google.gson.annotations.SerializedName("Max Price") val maxPrice: String?,
    @com.google.gson.annotations.SerializedName("Modal Price") val modalPrice: String?,
    @com.google.gson.annotations.SerializedName("Price Date") val priceDate: String?
) {
    fun parseMinPrice(): Int = minPrice?.replace(",", "")?.trim()?.toFloatOrNull()?.toInt() ?: 0
    fun parseMaxPrice(): Int = maxPrice?.replace(",", "")?.trim()?.toFloatOrNull()?.toInt() ?: 0
    fun parseModalPrice(): Int = modalPrice?.replace(",", "")?.trim()?.toFloatOrNull()?.toInt() ?: 0
}

interface AgmarknetApiService {
    @GET("resource/9ef84268-d588-465a-a308-a864a43d0070")
    suspend fun getPrices(
        @Query("api-key") apiKey: String,
        @Query("format") format: String = "json",
        @Query("filters[commodity]") commodity: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<AgmarknetResponse>
}
