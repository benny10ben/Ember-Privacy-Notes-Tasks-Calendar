package com.ben.inly.presentation.rag

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ben.inly.domain.ai.RagRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean
)

class RagViewModel(private val ragRepository: RagRepository) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // null = still checking, true = available, false = not available
    private val _isModelAvailable = MutableStateFlow<Boolean?>(null)
    val isModelAvailable: StateFlow<Boolean?> = _isModelAvailable.asStateFlow()

    init {
        viewModelScope.launch {
            _isModelAvailable.value = ragRepository.isModelAvailable()
        }
    }

    fun submitQuery(query: String) {
        if (query.isBlank()) return
        _messages.value = _messages.value + ChatMessage(text = query, isUser = true)
        _messages.value = _messages.value + ChatMessage(text = "", isUser = false)

        _isLoading.value = true

        viewModelScope.launch {
            try {
                ragRepository.queryAiStream(query).collect { token ->
                    val list = _messages.value.toMutableList()
                    val last = list.last()
                    list[list.lastIndex] = last.copy(text = last.text + token)
                    _messages.value = list
                }
            } catch (e: Exception) {
                val list = _messages.value.toMutableList()
                val last = list.last()
                list[list.lastIndex] = last.copy(text = "Sorry, an error occurred: ${e.message}")
                _messages.value = list
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearChat() {
        _messages.value = emptyList()
    }
}