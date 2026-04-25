package com.example.ouija_mobile

import android.util.Base64
import com.google.gson.Gson
import okhttp3.*

class WebSocketClient(private val sessionManager: SessionManager) {

    companion object {
        // Expected WS message format: { "type": "message", "data": { ...Message } }
        private const val RECONNECT_DELAY_MS = 3000L
    }

    private val gson = Gson()
    private val httpClient = OkHttpClient()
    private var webSocket: WebSocket? = null
    private var currentChatId: String? = null

    var onMessageReceived: ((Message) -> Unit)? = null
    var onConnected: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null

    fun connect(chatId: String) {
        currentChatId = chatId
        val email = sessionManager.getEmail() ?: return
        val password = sessionManager.getPassword() ?: return
        val credentials = Base64.encodeToString("$email:$password".toByteArray(), Base64.NO_WRAP)

        val apiClient = ApiClient(sessionManager)
        val request = Request.Builder()
            .url("${apiClient.wsUrl}/chats/$chatId/ws")
            .header("Authorization", "Basic $credentials")
            .build()

        webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onConnected?.invoke()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    val wrapper = gson.fromJson(text, WsWrapper::class.java)
                    if (wrapper.type == "message") {
                        val msg = gson.fromJson(gson.toJson(wrapper.data), Message::class.java)
                        onMessageReceived?.invoke(msg)
                    }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                onDisconnected?.invoke()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onDisconnected?.invoke()
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "User left chat")
        webSocket = null
        currentChatId = null
    }

    private data class WsWrapper(val type: String, val data: Any?)
}
