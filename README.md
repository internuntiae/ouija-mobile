# ouija mobile 📱

> Natywny klient Android dla komunikatora ouija — zbudowany w Kotlin.

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## Czym jest ouija mobile?

ouija mobile to natywna aplikacja Android będąca klientem do samohostedowanego komunikatora [ouija](https://github.com/zjezdzalka/ouija). Łączy się z dowolną instancją serwera ouija — zarówno lokalną, jak i publiczną. Obsługuje czaty prywatne i grupowe, załączniki mediów, zarządzanie znajomymi oraz komunikację w czasie rzeczywistym przez WebSocket.

Wiadomości są lokalnie cache'owane w bazie SQLite (Room), co umożliwia natychmiastowe wyświetlenie historii bez oczekiwania na odpowiedź serwera.

## Demo — publiczna instancja

Aplikacja domyślnie sugeruje adres emulatora (`http://10.0.2.2:3000`), ale możesz podłączyć ją do dowolnej instancji ouija — np. publicznej pod:

**https://ouija.rytui.dev/**

Wpisz ten adres na ekranie konfiguracji serwera, aby od razu zacząć korzystać bez lokalnego setupu.

## Stack technologiczny

| Warstwa          | Technologia                                    |
|------------------|------------------------------------------------|
| Język            | Kotlin                                         |
| UI               | XML Layouts, Material3, RecyclerView           |
| Sieć (HTTP)      | OkHttp3 + Gson                                 |
| Sieć (WebSocket) | OkHttp3 WebSocket                              |
| Baza lokalna     | Room (SQLite) — cache wiadomości               |
| Sesja            | EncryptedSharedPreferences (AES-256-GCM)       |
| Min. Android     | API 21 (Android 5.0 Lollipop)                  |
| Build            | Gradle 9.2 + AGP                               |

---

## Szybki start

### Wymagania

- [Android Studio](https://developer.android.com/studio) Hedgehog lub nowszy
- Android SDK z API 21+
- Urządzenie lub emulator z Androidem 5.0+

### 1. Sklonuj repozytorium

```bash
git clone https://github.com/zjezdzalka/ouija-mobile.git
cd ouija-mobile
```

### 2. Otwórz projekt w Android Studio

Otwórz katalog `source/` jako projekt Android Studio. IDE automatycznie zsynchronizuje zależności Gradle.

### 3. Uruchom aplikację

Wybierz urządzenie lub emulator i naciśnij **Run** (▶) lub:

```bash
./gradlew installDebug
```

### 4. Skonfiguruj serwer

Przy pierwszym uruchomieniu pojawi się ekran konfiguracji serwera. Wpisz adres instancji ouija:

- **Emulator lokalny:** `http://10.0.2.2:3000`
- **Urządzenie fizyczne (lokalna sieć):** `http://192.168.x.x:3000`
- **Instancja publiczna:** `https://ouija.rytui.dev`

Aplikacja automatycznie pobierze adresy API i CDN z endpointu `/urls`.

---

## Ekrany aplikacji

| Ekran                 | Klasa                    | Opis                                              |
|-----------------------|--------------------------|---------------------------------------------------|
| Konfiguracja serwera  | `ServerConfigActivity`   | Ustawienie adresu instancji ouija                 |
| Logowanie             | `LoginActivity`          | Logowanie nickiem i hasłem                        |
| Rejestracja           | `RegisterActivity`       | Tworzenie konta (nick, email, hasło)              |
| Lista czatów          | `ChatsActivity`          | Wszystkie czaty z wyszukiwarką i dolną nawigacją  |
| Czat                  | `ChatActivity`           | Widok wiadomości z WebSocket i załącznikami       |
| Profil                | `ProfileActivity`        | Profil użytkownika, znajomi, motyw, zmiana serwera|

---

## Dokumentacja

| Dokument                           | Opis                                                    |
|------------------------------------|---------------------------------------------------------|
| [Architektura](docs/architecture.md) | Struktura klas, przepływ danych, lokalna baza, WebSocket |
| [Ekrany i nawigacja](docs/screens.md) | Opis każdego ekranu i flow nawigacyjny               |
| [Motywy](docs/themes.md)           | System motywów — 6 palet kolorystycznych               |
| [Przewodnik deweloperski](docs/development.md) | Budowanie, uruchamianie, debugowanie        |

---

## Struktura projektu

```
source/
├── app/
│   └── src/
│       └── main/
│           ├── java/com/example/ouija_mobile/
│           │   ├── ApiClient.kt          # Klient HTTP (OkHttp3 + Gson)
│           │   ├── WebSocketClient.kt    # Klient WebSocket (real-time)
│           │   ├── MessageDatabase.kt    # Lokalna baza danych Room (cache wiadomości)
│           │   ├── SessionManager.kt     # Sesja i preferencje (zaszyfrowane)
│           │   ├── BaseActivity.kt       # Bazowa Activity z obsługą motywów
│           │   ├── Models.kt             # Modele danych (User, Chat, Message…)
│           │   ├── ServerConfigActivity.kt
│           │   ├── LoginActivity.kt
│           │   ├── RegisterActivity.kt
│           │   ├── ChatsActivity.kt
│           │   ├── ChatActivity.kt
│           │   └── ProfileActivity.kt
│           └── res/
│               ├── layout/               # Layouty XML dla każdego ekranu
│               ├── values/
│               │   ├── themes.xml        # 6 motywów (Dark, Light, Midnight…)
│               │   ├── colors.xml        # Palety kolorów
│               │   └── strings.xml       # Zasoby tekstowe
│               └── menu/
│                   └── bottom_nav_menu.xml
└── gradle/
    └── libs.versions.toml                # Wersje zależności (Version Catalog)
```

---

## Zespół

| Osoba                  | Rola                                                                         |
|------------------------|------------------------------------------------------------------------------|
| **Marek Zjeżdżałka**   | Backend & infrastruktura — serwer ouija, WebSocket, lokalna baza danych Room |
| **Filip Maciejewski**  | Android — UI/UX, layouty XML, motywy, nawigacja                              |
| **Gustaw Grześkowiak** | Android — logika sieciowa, API, deployment                                   |

---

## Licencja

ouija mobile jest dostępna na licencji [MIT](LICENSE).