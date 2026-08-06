package com.ben.ember.domain.ai.chat

data class ChatTurn(
    val userMessage: String,
    val assistantMessage: String
)
