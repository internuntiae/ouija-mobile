# Motywy

ouija mobile obsługuje 6 motywów kolorystycznych. Wybór zapisywany jest w `SessionManager` i aplikowany przy każdym uruchomieniu Activity przez `BaseActivity`.

## Dostępne motywy

| Klucz motywu | Nazwa | Opis |
|---|---|---|
| `Theme.Ouija` | Dark (domyślny) | Ciemne tło, stonowane akcenty |
| `Theme.Ouija.Light` | Light | Jasne tło, ciemny tekst |
| `Theme.Ouija.Midnight` | Midnight Blue | Głęboki granat |
| `Theme.Ouija.Forest` | Forest | Ciemna zieleń |
| `Theme.Ouija.Rose` | Rose | Ciepłe różowe akcenty |
| `Theme.Ouija.Solarized` | Solarized | Klasyczna paleta Solarized |

## Jak działa system motywów

Wszystkie motywy dziedziczą po `Theme.Ouija.Base`, który bazuje na `Theme.Material3.DayNight.NoActionBar`. Zamiast hardkodowanych kolorów, layouty odwołują się do **atrybutów niestandardowych** (`?attr/customPrimary` itd.), a każdy motyw dostarcza własnych wartości tych atrybutów.

### Atrybuty kolorystyczne

| Atrybut | Użycie |
|---|---|
| `customWindowBackground` | Tło ekranów |
| `customSurface` | Tło dolnej nawigacji, kart |
| `customCardBackground` | Tło kart, bąbelków wiadomości |
| `customInputBackground` | Tło pól tekstowych |
| `customTextPrimary` | Główny kolor tekstu |
| `customTextSecondary` | Tekst pomocniczy |
| `customTextTertiary` | Tekst trzeciorzędny (czas, status) |
| `customHintColor` | Placeholder w polach input |
| `customPrimary` | Kolor akcentu (przyciski, ikony) |
| `customOnPrimary` | Tekst na tle primaryColor |
| `customDangerBackground` | Tło przycisku destruktywnego |
| `customDangerStroke` | Obramowanie przycisku destruktywnego |
| `customDangerText` | Tekst przycisku destruktywnego |

### Aplikowanie motywu

`BaseActivity` nadpisuje `setContentView()` i przed inflacją layoutu wywołuje `setTheme()`:

```kotlin
override fun setContentView(layoutResID: Int) {
    applySelectedTheme()
    super.setContentView(layoutResID)
}
```

Zmiana motywu w `ProfileActivity` restartuje Activity, aby nowy motyw był widoczny natychmiast.

## Splash screen

Splash screen (`Theme.App.Starting`) zawsze używa ciemnego tła (`#111111`) niezależnie od wybranego motywu, aby uniknąć mignięcia białego ekranu przy starcie.