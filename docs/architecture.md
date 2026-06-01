# Architektura

## Overview

ouija mobile to aplikacja z lokalnym cache'em wiadomości. Historia czatów przechowywana jest w lokalnej bazie SQLite (Room) i wyświetlana natychmiast przy otwarciu czatu — bez oczekiwania na sieć. Żądania HTTP i zdarzenia WebSocket synchronizują dane z serwerem ouija w tle.

```
┌─────────────────────────────────────┐
│            Activity / UI            │
│  (ChatsActivity, ChatActivity, …)   │
└──────────┬───────────────┬──────────┘
           │               │
   ┌───────┴──────┐  ┌─────┴──────────┐
   │              │  │                │
   ▼              ▼  ▼                ▼
ApiClient   WebSocketClient     MessageDatabase
(OkHttp3)   (OkHttp3 WS)        (Room / SQLite)
   │              │                   │
   └──────┬───────┘         ouija_messages.db
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
| `MessageDatabase` | Lokalna baza SQLite (Room) — cache wiadomości z obsługą offline |
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

Połączenie WebSocket nawiązywane jest w `ChatActivity` i utrzymywane przez cały czas pobytu na ekranie czatu.

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

Każde odebrane zdarzenie jest automatycznie zapisywane / aktualizowane / usuwane w `MessageDatabase`, dzięki czemu lokalna baza pozostaje zsynchronizowana z serwerem.

### Callbacki

```kotlin
wsClient.onMessageReceived = { msg -> runOnUiThread { /* dodaj do listy i zapisz w DB */ } }
wsClient.onMessageUpdated  = { msg -> runOnUiThread { /* zaktualizuj w liście i DB */ } }
wsClient.onMessageDeleted  = { id  -> runOnUiThread { /* usuń z listy i DB */ } }
wsClient.onConnected       = { runOnUiThread { /* pokaż status */ } }
wsClient.onDisconnected    = { runOnUiThread { /* pokaż brak połączenia */ } }
```

---

## MessageDatabase — lokalna baza danych (Room)

`MessageDatabase` to jednowątkowy singleton Room oparty na SQLite. Przechowuje cache wiadomości per czat, umożliwiając natychmiastowe wyświetlenie historii przed załadowaniem danych z sieci.

### Schemat bazy

Plik bazy: `ouija_messages.db` (katalog wewnętrzny aplikacji, niedostępny bez root).

Tabela `messages`:

| Kolumna | Typ | Opis |
|---|---|---|
| `id` | TEXT (PK) | ID wiadomości z serwera |
| `chatId` | TEXT | ID czatu — klucz filtrowania |
| `senderId` | TEXT | ID nadawcy |
| `content` | TEXT (nullable) | Treść wiadomości |
| `sentAt` | TEXT | Czas wysłania (ISO 8601) |
| `editedAt` | TEXT (nullable) | Czas ostatniej edycji |
| `attachmentsJson` | TEXT | Lista załączników jako JSON (`List<Attachment>`) |

### Encja i konwersja

Room nie obsługuje natywnie `List<Attachment>`, dlatego lista serializowana jest do JSON przez `Converters` (Gson). Konwersja odbywa się automatycznie przy zapisie i odczycie.

```kotlin
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val senderId: String,
    val content: String?,
    val sentAt: String,
    val editedAt: String?,
    val attachmentsJson: String
)
```

`MessageEntity` posiada metody pomocnicze `toMessage(gson)` i `fromMessage(message, gson)` do konwersji między encją Room a modelem domenowym `Message`.

### DAO — operacje na danych

```kotlin
// Wszystkie wiadomości czatu posortowane chronologicznie
fun getMessagesForChat(chatId: String): List<MessageEntity>

// Wstaw lub zastąp (używane przy odbiorze z API i WebSocket)
fun insertOrReplace(message: MessageEntity)
fun insertOrReplaceAll(messages: List<MessageEntity>)

// Usuń konkretną wiadomość (zdarzenie message:deleted)
fun deleteById(messageId: String)

// Wyczyść cały cache czatu
fun clearChat(chatId: String)

// Liczba wiadomości w cache (debug/log)
fun countForChat(chatId: String): Int
```

### Strategia cache

1. **Otwarcie czatu:** `MessageDatabase` zwraca zapisane wiadomości natychmiast — UI wyświetla historię bez opóźnienia.
2. **Załadowanie z API:** `ApiClient.getMessages()` pobiera aktualne wiadomości z serwera i zapisuje je przez `insertOrReplaceAll()`, zastępując dane w cache.
3. **WebSocket:** nowe, edytowane i usunięte wiadomości aktualizują zarówno UI, jak i lokalną bazę.

### Singleton i bezpieczeństwo wątkowe

`MessageDatabase.getInstance(context)` zwraca jeden współdzielony egzemplarz (wzorzec double-checked locking z `@Volatile`). Wszystkie operacje DAO muszą być wywoływane poza wątkiem głównym (Room blokuje przy próbie użycia na UI thread).

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

`clearSession()` czyści wszystkie dane sesji, ale **zachowuje** motyw i adresy serwerów — użytkownik nie musi ich ponownie konfigurować po wylogowaniu. Lokalna baza wiadomości (`MessageDatabase`) **nie jest** czyszczona przy wylogowaniu — stare wiadomości zostaną zastąpione przy ponownym zalogowaniu przez `insertOrReplaceAll`.

---

## Modele danych

Modele w `Models.kt` odzwierciedlają odpowiedzi API:

```kotlin
data class User(id, email, nickname, status, avatarUrl)
data class Chat(id, name, type, users: List<ChatUser>)
data class ChatUser(chatId, userId, role, user: User?)
data class Message(id, chatId, senderId, content, sentAt, editedAt, attachments)
data class Attachment(id, messageId, url, type, name)
data class Friendship(userId, friendId, status, user: User, friend: User)
data class LoginResponse(token, user: User)
data class RegisterRequest(email, password, nickname)
data class SendMessageRequest(content, attachments)
data class AttachmentInput(url, type, name)
```

> **Uwaga:** pola `Message` używają `sentAt` i `editedAt` (nie `createdAt` / `updatedAt`). `MessageEntity` odzwierciedla te same nazwy.

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