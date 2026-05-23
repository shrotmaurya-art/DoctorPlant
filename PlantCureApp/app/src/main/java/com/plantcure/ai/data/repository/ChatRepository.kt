package com.plantcure.ai.data.repository

import com.plantcure.ai.BuildConfig
import com.plantcure.ai.data.remote.OpenAiApiService
import com.plantcure.ai.data.remote.OpenAiMessage
import com.plantcure.ai.data.remote.OpenAiRequest
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Repository for AI chat using Groq API (LLaMA-3).
 */
@Singleton
class ChatRepository @Inject constructor(
    @Named("groq") private val groqApi: OpenAiApiService
) {
    companion object {
        private const val SYSTEM_PROMPT = """You are AgriBot, an AI agricultural advisor for Indian farmers. 
You specialize in plant disease diagnosis, organic and chemical treatment recommendations, 
crop management, and farming best practices. 
Keep responses concise (under 150 words), practical, and farmer-friendly.
When discussing treatments, mention both organic and chemical options with dosage information.
Use simple language appropriate for rural Indian farmers."""
    }

    private fun logRequest(request: OpenAiRequest) {
        android.util.Log.d("PlantCure_Chat", "=== GROQ REQUEST DEBUG ===")
        android.util.Log.d("PlantCure_Chat", "Model: ${request.model}")
        android.util.Log.d("PlantCure_Chat", "Message count: ${request.messages.size}")
        request.messages.forEachIndexed { i, msg ->
            android.util.Log.d("PlantCure_Chat", 
                "[$i] role='${msg.role}' " +
                "content_length=${msg.content.length} " +
                "content_empty=${msg.content.isBlank()}")
        }
        android.util.Log.d("PlantCure_Chat", "========================")
    }

    private fun buildSafeMessages(
        systemPrompt: String,
        history: List<Pair<String, String>>
    ): List<OpenAiMessage> {
        val messages = mutableListOf<OpenAiMessage>()
        
        // Rule 1: System prompt always first, never empty
        if (systemPrompt.isNotBlank()) {
            messages.add(OpenAiMessage("system", systemPrompt.trim()))
        }
        
        // Rule 2: Filter history
        val filtered = history.filter { 
            it.second.isNotBlank()  // no empty messages
        }
        
        // Rule 3: First non-system message MUST be "user"
        // If history starts with "assistant", remove it
        val fixedHistory = if (filtered.firstOrNull()?.first?.lowercase() == "assistant" || filtered.firstOrNull()?.first?.lowercase() == "model") {
            filtered.drop(1)
        } else filtered
        
        // Rule 4: Roles must alternate user/assistant
        val alternating = mutableListOf<Pair<String, String>>()
        var lastRole = "system"
        fixedHistory.forEach { (roleRaw, text) ->
            val role = if (roleRaw.lowercase() == "assistant" || roleRaw.lowercase() == "model") "assistant" else "user"
            if (role != lastRole) {
                alternating.add(Pair(role, text))
                lastRole = role
            }
        }
        
        // Rule 5: Max 10 pairs to avoid token limit
        val trimmed = if (alternating.size > 20) 
            alternating.takeLast(20) else alternating
            
        val finalHistory = if (trimmed.firstOrNull()?.first == "assistant") trimmed.drop(1) else trimmed
        
        // Rule 6: Last message must be "user" role
        finalHistory.forEach { (role, content) ->
            messages.add(OpenAiMessage(role, content.trim()))
        }
        
        return messages
    }

    /**
     * Send a message to Groq and get AI response.
     * Returns the AI response text, or an error message if API fails.
     */
    suspend fun sendMessage(
        conversationHistory: List<Pair<String, String>>,
        userMessage: String,
        diseaseContext: String? = null
    ): String {
        val apiKey = BuildConfig.CLAUDE_API_KEY
        if (apiKey.isBlank() || apiKey.startsWith("your_")) {
            return "⚠️ AI Chat is not configured yet. Please add your Groq API key in local.properties to enable AgriBot."
        }
        
        if (!apiKey.startsWith("gsk_")) {
            android.util.Log.e("PlantCure_Chat", "WARNING: Groq API key format looks wrong")
        }

        return try {
            val systemPrompt = if (diseaseContext != null) {
                "$SYSTEM_PROMPT\n\nCurrent disease context: $diseaseContext"
            } else {
                SYSTEM_PROMPT
            }

            val safeHistory = buildSafeMessages(systemPrompt, conversationHistory).toMutableList()
            
            // Check if last role was user, we might need to handle combining or dropping
            val lastRole = safeHistory.lastOrNull()?.role
            if (lastRole == "user") {
                // If last message in history was user, and we are adding another user message, combine them
                val lastMsg = safeHistory.removeAt(safeHistory.size - 1)
                safeHistory.add(OpenAiMessage("user", lastMsg.content + "\n\n" + userMessage))
            } else {
                safeHistory.add(OpenAiMessage("user", userMessage))
            }

            val request = OpenAiRequest(
                model = "llama3-8b-8192",
                messages = safeHistory
            )
            
            logRequest(request)

            val authHeader = "Bearer $apiKey"
            val response = groqApi.getChatCompletion(authHeader, request)
            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string()
                val code = response.code()
                android.util.Log.e("PlantCure_Chat", "Error $code: $errorBody")
                
                return when {
                    errorBody?.contains("model") == true -> "Wrong AI model name. Contact support."
                    errorBody?.contains("empty") == true -> "Message was empty. Please type something."
                    errorBody?.contains("api_key") == true -> "AI service authentication failed."
                    else -> "AI service error ($code). Please try again."
                }
            }
            
            response.body()?.choices?.firstOrNull()?.message?.content
                ?: "I couldn't generate a response. Please try again."
        } catch (e: Exception) {
            e.printStackTrace()
            "Connection error: ${e.localizedMessage ?: "Please check your internet connection."}"
        }
    }
}
