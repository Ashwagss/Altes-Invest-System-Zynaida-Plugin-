# EcoMoney - Sichere Economy für Minecraft

Ein sicheres, performantes Economy-Plugin für Paper 1.21 - 1.21.4 mit vollständiger Vault-Integration.

## Features

### Sicherheit
- **Kein negatives Guthaben möglich** - Mehrfache Überprüfungen verhindern Minus-Guthaben
- **Maximales Guthaben** - Verhindert Integer/Decimal Overflow (max. 999.999.999.999,99)
- **Thread-Safe Transaktionen** - Alle Operationen sind mit Locks abgesichert
- **Rate Limiting** - Verhindert Spam-Exploits bei Überweisungen (100ms Cooldown)
- **Atomare Transfers** - Bei Fehlern wird automatisch zurückgerollt
- **Betrag-Validierung** - Maximal 2 Dezimalstellen, keine negativen Werte
- **Deadlock-Prevention** - Konsistente Lock-Reihenfolge bei Transfers

### Datenspeicherung
- **YAML** - Einfache Datei-basierte Speicherung mit Cache
- **MySQL** - Skalierbare Datenbank mit HikariCP Connection Pool
- **Auto-Save** - Automatisches Speichern in konfigurierbarem Intervall
- **Cache-System** - Schneller Zugriff auf Guthaben-Daten

### API & Integration
- **Vault Economy Provider** - Vollständige Vault-Integration
- **Custom Events** - `EcoMoneyTransactionEvent` für andere Plugins
- **TransactionResult** - Detaillierte Rückmeldungen bei Transaktionen
- **Thread-Safe API** - Sicher aus anderen Plugins verwendbar

## Installation

1. Lade das Plugin in den `plugins` Ordner
2. (Optional) Installiere [Vault](https://www.spigotmc.org/resources/vault.34315/) für Plugin-Kompatibilität
3. Starte den Server
4. Konfiguriere in `plugins/EcoMoney/config.yml`

## Befehle

| Befehl | Beschreibung | Berechtigung |
|--------|--------------|--------------|
| `/balance [Spieler]` | Zeigt Guthaben an | `ecomoney.balance` |
| `/pay <Spieler> <Betrag>` | Geld überweisen | `ecomoney.pay` |
| `/baltop [Seite]` | Reichsten Spieler | `ecomoney.baltop` |
| `/ecoadmin set <Spieler> <Betrag>` | Guthaben setzen | `ecomoney.admin` |
| `/ecoadmin give <Spieler> <Betrag>` | Geld geben | `ecomoney.admin` |
| `/ecoadmin take <Spieler> <Betrag>` | Geld abziehen | `ecomoney.admin` |
| `/ecoadmin reset <Spieler>` | Guthaben zurücksetzen | `ecomoney.admin` |
| `/ecoadmin reload` | Config neu laden | `ecomoney.admin` |

## Berechtigungen

| Berechtigung | Beschreibung | Standard |
|--------------|--------------|----------|
| `ecomoney.*` | Alle Berechtigungen | OP |
| `ecomoney.balance` | Eigenes Guthaben | Alle |
| `ecomoney.balance.others` | Guthaben anderer | OP |
| `ecomoney.pay` | Geld senden | Alle |
| `ecomoney.baltop` | Top-Liste | Alle |
| `ecomoney.admin` | Admin-Befehle | OP |

## API Verwendung

### Aus anderen Plugins

```java
// Plugin-Instanz holen
EcoMoney ecoMoney = (EcoMoney) Bukkit.getPluginManager().getPlugin("EcoMoney");
EcoMoneyAPI api = ecoMoney.getAPI();

// Guthaben abfragen
BigDecimal balance = api.getBalance(player.getUniqueId());

// Geld einzahlen
TransactionResult result = api.deposit(player.getUniqueId(), 
    new BigDecimal("100.00"), "Shop-Verkauf");

if (result.isSuccess()) {
    // Erfolgreich
} else {
    player.sendMessage("Fehler: " + result.getMessage());
}

// Geld abziehen
TransactionResult result = api.withdraw(player.getUniqueId(), 
    new BigDecimal("50.00"), "Shop-Kauf");

// Überweisung
TransactionResult result = api.transfer(
    fromPlayer.getUniqueId(), 
    toPlayer.getUniqueId(), 
    new BigDecimal("100.00"), 
    "Handel"
);

// Prüfen ob genug Geld vorhanden
if (api.has(player.getUniqueId(), new BigDecimal("100"))) {
    // Hat genug
}
```

### Event Listener

```java
@EventHandler
public void onTransaction(EcoMoneyTransactionEvent event) {
    // Transaktion abbrechen
    if (shouldCancel) {
        event.setCancelled(true);
        return;
    }
    
    // Informationen abrufen
    UUID player = event.getPlayer();
    TransactionType type = event.getType();
    BigDecimal oldBalance = event.getOldBalance();
    BigDecimal newBalance = event.getNewBalance();
}
```

### Über Vault (empfohlen für Kompatibilität)

```java
RegisteredServiceProvider<Economy> rsp = 
    Bukkit.getServicesManager().getRegistration(Economy.class);
Economy econ = rsp.getProvider();

// Guthaben abfragen
double balance = econ.getBalance(player);

// Geld abziehen
EconomyResponse r = econ.withdrawPlayer(player, 100.0);
if (r.transactionSuccess()) {
    // Erfolgreich
}

// Geld einzahlen
EconomyResponse r = econ.depositPlayer(player, 100.0);
```

## Konfiguration

```yaml
storage:
  type: yaml  # oder "mysql"
  auto-save-interval: 300
  mysql:
    host: localhost
    port: 3306
    database: ecomoney
    username: root
    password: ""
    useSSL: false

economy:
  starting-balance: 1000.0
  currency-symbol: "$"
  currency-name: "Coin"
  currency-name-plural: "Coins"
  format: "{symbol}{amount}"
  min-transfer: 0.01
  max-transfer: 1000000000.0
```

## Sicherheitsmaßnahmen

### Gegen Exploits geschützt

1. **Negatives Guthaben** - Unmöglich durch mehrfache Validierung
2. **Race Conditions** - Thread-Safe mit ReentrantLocks
3. **Overflow** - Maximum bei ~1 Billion begrenzt
4. **Spam-Transfers** - 100ms Cooldown zwischen Transaktionen
5. **Self-Pay** - Selbst-Überweisungen sind blockiert
6. **Invalid Amounts** - Negative/ungültige Beträge werden abgelehnt
7. **Decimal Precision** - Maximal 2 Dezimalstellen
8. **Atomare Transaktionen** - Rollback bei Fehlern

### Technische Details

- **BigDecimal** statt double für präzise Berechnungen
- **ConcurrentHashMap** für Thread-Safe Caching
- **HikariCP** für effizientes Database Pooling
- **Consistent Lock Ordering** verhindert Deadlocks
- **Prepared Statements** gegen SQL Injection

## Build

```bash
mvn clean package
```

Die JAR-Datei befindet sich dann in `target/EcoMoney-1.0.0.jar`

## Support

Bei Fragen oder Problemen: [Zynaida.net Discord]

---

Entwickelt für Zynaida.net SMP Citybuild
