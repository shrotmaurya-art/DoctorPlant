package com.plantcure.ai.data.repository

import android.util.Log
import com.google.gson.Gson
import com.plantcure.ai.data.local.ApiKeyManager
import com.plantcure.ai.data.remote.GroqApiService
import com.plantcure.ai.data.remote.GroqChatRequest
import com.plantcure.ai.data.remote.GroqMessage
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for AI chat using Groq (primary) or OpenAI (fallback).
 * Dynamically selects the provider based on which key the user has saved.
 */
@Singleton
class ChatRepository @Inject constructor() {

    companion object {
        private const val TAG = "PlantCure_Chat"
        private const val DEBUG_TAG = "Chat_Debug"
    }

    /**
     * Determines which API config to use.
     * Only uses Groq as per user configuration.
     */
    fun getApiConfig(): Pair<String, String> {
        val key = ApiKeyManager.getGroqKey() 
            ?: throw IllegalStateException("No Groq API key available")
        return Pair(key, "https://api.groq.com/openai/v1/")
    }

    fun getModelName(): String {
        return "llama-3.3-70b-versatile"
    }

    fun getProviderName(): String {
        return if (ApiKeyManager.hasGroqKey()) "Groq LLaMA3" else "No key"
    }

    private fun buildApiService(baseUrl: String, apiKey: String): GroqApiService {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GroqApiService::class.java)
    }

    private fun buildSafeMessages(
        systemPrompt: String,
        history: List<Pair<String, String>>
    ): List<GroqMessage> {
        val messages = mutableListOf<GroqMessage>()

        if (systemPrompt.isNotBlank()) {
            messages.add(GroqMessage("system", systemPrompt.trim()))
        }

        val filtered = history.filter { it.second.isNotBlank() }

        val fixedHistory = if (filtered.firstOrNull()?.first?.lowercase() == "assistant" || filtered.firstOrNull()?.first?.lowercase() == "model") {
            filtered.drop(1)
        } else filtered

        val alternating = mutableListOf<Pair<String, String>>()
        var lastRole = "system"
        fixedHistory.forEach { (roleRaw, text) ->
            val role = if (roleRaw.lowercase() == "assistant" || roleRaw.lowercase() == "model") "assistant" else "user"
            if (role != lastRole) {
                alternating.add(Pair(role, text))
                lastRole = role
            }
        }

        val trimmed = if (alternating.size > 20) alternating.takeLast(20) else alternating
        val finalHistory = if (trimmed.firstOrNull()?.first == "assistant") trimmed.drop(1) else trimmed

        finalHistory.forEach { (role, content) ->
            messages.add(GroqMessage(role, content.trim()))
        }

        return messages
    }

    /**
     * Send a message to the AI (Groq or OpenAI) and get a response.
     * Automatically selects provider based on saved keys.
     */
    fun buildSystemPrompt(
        diseaseName: String,
        cropName: String,
        severity: String
    ): String = """
You are AgriBot, a knowledgeable and 
friendly agricultural assistant for 
Indian farmers.

${if (diseaseName != "General Inquiry" && 
     diseaseName != "Unknown" && 
     diseaseName != "Unknown Disease") 
  "SCAN CONTEXT: The user's $cropName was " +
  "diagnosed with $diseaseName " +
  "($severity severity)." 
  else ""}

YOU CAN ANSWER ANYTHING ABOUT:
- Plant diseases and treatments
- Crop growing tips and best practices  
- Soil health and fertilizers
- Pesticides and organic remedies
- Weather and its effect on crops
- Irrigation and water management
- Seeds and varieties
- Harvesting and post-harvest
- Market prices and selling tips
- Government schemes for farmers
- Any general farming questions

YOUR STYLE:
- Friendly and practical
- Simple language a farmer understands
- Give specific actionable advice
- Include dosage when recommending chemicals
- Mention safety precautions for chemicals
- If asked in Hindi reply in Hindi
- If asked in Marathi reply in Marathi
- Keep answers concise — max 5 sentences
- If you don't know something, say so

You are NOT restricted to only 
plant disease topics.
Answer any farming or agriculture question.
""".trimIndent()

    suspend fun sendMessage(
        conversationHistory: List<Pair<String, String>>,
        userMessage: String,
        apiKey: String,
        diseaseName: String,
        cropName: String,
        severityLevel: String
    ): String {
        return try {
            val (resolvedKey, baseUrl) = getApiConfig()
            val model = getModelName()

            val systemPrompt = buildSystemPrompt(diseaseName, cropName, severityLevel)

            val safeHistory = buildSafeMessages(systemPrompt, conversationHistory).toMutableList()

            val lastRole = safeHistory.lastOrNull()?.role
            if (lastRole == "user") {
                val lastMsg = safeHistory.removeAt(safeHistory.size - 1)
                safeHistory.add(GroqMessage("user", lastMsg.content + "\n\n" + userMessage))
            } else {
                safeHistory.add(GroqMessage("user", userMessage))
            }

            val request = GroqChatRequest(
                model = model,
                messages = safeHistory,
                max_tokens = 500
            )

            val systemMsg = request.messages.firstOrNull()
            Log.d("Chat","System role: ${systemMsg?.role}")
            Log.d("Chat","System empty: ${systemMsg?.content?.isBlank()}")
            Log.d("Chat","Total messages: ${request.messages.size}")
            Log.d("Chat","Request model: ${request.model}")
            Log.d("Chat", Gson().toJson(request))

            val api = buildApiService(baseUrl, resolvedKey)
            val response = api.sendMessage(
                auth = "Bearer " + resolvedKey,
                request = request
            )

            // Log 2: After response received
            Log.d(DEBUG_TAG, "Response received: ${response.code()}")
            Log.d(DEBUG_TAG, "Is successful: ${response.isSuccessful}")
            Log.d(DEBUG_TAG, "Has body: ${response.body() != null}")
            Log.d(DEBUG_TAG, "Choices count: ${response.body()?.choices?.size}")
            Log.d(DEBUG_TAG, "First choice: ${response.body()?.choices?.firstOrNull()?.message?.content}")

            if (response.isSuccessful) {
                val body = response.body()
                Log.d(DEBUG_TAG, "Body: $body")
                
                // Safe extraction with null checks at every level
                val responseText = body
                    ?.choices
                    ?.firstOrNull()
                    ?.message
                    ?.content
                    ?.trim()
                
                Log.d(DEBUG_TAG, "Extracted text: $responseText")
                
                if (!responseText.isNullOrBlank()) {
                    return responseText
                } else {
                    Log.e(DEBUG_TAG, "Response text was null or blank")
                    Log.e(DEBUG_TAG, "Full body: ${Gson().toJson(body)}")
                    return "Empty response from AI. Try again."
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(DEBUG_TAG, "Error body: $errorBody")
                Log.e(DEBUG_TAG, "Error ${response.code()}: $errorBody")
                
                return when {
                    errorBody?.contains("model") == true -> "Wrong AI model name. Contact support."
                    errorBody?.contains("empty") == true -> "Message was empty. Please type something."
                    errorBody?.contains("api_key") == true -> "AI service authentication failed."
                    else -> "AI service error (${response.code()}). Please try again."
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return "Connection error: ${e.localizedMessage ?: "Please check your internet connection."}"
        }
    }
}
