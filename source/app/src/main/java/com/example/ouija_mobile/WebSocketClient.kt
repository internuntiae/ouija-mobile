package com.example.ouija_mobile

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.*

/**
 * Klient WebSocket dla backendu Ouija.
 *
 * Backend rejestruje połączenia pod:
 *   ws://<host>/ws?userId=<userId>
 *
 * i wysyła zdarzenia w formacie:
 *   { "type": "message:created",  "payload": { "chatId": "...", "message": { ...Message } } }
 *   { "type": "message:updated",  "payload": { "chatId": "...", "messageId": "...", "message": { ...Message } } }
 *   { "type": "message:deleted",  "payload": { "chatId": "...", "messageId": "..." } }
 *   { "type": "connected",        "userId": "..." }
 *
 * Klient filtruje zdarzenia po chatId, żeby ChatActivity dostawała
 * tylko wiadomości z aktualnie otwartego czatu.
 */
class WebSocketClient(private val sessionManager: SessionManager) {

    private val gson = Gson()
    private val httpClient = OkHttpClient()
    private var webSocket: WebSocket? = null
    private var currentChatId: String? = null

    /** Wywoływany z wątku I/O — użyj runOnUiThread po stronie Activity. */
    var onMessageReceived: ((Message) -> Unit)? = null
    var onMessageUpdated: ((Message) -> Unit)? = null
    var onMessageDeleted: ((messageId: String) -> Unit)? = null
    var onConnected: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null

    /**
     * Nawiązuje połączenie WS dla zalogowanego użytkownika.
     * [chatId] służy tylko do filtrowania zdarzeń — backend przyjmuje
     * jedno połączenie na użytkownika, nie na czat.
     */
    fun connect(chatId: String) {
        currentChatId = chatId

        val userId = sessionManager.getUserId() ?: return
        val email = sessionManager.getEmail() ?: return
        val password = sessionManager.getPassword() ?: return

        val apiClient = ApiClient(sessionManager)
        // Połączenie per-user: ws://<host>/ws?userId=<userId>
        val wsUrl = "${apiClient.wsUrl}/ws?userId=$userId"

        val credentials = Base64.encodeToString(
            "$email:$password".toByteArray(), Base64.NO_WRAP
        )

        val request = Request.Builder()
            .url(wsUrl)
            .header("Authorization", "Basic $credentials")
            .build()

        webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(ws: WebSocket, response: Response) {
                onConnected?.invoke()
            }

            override fun onMessage(ws: WebSocket, text: String) {
                runCatching {
                    val root = gson.fromJson(text, JsonObject::class.java)
                    val type = root.get("type")?.asString ?: return@runCatching

                    when (type) {
                        "message:created" -> {
                            val payload = root.getAsJsonObject("payload") ?: return@runCatching
                            val payloadChatId = payload.get("chatId")?.asString
                            // Ignoruj wiadomości z innych czatów
                            if (payloadChatId != currentChatId) return@runCatching

                            val msgJson = payload.get("message") ?: return@runCatching
                            val msg = gson.fromJson(msgJson, Message::class.java)
                            onMessageReceived?.invoke(msg)
                        }

                        "message:updated" -> {
                            val payload = root.getAsJsonObject("payload") ?: return@runCatching
                            if (payload.get("chatId")?.asString != currentChatId) return@runCatching

                            val msgJson = payload.get("message") ?: return@runCatching
                            val msg = gson.fromJson(msgJson, Message::class.java)
                            onMessageUpdated?.invoke(msg)
                        }

                        "message:deleted" -> {
                            val payload = root.getAsJsonObject("payload") ?: return@runCatching
                            if (payload.get("chatId")?.asString != currentChatId) return@runCatching

                            val messageId = payload.get("messageId")?.asString ?: return@runCatching
                            onMessageDeleted?.invoke(messageId)
                        }

                        "connected" -> {
                            // ACK od serwera — nic nie robimy, onOpen już był wywołany
                        }

                        else -> { /* nieznany typ zdarzenia — ignoruj */ }
                    }
                }.onFailure { e ->
                    android.util.Log.e("WebSocketClient", "Parse error: ${e.message}")
                }
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(1000, null)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                onDisconnected?.invoke()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                android.util.Log.e("WebSocketClient", "WS failure: ${t.message}")
                onDisconnected?.invoke()
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "User left chat")
        webSocket = null
        currentChatId = null
    }
}