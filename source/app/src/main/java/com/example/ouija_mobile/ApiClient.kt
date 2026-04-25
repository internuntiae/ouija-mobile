package com.example.ouija_mobile

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class ApiClient(private val sessionManager: SessionManager) {

    companion object {
        // TODO: Replace with your actual server URL
        const val BASE_URL = "http://10.0.2.2:3000/api"
        const val WS_URL = "ws://10.0.2.2:3000"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }

    private val gson = Gson()
    private val client = OkHttpClient()

    private fun basicAuthHeader(): String {
        val email = sessionManager.getEmail() ?: ""
        val password = sessionManager.getPassword() ?: ""
        val credentials = "$email:$password"
        return "Basic " + Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
    }

    private fun buildRequest(url: String): Request.Builder {
        return Request.Builder()
            .url(url)
            .header("Authorization", basicAuthHeader())
    }

    // ── Users ────────────────────────────────────────────────────────────────

    fun register(
        email: String,
        password: String,
        nickname: String,
        onSuccess: (User) -> Unit,
        onError: (String) -> Unit
    ) {
        val body = gson.toJson(RegisterRequest(email, password, nickname))
            .toRequestBody(JSON)
        val request = Request.Builder()
            .url("$BASE_URL/users")
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
                    onError("Error ${response.code}")
                }
            }
        })
    }

    fun getUser(userId: String, onSuccess: (User) -> Unit, onError: (String) -> Unit) {
        // API returns list of all users; find by ID
        val request = buildRequest("$BASE_URL/users").get().build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = onError(e.message ?: "Network error")
            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    runCatching {
                        val type = object : TypeToken<List<User>>() {}.type
                        val users: List<User> = gson.fromJson(bodyStr, type)
                        users.first { it.id == userId }
                    }.onSuccess(onSuccess).onFailure { onError("User not found") }
                } else {
                    onError("Error ${response.code}")
                }
            }
        })
    }

    // Login = fetch all users and verify credentials match a user (Basic Auth is validated server-side)
    // We call GET /users with credentials — if 200, credentials are valid; find matching user by email
    fun login(
        email: String,
        password: String,
        onSuccess: (User) -> Unit,
        onError: (String) -> Unit
    ) {
        val credentials = "Basic " + Base64.encodeToString("$email:$password".toByteArray(), Base64.NO_WRAP)
        val request = Request.Builder()
            .url("$BASE_URL/users")
            .header("Authorization", credentials)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = onError(e.message ?: "Network error")
            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    runCatching {
                        val type = object : TypeToken<List<User>>() {}.type
                        val users: List<User> = gson.fromJson(bodyStr, type)
                        users.firstOrNull { it.email == email } ?: throw Exception("User not found")
                    }.onSuccess(onSuccess).onFailure { onError("Invalid credentials") }
                } else {
                    onError(if (response.code == 401) "Invalid credentials" else "Error ${response.code}")
                }
            }
        })
    }

    // ── Chats ────────────────────────────────────────────────────────────────

    fun getChats(userId: String, onSuccess: (List<Chat>) -> Unit, onError: (String) -> Unit) {
        val request = buildRequest("$BASE_URL/users/$userId/chats").get().build()
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

    fun getMessages(chatId: String, onSuccess: (List<Message>) -> Unit, onError: (String) -> Unit) {
        val request = buildRequest("$BASE_URL/chats/$chatId/messages").get().build()
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

    fun sendMessage(
        chatId: String,
        senderId: String,
        content: String,
        onSuccess: (Message) -> Unit,
        onError: (String) -> Unit
    ) {
        val body = gson.toJson(SendMessageRequest(senderId, content)).toRequestBody(JSON)
        val request = buildRequest("$BASE_URL/chats/$chatId/messages").post(body).build()
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
