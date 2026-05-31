package com.example.ouija_mobile

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class ApiClient(private val sessionManager: SessionManager) {

    private val baseUrl: String
        get() {
            val api = sessionManager.getApiUrl() ?: "http://10.0.2.2:3001"
            return api.removeSuffix("/") + "/api"
        }

    val wsUrl: String
        get() {
            val api = sessionManager.getApiUrl() ?: "http://10.0.2.2:3001"
            return api.replace("http://", "ws://").replace("https://", "wss://").removeSuffix("/")
        }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }

    private val gson = Gson()
    private val client = OkHttpClient()

    /** Adds Authorization: Bearer <token> header to every authenticated request. */
    private fun buildRequest(url: String): Request.Builder {
        val token = sessionManager.getToken() ?: ""
        return Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
    }

    // ── Auth ─────────────────────────────────────────────────────────────────

    fun register(
        email: String,
        password: String,
        nickname: String,
        onSuccess: (User) -> Unit,
        onError: (String) -> Unit
    ) {
        val body = gson.toJson(RegisterRequest(email, password, nickname))
            .toRequestBody(JSON)
        // POST /api/auth/register — no auth header needed
        val request = Request.Builder()
            .url("$baseUrl/auth/register")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = onError(e.message ?: "Network error")
            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    runCatching { gson.fromJson(bodyStr, User::class.java) }
                        .onSuccess(onSuccess)
                        .onFailure { onError("Parse error") }
                } else {
                    val errMsg = runCatching {
                        gson.fromJson(bodyStr, ErrorResponse::class.java).error
                    }.getOrNull() ?: "Error ${response.code}"
                    onError(errMsg)
                }
            }
        })
    }

    /**
     * POST /api/auth/login
     * Body: { nickname, password }
     * Returns: { token, user }
     *
     * Note: the backend logs in with NICKNAME, not email.
     */
    fun login(
        nickname: String,
        password: String,
        onSuccess: (LoginResponse) -> Unit,
        onError: (String) -> Unit
    ) {
        val body = gson.toJson(LoginRequest(nickname, password)).toRequestBody(JSON)
        val request = Request.Builder()
            .url("$baseUrl/auth/login")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = onError(e.message ?: "Network error")
            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    runCatching { gson.fromJson(bodyStr, LoginResponse::class.java) }
                        .onSuccess(onSuccess)
                        .onFailure { onError("Parse error") }
                } else {
                    val errMsg = runCatching {
                        gson.fromJson(bodyStr, ErrorResponse::class.java).error
                    }.getOrNull() ?: if (response.code == 401) "Invalid credentials" else "Error ${response.code}"
                    onError(errMsg)
                }
            }
        })
    }

    /** POST /api/auth/logout */
    fun logout(onComplete: () -> Unit) {
        val request = buildRequest("$baseUrl/auth/logout").post("".toRequestBody(JSON)).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = onComplete()
            override fun onResponse(call: Call, response: Response) = onComplete()
        })
    }

    // ── Users ────────────────────────────────────────────────────────────────

    /** GET /api/users?id=<userId> */
    fun getUser(userId: String, onSuccess: (User) -> Unit, onError: (String) -> Unit) {
        val request = buildRequest("$baseUrl/users?id=$userId").get().build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = onError(e.message ?: "Network error")
            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    runCatching { gson.fromJson(bodyStr, User::class.java) }
                        .onSuccess(onSuccess)
                        .onFailure { onError("Parse error") }
                } else {
                    onError("Error ${response.code}")
                }
            }
        })
    }

    // ── Chats ────────────────────────────────────────────────────────────────

    /** GET /api/users/<userId>/chats */
    fun getChats(userId: String, onSuccess: (List<Chat>) -> Unit, onError: (String) -> Unit) {
        val request = buildRequest("$baseUrl/users/$userId/chats").get().build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = onError(e.message ?: "Network error")
            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    runCatching {
                        val type = object : TypeToken<List<Chat>>() {}.type
                        gson.fromJson<List<Chat>>(bodyStr, type)
                    }.onSuccess(onSuccess).onFailure { onError("Parse error") }
                } else {
                    onError("Error ${response.code}")
                }
            }
        })
    }

    // ── Messages ─────────────────────────────────────────────────────────────

    /** GET /api/chats/<chatId>/messages */
    fun getMessages(chatId: String, onSuccess: (List<Message>) -> Unit, onError: (String) -> Unit) {
        val request = buildRequest("$baseUrl/chats/$chatId/messages").get().build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = onError(e.message ?: "Network error")
            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    runCatching {
                        val type = object : TypeToken<List<Message>>() {}.type
                        gson.fromJson<List<Message>>(bodyStr, type)
                    }.onSuccess(onSuccess).onFailure { onError("Parse error") }
                } else {
                    onError("Error ${response.code}")
                }
            }
        })
    }

    /**
     * POST /api/chats/<chatId>/messages
     * Body: { content }  — senderId is NOT sent; backend reads it from the Bearer token.
     */
    fun sendMessage(
        chatId: String,
        content: String,
        onSuccess: (Message) -> Unit,
        onError: (String) -> Unit
    ) {
        val body = gson.toJson(SendMessageRequest(content)).toRequestBody(JSON)
        val request = buildRequest("$baseUrl/chats/$chatId/messages").post(body).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = onError(e.message ?: "Network error")
            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    runCatching { gson.fromJson(bodyStr, Message::class.java) }
                        .onSuccess(onSuccess).onFailure { onError("Parse error") }
                } else {
                    onError("Error ${response.code}")
                }
            }
        })
    }
}

private data class ErrorResponse(val error: String)