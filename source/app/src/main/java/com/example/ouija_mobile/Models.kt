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
    val role: String,
    val user: User?
)

data class Message(
    val id: String,
    val chatId: String,
    val senderId: String,
    val content: String?,
    val sentAt: String,
    val editedAt: String?
)

// Friendship returned by GET /api/users/:userId/friends
// { userId, friendId, status, user: User, friend: User }
data class Friendship(
    val userId: String,
    val friendId: String,
    val status: String,   // PENDING | ACCEPTED | BLOCKED
    val user: User,
    val friend: User
)

data class RegisterRequest(
    val email: String,
    val password: String,
    val nickname: String
)

data class LoginRequest(
    val nickname: String,
    val password: String
)

data class LoginResponse(
    val token: String,
    val user: User
)

data class SendMessageRequest(
    val content: String
)
