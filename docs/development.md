# Przewodnik deweloperski

## Wymagania wstępne

- [Android Studio](https://developer.android.com/studio) Hedgehog (2023.1.1) lub nowszy
- Android SDK — API 21 (min) oraz API 34 (target/compile)
- JDK 17+ (wbudowany w Android Studio)
- Działająca instancja serwera ouija (lokalnie lub zdalnie)

---

## Uruchamianie lokalnie

### 1. Sklonuj repozytorium

```bash
git clone https://github.com/zjezdzalka/ouija-mobile.git
cd ouija-mobile
```

### 2. Otwórz projekt

Otwórz katalog `source/` w Android Studio (`File → Open`). IDE zsynchronizuje Gradle automatycznie.

### 3. Uruchom serwer ouija

Aplikacja mobilna wymaga działającego backendu. Najprościej przez Docker:

```bash
# W katalogu projektu ouija (backend)
docker compose up
```

Serwer startuje na `http://localhost:3000` (web) i `http://localhost:3001` (API).

### 4. Uruchom aplikację

**Na emulatorze:**

Adres hosta jest dostępny z emulatora pod `http://10.0.2.2:3000`. Wybierz emulator w Android Studio i naciśnij Run (▶).

**Na urządzeniu fizycznym:**

Urządzenie i komputer muszą być w tej samej sieci lokalnej. Wpisz adres IP komputera zamiast `10.0.2.2`, np. `http://192.168.1.100:3000`.

---

## Budowanie APK

### Debug APK

```bash
cd source
./gradlew assembleDebug
```

Plik APK zostanie wygenerowany w:
```
app/build/outputs/apk/debug/app-debug.apk
```

### Release APK

```bash
./gradlew assembleRelease
```

> **Uwaga:** Build release wymaga skonfigurowanego keystora do podpisywania. Bez konfiguracji podpisywania w `build.gradle.kts` polecenie zakończy się błędem.

---

## Struktura zależności

Zależności zarządzane są przez Gradle Version Catalog (`gradle/libs.versions.toml`):

| Biblioteka | Wersja | Użycie |
|---|---|---|
| `androidx.core:core-ktx` | 1.10.1 | Rozszerzenia Kotlin dla Android |
| `androidx.appcompat:appcompat` | 1.6.1 | Kompatybilność wsteczna |
| `com.google.android.material` | 1.10.0 | Komponenty Material3 |
| `androidx.constraintlayout` | 2.1.4 | Layouty |
| `androidx.activity:activity-ktx` | 1.8.0 | `ActivityResultContracts` |
| OkHttp3 | (via Gradle) | HTTP i WebSocket |
| Gson | (via Gradle) | Serializacja JSON |
| `androidx.security:security-crypto` | (via Gradle) | `EncryptedSharedPreferences` |

---

## Debugowanie sieci

### Cleartext traffic

`AndroidManifest.xml` zawiera `android:usesCleartextTraffic="true"`, co umożliwia połączenia `http://` z lokalnym serwerem. Na produkcji zaleca się HTTPS z odpowiednim certyfikatem.

### Logi WebSocket

```bash
adb logcat -s WebSocketClient
```

### Logi ogólne aplikacji

```bash
adb logcat -s ouija_mobile
```

---

## Typowe problemy

**„Cannot connect to server"** na urządzeniu fizycznym
→ Sprawdź czy urządzenie i komputer są w tej samej sieci. Użyj adresu IP komputera zamiast `10.0.2.2`.

**Cleartext traffic blocked**
→ Upewnij się, że `android:usesCleartextTraffic="true"` jest w `<application>` w `AndroidManifest.xml`. Dotyczy tylko połączeń `http://`.

**Gradle sync failure**
→ Sprawdź wersję JDK: `File → Project Structure → SDK Location → JDK Location`. Wymagany JDK 17+.

**EncryptedSharedPreferences error przy zmianie debug/release**
→ Odinstaluj aplikację z urządzenia i zainstaluj ponownie — preferencje zaszyfrowane starym kluczem są niekompatybilne po zmianie środowiska.

---

## Testy

Projekt zawiera szablony testów wygenerowane przez Android Studio:

```bash
# Testy jednostkowe (JVM)
./gradlew test

# Testy instrumentowane (wymagają urządzenia/emulatora)
./gradlew connectedAndroidTest
```

Testy jednostkowe: `app/src/test/java/com/example/ouija_mobile/ExampleUnitTest.kt`

Testy instrumentowane: `app/src/androidTest/java/com/example/ouija_mobile/ExampleInstrumentedTest.kt`
