package com.example.ouija_mobile

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
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

    private fun buildRequest(url: String): Request.Builder {
        val token = sessionManager.getToken() ?: ""
        return Request.Builder().url(url).header("Authorization", "Bearer $token")
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    fun register(email: String, password: String, nickname: String,
                 onSuccess: (User) -> Unit, onError: (String) -> Unit) {
        val body = gson.toJson(RegisterRequest(email, password, nickname)).toRequestBody(JSON)
        val req = Request.Builder().url("$baseUrl/auth/register").post(body).build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = onError(e.message ?: "Network error")
            override fun onResponse(call: Call, response: Response) {
                val s = response.body?.string() ?: ""
                if (response.isSuccessful) runCatching { gson.fromJson(s, User::class.java) }.onSuccess(onSuccess).onFailure { onError("Parse error") }
                else onError(runCatching { gson.fromJson(s, ErrorResponse::class.java).error }.getOrNull() ?: "Error ${response.code}")
            }
        })
    }

    fun login(nickname: String, password: String,
              onSuccess: (LoginResponse) -> Unit, onError: (String) -> Unit) {
        val body = gson.toJson(LoginRequest(nickname, password)).toRequestBody(JSON)
        val req = Request.Builder().url("$baseUrl/auth/login").post(body).build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = onError(e.message ?: "Network error")
            override fun onResponse(call: Call, response: Response) {
                val s = response.body?.string() ?: ""
                if (response.isSuccessful) runCatching { gson.fromJson(s, LoginResponse::class.java) }.onSuccess(onSuccess).onFailure { onError("Parse error") }
                else onError(runCatching { gson.fromJson(s, ErrorResponse::class.java).error }.getOrNull() ?: if (response.code == 401) "Invalid credentials" else "Error ${response.code}")
            }
        })
    }

    fun logout(onComplete: () -> Unit) {
        val req = buildRequest("$baseUrl/auth/logout").post("".toRequestBody(JSON)).build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = onComplete()
            override fun onResponse(call: Call, response: Response) = onComplete()
        })
    }

    // ── Users ─────────────────────────────────────────────────────────────────

    fun getUser(userId: String, onSuccess: (User) -> Unit, onError: (String) -> Unit) {
        val req = buildRequest("$baseUrl/users?id=$userId").get().build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = onError(e.message ?: "Network error")
            override fun onResponse(call: Call, response: Response) {
                val s = response.body?.string() ?: ""
                if (response.isSuccessful) runCatching { gson.fromJson(s, User::class.java) }.onSuccess(onSuccess).onFailure { onError("Parse error") }
                else onError("Error ${response.code}")
            }
        })
    }

    fun searchUsers(query: String, onSuccess: (List<User>) -> Unit, onError: (String) -> Unit) {
        val enc = java.net.URLEncoder.encode(query, "UTF-8")
        val req = buildRequest("$baseUrl/users?q=$enc").get().build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = onError(e.message ?: "Network error")
            override fun onResponse(call: Call, response: Response) {
                val s = response.body?.string() ?: ""
                if (response.isSuccessful) runCatching {
                    val t = object : TypeToken<List<User>>() {}.type
                    gson.fromJson<List<User>>(s, t)
                }.onSuccess(onSuccess).onFailure { onError("Parse error") }
                else onError("Error ${response.code}")
            }
        })
    }

    // ── Friends ───────────────────────────────────────────────────────────────

    /** GET /api/users/:userId/friends */
    fun getFriends(userId: String, onSuccess: (List<Friendship>) -> Unit, onError: (String) -> Unit) {
        val req = buildRequest("$baseUrl/users/$userId/friends").get().build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = onError(e.message ?: "Network error")
            override fun onResponse(call: Call, response: Response) {
                val s = response.body?.string() ?: ""
                if (response.isSuccessful) runCatching {
                    val t = object : TypeToken<List<Friendship>>() {}.type
                    gson.fromJson<List<Friendship>>(s, t)
                }.onSuccess(onSuccess).onFailure { onError("Parse error") }
                else onError("Error ${response.code}")
            }
        })
    }

    // ── Chats ─────────────────────────────────────────────────────────────────

    fun getChats(userId: String, onSuccess: (List<Chat>) -> Unit, onError: (String) -> Unit) {
        val req = buildRequest("$baseUrl/users/$userId/chats").get().build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = onError(e.message ?: "Network error")
            override fun onResponse(call: Call, response: Response) {
                val s = response.body?.string() ?: ""
                if (response.isSuccessful) runCatching {
                    val t = object : TypeToken<List<Chat>>() {}.type
                    gson.fromJson<List<Chat>>(s, t)
                }.onSuccess(onSuccess).onFailure { onError("Parse error") }
                else onError("Error ${response.code}")
            }
        })
    }

    // ── Messages ──────────────────────────────────────────────────────────────

    fun getMessages(chatId: String, onSuccess: (List<Message>) -> Unit, onError: (String) -> Unit) {
        val req = buildRequest("$baseUrl/chats/$chatId/messages").get().build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = onError(e.message ?: "Network error")
            override fun onResponse(call: Call, response: Response) {
                val s = response.body?.string() ?: ""
                if (response.isSuccessful) runCatching {
                    val t = object : TypeToken<List<Message>>() {}.type
                    gson.fromJson<List<Message>>(s, t)
                }.onSuccess(onSuccess).onFailure { onError("Parse error") }
                else onError("Error ${response.code}")
            }
        })
    }

    fun sendMessage(chatId: String, content: String,
                    onSuccess: (Message) -> Unit, onError: (String) -> Unit) {
        val body = gson.toJson(SendMessageRequest(content = content)).toRequestBody(JSON)
        val req = buildRequest("$baseUrl/chats/$chatId/messages").post(body).build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = onError(e.message ?: "Network error")
            override fun onResponse(call: Call, response: Response) {
                val s = response.body?.string() ?: ""
                if (response.isSuccessful) runCatching { gson.fromJson(s, Message::class.java) }.onSuccess(onSuccess).onFailure { onError("Parse error") }
                else onError("Error ${response.code}")
            }
        })
    }

    fun sendMessageWithAttachments(
        chatId: String,
        content: String?,
        attachments: List<AttachmentInput>,
        onSuccess: (Message) -> Unit,
        onError: (String) -> Unit
    ) {
        val body = gson.toJson(SendMessageRequest(content = content, attachments = attachments)).toRequestBody(JSON)
        val req = buildRequest("$baseUrl/chats/$chatId/messages").post(body).build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = onError(e.message ?: "Network error")
            override fun onResponse(call: Call, response: Response) {
                val s = response.body?.string() ?: ""
                if (response.isSuccessful) runCatching { gson.fromJson(s, Message::class.java) }.onSuccess(onSuccess).onFailure { onError("Parse error") }
                else onError("Error ${response.code}")
            }
        })
    }

    // ── Media ─────────────────────────────────────────────────────────────────

    /**
     * Upload avatar image for a user.
     * Returns the updated media record with the full URL.
     */
    fun uploadAvatar(
        context: Context,
        userId: String,
        imageUri: Uri,
        onSuccess: (String) -> Unit,   // new avatarUrl
        onError: (String) -> Unit
    ) {
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(imageUri) ?: "image/jpeg"
        val inputStream = contentResolver.openInputStream(imageUri) ?: run {
            onError("Cannot open image"); return
        }
        val bytes = inputStream.use { it.readBytes() }
        val mediaType = mimeType.toMediaTypeOrNull() ?: "image/jpeg".toMediaType()
        val fileBody = bytes.toRequestBody(mediaType)

        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("avatar", "avatar.jpg", fileBody)
            .build()

        val req = buildRequest("$baseUrl/media/avatar/$userId").post(multipart).build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = onError(e.message ?: "Network error")
            override fun onResponse(call: Call, response: Response) {
                val s = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    runCatching {
                        val obj = gson.fromJson(s, UploadAvatarResponse::class.java)
                        obj.media.url
                    }.onSuccess(onSuccess).onFailure { onError("Parse error") }
                } else {
                    onError(runCatching { gson.fromJson(s, ErrorResponse::class.java).error }.getOrNull() ?: "Error ${response.code}")
                }
            }
        })
    }

    /**
     * Upload one or more files as attachments.
     * Returns a list of media records with full URLs.
     */
    fun uploadFiles(
        context: Context,
        uris: List<Uri>,
        onSuccess: (List<MediaFile>) -> Unit,
        onError: (String) -> Unit
    ) {
        val contentResolver = context.contentResolver
        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)

        for (uri in uris) {
            val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
            val inputStream = contentResolver.openInputStream(uri) ?: continue
            val bytes = inputStream.use { it.readBytes() }
            val mediaType = mimeType.toMediaTypeOrNull() ?: "application/octet-stream".toMediaType()
            val filename = uri.lastPathSegment ?: "file"
            builder.addFormDataPart("files", filename, bytes.toRequestBody(mediaType))
        }

        val req = buildRequest("$baseUrl/media/upload").post(builder.build()).build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = onError(e.message ?: "Network error")
            override fun onResponse(call: Call, response: Response) {
                val s = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    runCatching {
                        val t = object : TypeToken<List<MediaFile>>() {}.type
                        gson.fromJson<List<MediaFile>>(s, t)
                    }.onSuccess(onSuccess).onFailure { onError("Parse error") }
                } else {
                    onError(runCatching { gson.fromJson(s, ErrorResponse::class.java).error }.getOrNull() ?: "Error ${response.code}")
                }
            }
        })
    }
}

data class MediaFile(
    val id: String,
    val url: String,
    val mimeType: String,
    val filename: String,
    val storedName: String
)

private data class UploadAvatarResponse(val media: MediaFile, val user: Any?)
private data class ErrorResponse(val error: String)
