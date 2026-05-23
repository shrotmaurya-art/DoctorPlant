package com.plantcure.ai.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantcure.ai.data.repository.ChatRepository
import com.plantcure.ai.domain.model.ChatMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

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

    /** Optional disease context passed from a scan result */
    var diseaseContext: String? = null

    init {
        // Welcome message
        _messages.value = listOf(
            ChatMessage(
                role = "assistant",
                text = "Hello! I'm AgriBot 🌱, your AI farming assistant. Ask me about plant diseases, treatments, crop management, or anything agricultural!"
            )
        )
    }

    /**
     * Send a user message and get AI response.
     */
    fun sendMessage(text: String) {
        if (text.isBlank()) return

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
                    diseaseContext = diseaseContext
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
