# EcoMoney Placeholder

Alle verfügbaren PlaceholderAPI-Placeholder für EcoMoney.
Funktioniert mit Java UND Bedrock Spielern!

## Voraussetzungen

- [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/)
- (Optional) [Floodgate](https://geysermc.org/) für Bedrock-Unterstützung

## Guthaben Placeholder

| Placeholder | Beschreibung | Beispiel Output |
|-------------|--------------|-----------------|
| `%ecomoney_balance%` | Formatiertes Guthaben | `1,234.56` |
| `%ecomoney_balance_raw%` | Rohes Guthaben | `1234.56` |
| `%ecomoney_balance_formatted%` | Mit Währungssymbol | `$1,234.56` |
| `%ecomoney_balance_short%` | Kurzformat | `1.2K`, `5.3M`, `2.1B` |
| `%ecomoney_balance_int%` | Ganzzahl | `1234` |
| `%ecomoney_balance_long%` | Long-Wert | `1234` |
| `%ecomoney_balance_commas%` | Deutsche Formatierung | `1.234,56` |
| `%ecomoney_balance_dots%` | US Formatierung | `1,234.56` |

## Währungs Placeholder

| Placeholder | Beschreibung | Beispiel Output |
|-------------|--------------|-----------------|
| `%ecomoney_currency%` | Währungsname (Plural) | `Coins` |
| `%ecomoney_currency_singular%` | Währungsname (Singular) | `Coin` |
| `%ecomoney_currency_symbol%` | Währungssymbol | `$` |

## Top-Liste Placeholder

| Placeholder | Beschreibung | Beispiel Output |
|-------------|--------------|-----------------|
| `%ecomoney_top_name_1%` | Name auf Platz 1 | `Steve` |
| `%ecomoney_top_name_2%` | Name auf Platz 2 | `Alex` |
| `%ecomoney_top_name_<N>%` | Name auf Platz N | `...` |
| `%ecomoney_top_balance_1%` | Guthaben Platz 1 (roh) | `999999.99` |
| `%ecomoney_top_balance_formatted_1%` | Guthaben Platz 1 (formatiert) | `$999,999.99` |
| `%ecomoney_top_balance_short_1%` | Guthaben Platz 1 (kurz) | `1.0M` |
| `%ecomoney_top_rank%` | Eigene Platzierung | `42` |

## Sonstige Placeholder

| Placeholder | Beschreibung | Beispiel Output |
|-------------|--------------|-----------------|
| `%ecomoney_has_account%` | Hat der Spieler ein Konto? | `true` / `false` |

## Beispiel-Verwendung

### Scoreboard (z.B. mit TAB oder AnimatedScoreboard)

```yaml
lines:
  - "&6Guthaben: &a%ecomoney_balance_formatted%"
  - "&7Rang: &e#%ecomoney_top_rank%"
```

### Hologramm (z.B. mit DecentHolograms)

```
&6&l⭐ TOP REICHE SPIELER ⭐
&e#1 &f%ecomoney_top_name_1% &7- &a%ecomoney_top_balance_short_1%
&e#2 &f%ecomoney_top_name_2% &7- &a%ecomoney_top_balance_short_2%
&e#3 &f%ecomoney_top_name_3% &7- &a%ecomoney_top_balance_short_3%
```

### Tab-Liste (z.B. mit TAB Plugin)

```yaml
header:
  - "&6Zynaida.net &7| &a%ecomoney_balance_short% %ecomoney_currency%"
```

### Chat-Format (z.B. mit LuckPerms)

```
[%ecomoney_balance_short%] {prefix} {name}: {message}
```

## Bedrock-Spieler Hinweise

Die Placeholder funktionieren automatisch mit Bedrock-Spielern über Geyser/Floodgate:

- Kurzformat (`_short`) ist ideal für kleinere Bildschirme
- Namen werden ohne Floodgate-Prefix angezeigt
- Alle Guthaben-Operationen sind identisch zu Java-Spielern

## Kurzformat Erklärung

| Wert | Kurzformat |
|------|------------|
| < 1.000 | `123.45` |
| 1.000 - 999.999 | `1.5K` |
| 1.000.000 - 999.999.999 | `1.5M` |
| 1.000.000.000 - 999.999.999.999 | `1.5B` |
| > 1.000.000.000.000 | `1.5T` |

## Performance

- Top-Liste wird 30 Sekunden gecacht
- Guthaben werden aus dem Memory-Cache gelesen
- Optimal für hochfrequente Abfragen (Scoreboards, TAB, etc.)
