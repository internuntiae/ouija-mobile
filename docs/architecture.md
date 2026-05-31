# Architektura

## Overview

ouija mobile to aplikacja jednowarstwowa — brak lokalnej bazy danych, brak cache'u. Każde żądanie trafia bezpośrednio do serwera ouija przez HTTP REST lub WebSocket. Stan UI przechowywany jest w pamięci operacyjnej Activity.

```
┌─────────────────────────────────────┐
│            Activity / UI            │
│  (ChatsActivity, ChatActivity, …)   │
└──────────────┬──────────────────────┘
               │
       ┌───────┴────────┐
       │                │
       ▼                ▼
  ApiClient       WebSocketClient
  (OkHttp3)       (OkHttp3 WS)
       │                │
       └───────┬────────┘
               │
               ▼
       EncryptedSharedPreferences
          (SessionManager)
               │
               ▼
       Serwer ouija (HTTP / WS)
```

## Klasy

| Klasa | Odpowiedzialność |
|---|---|
| `ApiClient` | Wszystkie wywołania HTTP REST — auth, użytkownicy, czaty, wiadomości, media |
| `WebSocketClient` | Połączenie WebSocket — odbiór wiadomości w czasie rzeczywistym |
| `SessionManager` | Przechowywanie sesji i preferencji w `EncryptedSharedPreferences` |
| `BaseActivity` | Bazowa klasa Activity — aplikuje wybrany motyw przed inflacją layoutu |
| `Models.kt` | Modele danych Kotlin (`User`, `Chat`, `Message`, `Attachment`, …) |

---

## ApiClient — komunikacja HTTP

`ApiClient` opakowuje OkHttp3 i udostępnia wszystkie operacje jako metody z callbackami `onSuccess` / `onError`. Każde żądanie jest asynchroniczne — callback wywoływany jest z wątku I/O OkHttp, więc Activity musi przełączyć się na wątek UI przez `runOnUiThread { … }`.

### Budowanie URL

```
baseUrl = sessionManager.getApiUrl() + "/api"
wsUrl   = baseUrl (http→ws, https→wss)
```

Adres API pobierany jest dynamicznie z `SessionManager` przy każdym żądaniu — zmiana serwera nie wymaga restartu aplikacji.

### Autoryzacja

Każde żądanie (oprócz `register` i `login`) dołącza nagłówek:

```
Authorization: Bearer <sessionToken>
```

Token przechowywany jest w `SessionManager` i wstrzykiwany przez `buildRequest()`.

### Grupy endpointów

| Grupa | Metody |
|---|---|
| Auth | `register`, `login`, `logout` |
| Users | `getUser`, `searchUsers` |
| Friends | `getFriends` |
| Chats | `getChats` |
| Messages | `getMessages`, `sendMessage`, `sendMessageWithAttachments` |
| Media | `uploadAvatar`, `uploadFiles` |

---

## WebSocketClient — real-time

Połączenie WebSocket jest nawiązywane na poziomie `ChatActivity` i utrzymywane przez cały czas pobytu na ekranie czatu.

### Protokół autentykacji

Token nigdy nie trafia do URL — przekazywany jest przez wiadomość po nawiązaniu połączenia:

```
1. Klient otwiera  ws://<host>/ws
2. Serwer wysyła   { "type": "auth:required" }
3. Klient odsyła   { "type": "auth", "token": "<sessionToken>" }
4. Serwer wysyła   { "type": "connected", "userId": "…" }
   lub zamyka z kodem 4401 przy błędnym tokenie
```

### Zdarzenia serwera

| Typ | Payload | Opis |
|---|---|---|
| `message:created` | `{ …Message }` | Nowa wiadomość w czacie |
| `message:updated` | `{ chatId, messageId, message }` | Edycja wiadomości |
| `message:deleted` | `{ chatId, messageId }` | Usunięcie wiadomości |

`WebSocketClient` filtruje zdarzenia po `chatId` — jedno połączenie obsługuje cały kanał użytkownika, ale Activity widzi tylko zdarzenia ze swojego czatu.

### Callbacki

```kotlin
wsClient.onMessageReceived = { msg -> runOnUiThread { /* dodaj do listy */ } }
wsClient.onMessageUpdated  = { msg -> runOnUiThread { /* zaktualizuj */ } }
wsClient.onMessageDeleted  = { id  -> runOnUiThread { /* usuń */ } }
wsClient.onConnected       = { runOnUiThread { /* pokaż status */ } }
wsClient.onDisconnected    = { runOnUiThread { /* pokaż brak połączenia */ } }
```

---

## SessionManager — bezpieczna sesja

`SessionManager` przechowuje dane sesji i preferencje w `EncryptedSharedPreferences` z szyfrowaniem AES-256-GCM (klucz w Android Keystore).

### Przechowywane wartości

| Klucz | Opis |
|---|---|
| `server_url` | Adres webowy instancji ouija |
| `api_url` | Adres API backendu |
| `media_url` | Bazowy URL CDN dla plików i awatarów |
| `user_id` | ID zalogowanego użytkownika |
| `nickname` | Nick użytkownika |
| `email` | Email użytkownika |
| `session_token` | Token autoryzacyjny Bearer |
| `avatar_url` | URL awatara (cache) |
| `app_theme` | Wybrany motyw (np. `Theme.Ouija.Midnight`) |

### Zachowanie przy wylogowaniu

`clearSession()` czyści wszystkie dane, ale **zachowuje** motyw i adresy serwerów — użytkownik nie musi ich ponownie konfigurować po wylogowaniu.

---

## Modele danych

Modele w `Models.kt` odzwierciedlają odpowiedzi API:

```kotlin
data class User(id, email, nickname, status, avatarUrl)
data class Chat(id, name, type, users: List<ChatUser>)
data class ChatUser(chatId, userId, role, user: User?)
data class Message(id, chatId, content, senderId, createdAt, updatedAt, attachments, sender)
data class Attachment(id, messageId, url, type, name)
data class Friendship(id, requesterId, addresseeId, status, requester, addressee)
data class LoginResponse(token, user: User)
data class RegisterRequest(email, password, nickname)
data class SendMessageRequest(content, attachments)
data class AttachmentInput(url, type, name)
```

---

## Uprawnienia Android

Zadeklarowane w `AndroidManifest.xml`:

| Uprawnienie | Powód |
|---|---|
| `INTERNET` | Komunikacja z serwerem ouija |
| `READ_MEDIA_IMAGES` (API 33+) | Wybór zdjęć do wysłania |
| `READ_MEDIA_VIDEO` (API 33+) | Wybór wideo do wysłania |
| `READ_MEDIA_AUDIO` (API 33+) | Wybór plików audio |
| `READ_EXTERNAL_STORAGE` (do API 32) | Dostęp do plików na starszych wersjach Androida |

`android:usesCleartextTraffic="true"` jest ustawione, aby umożliwić połączenia `http://` z lokalnym serwerem deweloperskim. Na produkcji zalecane jest połączenie HTTPS.
