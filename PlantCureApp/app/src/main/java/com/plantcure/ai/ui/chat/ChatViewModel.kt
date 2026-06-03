package com.plantcure.ai.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantcure.ai.data.local.ApiKeyManager
import com.plantcure.ai.data.repository.ChatRepository
import com.plantcure.ai.domain.model.ChatMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ChatUiState {
    object Idle : ChatUiState()
    object NoApiKey : ChatUiState()
}

/**
 * ViewModel for AgriBot Chat screen.
 * Manages conversation state and Claude API calls.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Idle)
    val uiState: StateFlow<ChatUiState> = _uiState

    var diseaseName: String = "Unknown Disease"
    var cropName: String = "Unknown Crop"
    var severity: String = "Unknown"

    fun initChatContext(dName: String, cName: String, sev: String) {
        // Prevent re-initializing if already set
        if (this.diseaseName != "Unknown Disease") return

        this.diseaseName = dName
        this.cropName = cName
        this.severity = sev

        val isGeneral = dName == "Unknown Disease" || dName == "General Inquiry" || dName == "Unknown"
        
        val firstMessage = if (!isGeneral) {
            "I've analyzed your $cName scan. It shows $dName with $sev severity. How can I help? You can ask me about treatment, prevention, or any farming question!"
        } else {
            "Hello! I'm AgriBot, your farming assistant. Ask me about crops, diseases, soil, fertilizers, weather, or anything agriculture related!"
        }

        _messages.value = listOf(
            ChatMessage(
                role = "assistant",
                text = firstMessage
            )
        )
    }

    fun checkApiKey() {
        if (!ApiKeyManager.hasGroqKey() && !ApiKeyManager.hasOpenAiKey()) {
            _uiState.value = ChatUiState.NoApiKey
        } else {
            _uiState.value = ChatUiState.Idle
        }
    }

    /** Returns a human-readable provider label for the toolbar */
    fun getProviderLabel(): String {
        return chatRepository.getProviderName()
    }

    /**
     * Send a user message and get AI response.
     */
    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val apiKey = ApiKeyManager.getGroqKey() ?: ApiKeyManager.getOpenAiKey()
        if (apiKey == null) {
            _uiState.value = ChatUiState.NoApiKey
            return
        }

        val userMessage = ChatMessage(role = "user", text = text.trim())
        _messages.value = _messages.value + userMessage

        // Add a loading placeholder
        val loadingMessage = ChatMessage(role = "assistant", text = "...", isLoading = true)
        _messages.value = _messages.value + loadingMessage

        _isLoading.value = true

        viewModelScope.launch {
            try {
                // Build conversation history (exclude loading messages)
                val history = _messages.value
                    .filter { !it.isLoading }
                    .dropLast(1) // exclude the user message we just added (it's sent separately)
                    .map { it.role to it.text }

                val response = chatRepository.sendMessage(
                    conversationHistory = history,
                    userMessage = text.trim(),
                    apiKey = apiKey,
                    diseaseName = diseaseName,
                    cropName = cropName,
                    severityLevel = severity
                )

                // Replace loading placeholder with real response
                val aiMessage = ChatMessage(role = "assistant", text = response)
                _messages.value = _messages.value
                    .filter { !it.isLoading } + aiMessage

            } catch (e: Exception) {
                val errorMessage = ChatMessage(
                    role = "assistant",
                    text = "Sorry, something went wrong: ${e.localizedMessage}"
                )
                _messages.value = _messages.value
                    .filter { !it.isLoading } + errorMessage
            } finally {
                _isLoading.value = false
            }
        }
    }
}
