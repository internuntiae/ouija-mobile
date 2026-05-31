package com.example.ouija_mobile

import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.*

/**
 * Klient WebSocket dla backendu Ouija.
 *
 * Protokół autentykacji (token NIGDY w URL):
 *   1. Klient otwiera ws://<host>/ws  (bez parametrów)
 *   2. Serwer wysyła { "type": "auth:required" }
 *   3. Klient odpowiada { "type": "auth", "token": "<sessionToken>" }
 *   4. Serwer akceptuje i wysyła { "type": "connected", "userId": "..." }
 *      lub zamyka z kodem 4401 przy błędnym tokenie.
 *
 * Zdarzenia serwera:
 *   { "type": "message:created",  "payload": { ...Message } }
 *   { "type": "message:updated",  "payload": { "chatId": "...", "messageId": "...", "message": { ...Message } } }
 *   { "type": "message:deleted",  "payload": { "chatId": "...", "messageId": "..." } }
 *
 * Klient filtruje zdarzenia po chatId.
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
     * [chatId] służy tylko do filtrowania zdarzeń — backend utrzymuje
     * jedno połączenie per użytkownik, nie per czat.
     */
    fun connect(chatId: String) {
        currentChatId = chatId

        val token = sessionManager.getToken() ?: return

        val apiClient = ApiClient(sessionManager)
        // Połączenie do /ws — bez tokenu w URL
        val wsUrl = "${apiClient.wsUrl}/ws"

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(ws: WebSocket, response: Response) {
                // Czekamy na "auth:required" od serwera — nie wywołujemy onConnected jeszcze
            }

            override fun onMessage(ws: WebSocket, text: String) {
                runCatching {
                    val root = gson.fromJson(text, JsonObject::class.java)
                    val type = root.get("type")?.asString ?: return@runCatching

                    when (type) {
                        // Krok 2: serwer prosi o token
                        "auth:required" -> {
                            val authFrame = gson.toJson(mapOf("type" to "auth", "token" to token))
                            ws.send(authFrame)
                        }

                        // Krok 4: autentykacja OK
                        "connected" -> {
                            onConnected?.invoke()
                        }

                        "message:created" -> {
                            val payload = root.getAsJsonObject("payload") ?: return@runCatching
                            // Payload to bezpośrednio obiekt Message (spread przez backend)
                            val payloadChatId = payload.get("chatId")?.asString
                            if (payloadChatId != currentChatId) return@runCatching

                            val msg = gson.fromJson(payload, Message::class.java)
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