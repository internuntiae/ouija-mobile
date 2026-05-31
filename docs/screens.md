# Ekrany i nawigacja

## Flow nawigacyjny

```
Uruchomienie aplikacji
        │
        ▼
ServerConfigActivity  ◄──── (brak zapisanego URL serwera)
        │
        │ URL zwalidowany, pobrano /urls
        ▼
LoginActivity  ◄──────────── (sesja wygasła / wylogowanie)
        │
        ├── [Nowe konto] ──► RegisterActivity ──► LoginActivity
        │
        │ Zalogowano
        ▼
ChatsActivity (główny ekran)
        │
        ├── [Wybór czatu] ──► ChatActivity
        │
        └── [Dolna nawigacja: Profil] ──► ProfileActivity
                                              │
                                              └── [Zmień serwer] ──► ServerConfigActivity
```

---

## ServerConfigActivity

Pierwszy ekran po instalacji. Wyświetlany gdy nie ma zapisanego adresu serwera lub gdy użytkownik chce go zmienić.

**Co robi:**
- Pobiera adres instancji ouija wpisany przez użytkownika
- Wysyła żądanie GET do `<adres>/urls`, które zwraca `{ web, api, media }`
- Zapisuje wszystkie trzy adresy w `SessionManager`
- Przekierowuje do `LoginActivity`

**Domyślny adres:** `http://10.0.2.2:3000` (adres hosta z emulatora Androida)

**Walidacja:** akceptuje tylko adresy zaczynające się od `http://` lub `https://`.

---

## LoginActivity

Logowanie istniejącym kontem.

**Pola:** nick, hasło

**Co robi:**
- Wywołuje `ApiClient.login(nickname, password)`
- Przy sukcesie: zapisuje sesję przez `SessionManager.saveSession()`, przechodzi do `ChatsActivity`
- Przy błędzie 401: wyświetla komunikat `"Invalid credentials"`

**Linki:** → `RegisterActivity` (przycisk „Zarejestruj się")

---

## RegisterActivity

Tworzenie nowego konta.

**Pola:** nick, email (opcjonalny), hasło

**Co robi:**
- Wywołuje `ApiClient.register(email, password, nickname)`
- Przy sukcesie: wraca do `LoginActivity` z informacją o pomyślnej rejestracji
- Wyświetla błędy walidacji zwrócone przez API

**Linki:** → `LoginActivity` (przycisk „Masz już konto?")

---

## ChatsActivity

Główny ekran aplikacji. Wyświetla listę wszystkich czatów użytkownika.

**Funkcje:**
- Lista czatów z awatarami uczestników i podglądem ostatniej wiadomości
- Wyszukiwarka czatów (filtruje po nazwie grupy lub nickach uczestników)
- Dolna nawigacja: Czaty / Profil
- Pull-to-refresh nie jest zaimplementowany — lista odświeżana przy każdym powrocie na ekran

**Adapter:** `ChatAdapter` (RecyclerView) z `DiffUtil` dla wydajnych aktualizacji listy

**Awatary:** ładowane asynchronicznie przez ExecutorService, wyświetlane jako `Bitmap` w `ImageView`

---

## ChatActivity

Widok pojedynczego czatu z obsługą wiadomości w czasie rzeczywistym.

**Funkcje:**
- Lista wiadomości (wysłane / odebrane — osobne layouty item)
- Wysyłanie wiadomości tekstowych
- Wysyłanie załączników (zdjęcia, wideo, pliki)
- Pobieranie i zapisywanie obrazów do galerii
- WebSocket — wiadomości pojawiają się w czasie rzeczywistym bez odświeżania

**Attachmenty:**
- Wybór pliku przez `ActivityResultContracts.GetContent`
- Upload przez `ApiClient.uploadFiles()` → zwraca URL → wysłanie wiadomości z `attachments`
- Obrazy wyświetlane jako miniatury, pliki jako nazwy z przyciskiem pobierania

**Adapter:** `MessageAdapter` (RecyclerView) z `DiffUtil`

**Typy wiadomości:**
- `item_message_sent.xml` — wiadomości wysłane przez bieżącego użytkownika
- `item_message_received.xml` — wiadomości od innych uczestników

**WebSocket lifecycle:**
- `wsClient.connect(chatId)` — w `onResume`
- `wsClient.disconnect()` — w `onPause`

---

## ProfileActivity

Profil bieżącego użytkownika z ustawieniami.

**Sekcje:**

**Profil:**
- Awatar (z możliwością zmiany przez galerię lub aparat)
- Nick, email, status
- Inicjały jako fallback gdy brak awatara

**Znajomi:**
- Lista zaakceptowanych znajomości (tylko `status == "ACCEPTED"`)
- Adapter: `FriendAdapter` (RecyclerView)
- Wyszukiwanie użytkowników i wysyłanie zaproszeń

**Ustawienia:**
- Wybór motywu (6 opcji) — zmiana natychmiastowa, Activity restartowana
- Zmiana serwera — przekierowanie do `ServerConfigActivity`
- Wylogowanie — czyści sesję, przekierowanie do `LoginActivity`

**Dolna nawigacja:** Czaty / Profil (wspólna z `ChatsActivity`)
