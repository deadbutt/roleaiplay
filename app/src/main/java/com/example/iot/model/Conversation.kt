package com.example.iot.model

data class Session(
    val sessionId: String,
    val title: String,
    val characterName: String,
    val preview: String,
    val lastMessageTime: String,
    val isPinned: Boolean
)

data class SessionListResponse(
    val pinned: List<Session>,
    val today: List<Session>,
    val yesterday: List<Session>
)

data class Message(
    val id: String,
    val content: String,
    val isUser: Boolean,
    val createdAt: String
)

data class SessionDetail(
    val sessionId: String,
    val title: String,
    val characterId: String,
    val messages: List<Message>
)

data class ChatSendRequest(
    val text: String,
    val userId: String,
    val characterId: String,
    val sessionId: String
)

data class ChatSendResponse(
    val reply: String,
    val sessionId: String
)

data class SessionCreateRequest(
    val title: String,
    val characterId: String
)
