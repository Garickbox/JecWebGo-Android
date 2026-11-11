package com.garickbox.jecwebgo.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(val role: String, val content: String)

@Serializable
data class ChatRequest(
    val model: String = "deepseek-chat",
    val messages: List<ChatMessage>,
    val max_tokens: Int = 1024,
    val temperature: Double = 0.6,
    val top_p: Double = 0.95,
    val frequency_penalty: Double = 0.1,
    val presence_penalty: Double = 0.0
)

@Serializable
data class ChatResponse(val choices: List<Choice> = emptyList()) {
    @Serializable data class Choice(val message: ChatMessage = ChatMessage("", ""))
}

@Serializable
data class AiMovie(val title: String = "", val rating: String = "")

@Serializable
data class AiAction(
    val action: String = "",
    val search_query: String = "",
    val search_type: String = "",
    val movies: List<AiMovie> = emptyList(),
    val chips: List<String> = emptyList(),
    val question: String = "",
    val text: String = ""
)
