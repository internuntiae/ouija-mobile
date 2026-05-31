package com.example.ouija_mobile

data class User(
    val id: String,
    val email: String?,
    val nickname: String,
    val status: String,
    val avatarUrl: String?
)

data class Chat(
    val id: String,
    val name: String?,
    val type: String,
    val users: List<ChatUser>
)

data class ChatUser(
    val chatId: String,
    val userId: String,
    val role: String
)

data class Message(
    val id: String,
    val chatId: String,
    val senderId: String,
    val content: String?,
    val sentAt: String,
    val editedAt: String?
)

data class RegisterRequest(
    val email: String,
    val password: String,
    val nickname: String
)

// Backend /api/auth/login accepts nickname + password (not email)
data class LoginRequest(
    val nickname: String,
    val password: String
)

data class LoginResponse(
    val token: String,
    val user: User
)

// senderId is NOT sent — backend reads it from the session token
data class SendMessageRequest(
    val content: String
)