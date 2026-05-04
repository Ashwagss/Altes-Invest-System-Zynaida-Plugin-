package net.zynaida.ecomoney.api;

import net.zynaida.ecomoney.EcoMoney;
import net.zynaida.ecomoney.api.events.EcoMoneyTransactionEvent;
import net.zynaida.ecomoney.api.events.TransactionResult;
import net.zynaida.ecomoney.api.events.TransactionType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Haupt-API für EcoMoney
 * Thread-safe und mit allen Sicherheitsmaßnahmen
 */
public class EcoMoneyAPI {

    private final EcoMoney plugin;
    
    // Maximale Geldmenge zur Verhinderung von Overflow
    private static final BigDecimal MAX_BALANCE = new BigDecimal("999999999999.99");
    private static final BigDecimal MIN_BALANCE = BigDecimal.ZERO;
    
    // Transaktionslocks für Thread-Safety
    private final Map<UUID, ReentrantLock> playerLocks = new ConcurrentHashMap<>();
    
    // Rate Limiting für Pay-Command (Anti-Exploit)
    private final Map<UUID, Long> lastTransaction = new ConcurrentHashMap<>();
    private static final long TRANSACTION_COOLDOWN_MS = 100; // 100ms zwischen Transaktionen

    public EcoMoneyAPI(EcoMoney plugin) {
        this.plugin = plugin;
    }

    /**
     * Holt oder erstellt einen Lock für einen Spieler
     */
    private ReentrantLock getLock(UUID uuid) {
        return playerLocks.computeIfAbsent(uuid, k -> new ReentrantLock(true));
    }

    /**
     * Validiert einen Geldbetrag
     * @return true wenn gültig
     */
    private boolean validateAmount(BigDecimal amount) {
        if (amount == null) return false;
        if (amount.compareTo(BigDecimal.ZERO) < 0) return false;
        if (amount.scale() > 2) return false; // Maximal 2 Dezimalstellen
        return true;
    }

    /**
     * Rundet einen Betrag auf 2 Dezimalstellen
     */
    private BigDecimal roundAmount(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Prüft Rate Limiting
     */
    private boolean checkRateLimit(UUID uuid) {
        Long lastTime = lastTransaction.get(uuid);
        long now = System.currentTimeMillis();
        
        if (lastTime != null && (now - lastTime) < TRANSACTION_COOLDOWN_MS) {
            return false; // Zu schnell
        }
        
        lastTransaction.put(uuid, now);
        return true;
    }

    /**
     * Holt das Guthaben direkt aus der DB (für kritische Cross-Server Operationen).
     * Bei YAML-Modus wird einfach der Cache zurückgegeben.
     */
    private BigDecimal getFreshBalance(UUID uuid) {
        return plugin.getDataManager().getBalanceFromDB(uuid);
    }

    // ==================== BALANCE OPERATIONS ====================

    /**
     * Gibt das aktuelle Guthaben eines Spielers zurück
     */
    public BigDecimal getBalance(UUID uuid) {
        if (uuid == null) return BigDecimal.ZERO;
        
        ReentrantLock lock = getLock(uuid);
        lock.lock();
        try {
            BigDecimal balance = plugin.getDataManager().getBalance(uuid);
            return balance != null ? balance : BigDecimal.ZERO;
        } finally {
            lock.unlock();
        }
    }

    public BigDecimal getBalance(OfflinePlayer player) {
        if (player == null) return BigDecimal.ZERO;
        return getBalance(player.getUniqueId());
    }

    /**
     * Holt das Guthaben direkt aus der Datenbank (bypassed Cache).
     * Nutze dies für Cross-Server-Anzeigen wie /balance.
     */
    public BigDecimal getBalanceFresh(UUID uuid) {
        if (uuid == null) return BigDecimal.ZERO;
        return getFreshBalance(uuid);
    }

    /**
     * Prüft ob ein Spieler ein Konto hat
     */
    public boolean hasAccount(UUID uuid) {
        if (uuid == null) return false;
        return plugin.getDataManager().hasAccount(uuid);
    }

    public boolean hasAccount(OfflinePlayer player) {
        if (player == null) return false;
        return hasAccount(player.getUniqueId());
    }

    /**
     * Erstellt ein Konto für einen Spieler
     */
    public boolean createAccount(UUID uuid) {
        if (uuid == null) return false;
        if (hasAccount(uuid)) return true;
        
        ReentrantLock lock = getLock(uuid);
        lock.lock();
        try {
            BigDecimal startBalance = BigDecimal.valueOf(plugin.getConfig().getDouble("economy.starting-balance", 0.0));
            startBalance = roundAmount(startBalance);
            
            // Sicherheitscheck
            if (startBalance.compareTo(MAX_BALANCE) > 0) {
                startBalance = MAX_BALANCE;
            }
            if (startBalance.compareTo(MIN_BALANCE) < 0) {
                startBalance = MIN_BALANCE;
            }
            
            return plugin.getDataManager().createAccount(uuid, startBalance);
        } finally {
            lock.unlock();
        }
    }

    public boolean createAccount(OfflinePlayer player) {
        if (player == null) return false;
        return createAccount(player.getUniqueId());
    }

    // ==================== TRANSACTION OPERATIONS ====================

    /**
     * Setzt das Guthaben eines Spielers (nur für Admin-Zwecke)
     * @return TransactionResult mit Erfolg/Fehler-Information
     */
    public TransactionResult setBalance(UUID uuid, BigDecimal amount, String reason) {
        if (uuid == null) {
            return new TransactionResult(false, "Ungültige Spieler-UUID");
        }
        
        if (amount == null) {
            return new TransactionResult(false, "Ungültiger Betrag");
        }
        
        // Betrag validieren und runden
        amount = roundAmount(amount);
        
        // Grenzen prüfen
        if (amount.compareTo(MIN_BALANCE) < 0) {
            return new TransactionResult(false, "Guthaben kann nicht negativ sein");
        }
        if (amount.compareTo(MAX_BALANCE) > 0) {
            return new TransactionResult(false, "Maximales Guthaben überschritten (" + formatMoney(MAX_BALANCE) + ")");
        }
        
        ReentrantLock lock = getLock(uuid);
        lock.lock();
        try {
            // Event auslösen
            BigDecimal oldBalance = getBalance(uuid);
            EcoMoneyTransactionEvent event = new EcoMoneyTransactionEvent(
                uuid, null, oldBalance, amount, TransactionType.SET, reason
            );
            Bukkit.getPluginManager().callEvent(event);
            
            if (event.isCancelled()) {
                return new TransactionResult(false, "Transaktion wurde abgebrochen");
            }
            
            // Konto erstellen falls nicht vorhanden
            if (!hasAccount(uuid)) {
                createAccount(uuid);
            }
            
            boolean success = plugin.getDataManager().setBalance(uuid, amount);
            
            if (success) {
                return new TransactionResult(true, "Guthaben gesetzt");
            } else {
                return new TransactionResult(false, "Datenbankfehler");
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Fügt Geld zu einem Konto hinzu
     */
    public TransactionResult deposit(UUID uuid, BigDecimal amount, String reason) {
        if (uuid == null) {
            return new TransactionResult(false, "Ungültige Spieler-UUID");
        }
        
        if (!validateAmount(amount)) {
            return new TransactionResult(false, "Ungültiger Betrag");
        }
        
        amount = roundAmount(amount);
        
        if (amount.compareTo(BigDecimal.ZERO) == 0) {
            return new TransactionResult(false, "Betrag muss größer als 0 sein");
        }
        
        ReentrantLock lock = getLock(uuid);
        lock.lock();
        try {
            BigDecimal currentBalance = getFreshBalance(uuid);
            BigDecimal newBalance = currentBalance.add(amount);

            // Overflow-Check
            if (newBalance.compareTo(MAX_BALANCE) > 0) {
                return new TransactionResult(false, "Maximales Guthaben würde überschritten werden");
            }
            
            // Event auslösen
            EcoMoneyTransactionEvent event = new EcoMoneyTransactionEvent(
                uuid, null, currentBalance, newBalance, TransactionType.DEPOSIT, reason
            );
            Bukkit.getPluginManager().callEvent(event);
            
            if (event.isCancelled()) {
                return new TransactionResult(false, "Transaktion wurde abgebrochen");
            }
            
            // Konto erstellen falls nicht vorhanden
            if (!hasAccount(uuid)) {
                createAccount(uuid);
            }
            
            boolean success = plugin.getDataManager().setBalance(uuid, newBalance);
            
            if (success) {
                return new TransactionResult(true, "Einzahlung erfolgreich", amount);
            } else {
                return new TransactionResult(false, "Datenbankfehler");
            }
        } finally {
            lock.unlock();
        }
    }

    public TransactionResult deposit(OfflinePlayer player, BigDecimal amount, String reason) {
        if (player == null) return new TransactionResult(false, "Ungültiger Spieler");
        return deposit(player.getUniqueId(), amount, reason);
    }

    /**
     * Zieht Geld von einem Konto ab
     */
    public TransactionResult withdraw(UUID uuid, BigDecimal amount, String reason) {
        if (uuid == null) {
            return new TransactionResult(false, "Ungültige Spieler-UUID");
        }
        
        if (!validateAmount(amount)) {
            return new TransactionResult(false, "Ungültiger Betrag");
        }
        
        amount = roundAmount(amount);
        
        if (amount.compareTo(BigDecimal.ZERO) == 0) {
            return new TransactionResult(false, "Betrag muss größer als 0 sein");
        }
        
        ReentrantLock lock = getLock(uuid);
        lock.lock();
        try {
            BigDecimal currentBalance = getFreshBalance(uuid);
            BigDecimal newBalance = currentBalance.subtract(amount);

            // KRITISCH: Niemals ins Minus!
            if (newBalance.compareTo(MIN_BALANCE) < 0) {
                return new TransactionResult(false, "Nicht genügend Guthaben");
            }
            
            // Event auslösen
            EcoMoneyTransactionEvent event = new EcoMoneyTransactionEvent(
                uuid, null, currentBalance, newBalance, TransactionType.WITHDRAW, reason
            );
            Bukkit.getPluginManager().callEvent(event);
            
            if (event.isCancelled()) {
                return new TransactionResult(false, "Transaktion wurde abgebrochen");
            }
            
            boolean success = plugin.getDataManager().setBalance(uuid, newBalance);
            
            if (success) {
                return new TransactionResult(true, "Abhebung erfolgreich", amount);
            } else {
                return new TransactionResult(false, "Datenbankfehler");
            }
        } finally {
            lock.unlock();
        }
    }

    public TransactionResult withdraw(OfflinePlayer player, BigDecimal amount, String reason) {
        if (player == null) return new TransactionResult(false, "Ungültiger Spieler");
        return withdraw(player.getUniqueId(), amount, reason);
    }

    /**
     * Prüft ob ein Spieler genug Geld hat
     */
    public boolean has(UUID uuid, BigDecimal amount) {
        if (uuid == null || amount == null) return false;
        if (amount.compareTo(BigDecimal.ZERO) < 0) return false;
        
        return getBalance(uuid).compareTo(amount) >= 0;
    }

    public boolean has(OfflinePlayer player, BigDecimal amount) {
        if (player == null) return false;
        return has(player.getUniqueId(), amount);
    }

    /**
     * Überweist Geld von einem Spieler zu einem anderen
     * THREAD-SAFE mit doppeltem Locking
     */
    public TransactionResult transfer(UUID from, UUID to, BigDecimal amount, String reason) {
        if (from == null || to == null) {
            return new TransactionResult(false, "Ungültige Spieler-UUID");
        }
        
        if (from.equals(to)) {
            return new TransactionResult(false, "Du kannst dir selbst kein Geld senden");
        }
        
        if (!validateAmount(amount)) {
            return new TransactionResult(false, "Ungültiger Betrag");
        }
        
        amount = roundAmount(amount);
        
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return new TransactionResult(false, "Betrag muss größer als 0 sein");
        }
        
        // Minimum Transfer Check
        BigDecimal minTransfer = BigDecimal.valueOf(plugin.getConfig().getDouble("economy.min-transfer", 0.01));
        if (amount.compareTo(minTransfer) < 0) {
            return new TransactionResult(false, "Mindestbetrag für Überweisungen: " + formatMoney(minTransfer));
        }
        
        // Rate Limiting
        if (!checkRateLimit(from)) {
            return new TransactionResult(false, "Bitte warte einen Moment zwischen Transaktionen");
        }
        
        // Locks in konsistenter Reihenfolge erhalten (verhindert Deadlocks)
        UUID first = from.compareTo(to) < 0 ? from : to;
        UUID second = from.compareTo(to) < 0 ? to : from;
        
        ReentrantLock lock1 = getLock(first);
        ReentrantLock lock2 = getLock(second);
        
        lock1.lock();
        try {
            lock2.lock();
            try {
                // Prüfen ob Absender genug hat (Fresh Read aus DB für Cross-Server Safety)
                BigDecimal fromBalance = getFreshBalance(from);
                if (fromBalance.compareTo(amount) < 0) {
                    return new TransactionResult(false, "Nicht genügend Guthaben");
                }

                // Prüfen ob Empfänger nicht über Maximum kommt (Fresh Read aus DB)
                BigDecimal toBalance = getFreshBalance(to);
                BigDecimal newToBalance = toBalance.add(amount);
                if (newToBalance.compareTo(MAX_BALANCE) > 0) {
                    return new TransactionResult(false, "Empfänger kann nicht mehr Geld empfangen (Maximum erreicht)");
                }
                
                // Event auslösen
                EcoMoneyTransactionEvent event = new EcoMoneyTransactionEvent(
                    from, to, fromBalance, amount, TransactionType.TRANSFER, reason
                );
                Bukkit.getPluginManager().callEvent(event);
                
                if (event.isCancelled()) {
                    return new TransactionResult(false, "Transaktion wurde abgebrochen");
                }
                
                // Konten erstellen falls nicht vorhanden
                if (!hasAccount(to)) {
                    createAccount(to);
                }
                
                // ATOMARE TRANSAKTION: Beide Updates müssen erfolgreich sein
                BigDecimal newFromBalance = fromBalance.subtract(amount);
                
                // Nochmal sicherstellen dass wir nicht ins Minus gehen
                if (newFromBalance.compareTo(MIN_BALANCE) < 0) {
                    return new TransactionResult(false, "Fehler: Würde negatives Guthaben verursachen");
                }
                
                boolean success1 = plugin.getDataManager().setBalance(from, newFromBalance);
                if (!success1) {
                    return new TransactionResult(false, "Datenbankfehler beim Absender");
                }
                
                boolean success2 = plugin.getDataManager().setBalance(to, newToBalance);
                if (!success2) {
                    // ROLLBACK: Geld zurückgeben
                    plugin.getDataManager().setBalance(from, fromBalance);
                    return new TransactionResult(false, "Datenbankfehler beim Empfänger - Transaktion rückgängig gemacht");
                }
                
                return new TransactionResult(true, "Überweisung erfolgreich", amount);
            } finally {
                lock2.unlock();
            }
        } finally {
            lock1.unlock();
        }
    }

    public TransactionResult transfer(OfflinePlayer from, OfflinePlayer to, BigDecimal amount, String reason) {
        if (from == null || to == null) return new TransactionResult(false, "Ungültiger Spieler");
        return transfer(from.getUniqueId(), to.getUniqueId(), amount, reason);
    }

    // ==================== UTILITY METHODS ====================

    /**
     * Formatiert einen Geldbetrag mit voller Genauigkeit
     */
    public String formatMoney(BigDecimal amount) {
        if (amount == null) amount = BigDecimal.ZERO;

        String currencySymbol = plugin.getConfig().getString("economy.currency-symbol", "$");
        String format = plugin.getConfig().getString("economy.format", "{symbol}{amount}");

        String formattedAmount = String.format("%,.2f", amount);

        return format
            .replace("{symbol}", currencySymbol)
            .replace("{amount}", formattedAmount);
    }

    /**
     * Formatiert einen Geldbetrag mit Abkürzungen (K, M, B, T)
     * Für kompakte Anzeige (z.B. Scoreboard, Bedrock)
     */
    public String formatMoneyShort(BigDecimal amount) {
        if (amount == null) amount = BigDecimal.ZERO;

        String currencySymbol = plugin.getConfig().getString("economy.currency-symbol", "$");
        String format = plugin.getConfig().getString("economy.format", "{symbol}{amount}");

        String formattedAmount = formatWithSuffix(amount);

        return format
            .replace("{symbol}", currencySymbol)
            .replace("{amount}", formattedAmount);
    }

    /**
     * Formatiert eine Zahl mit Suffixen (K, M, B, T)
     * 1.000 = 1K, 1.000.000 = 1M, 1.000.000.000 = 1B, 1.000.000.000.000 = 1T
     */
    private String formatWithSuffix(BigDecimal amount) {
        if (amount == null) return "0";
        
        double value = amount.doubleValue();
        
        // Unter 1000: Zeige genau
        if (value < 1_000) {
            return String.format("%.2f", value);
        }
        
        // 1K - 999.99K
        if (value < 1_000_000) {
            double k = value / 1_000;
            if (k >= 100) {
                return String.format("%.0fK", k);
            } else if (k >= 10) {
                return String.format("%.1fK", k);
            }
            return String.format("%.2fK", k);
        }
        
        // 1M - 999.99M
        if (value < 1_000_000_000) {
            double m = value / 1_000_000;
            if (m >= 100) {
                return String.format("%.0fM", m);
            } else if (m >= 10) {
                return String.format("%.1fM", m);
            }
            return String.format("%.2fM", m);
        }
        
        // 1B - 999.99B
        if (value < 1_000_000_000_000L) {
            double b = value / 1_000_000_000;
            if (b >= 100) {
                return String.format("%.0fB", b);
            } else if (b >= 10) {
                return String.format("%.1fB", b);
            }
            return String.format("%.2fB", b);
        }
        
        // 1T+
        double t = value / 1_000_000_000_000L;
        if (t >= 100) {
            return String.format("%.0fT", t);
        } else if (t >= 10) {
            return String.format("%.1fT", t);
        }
        return String.format("%.2fT", t);
    }

    /**
     * Parst einen String zu einem Geldbetrag
     * @return null wenn ungültig
     */
    public BigDecimal parseAmount(String input) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }
        
        try {
            // Entferne Währungssymbol falls vorhanden
            String currencySymbol = plugin.getConfig().getString("economy.currency-symbol", "$");
            input = input.replace(currencySymbol, "").trim();
            
            // Entferne Tausendertrennzeichen
            input = input.replace(",", "");
            
            BigDecimal amount = new BigDecimal(input);
            
            // Validieren
            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                return null;
            }
            
            return roundAmount(amount);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Gibt den Namen der Währung zurück
     */
    public String getCurrencyName(boolean plural) {
        if (plural) {
            return plugin.getConfig().getString("economy.currency-name-plural", "Coins");
        }
        return plugin.getConfig().getString("economy.currency-name", "Coin");
    }

    /**
     * Gibt das maximale Guthaben zurück
     */
    public BigDecimal getMaxBalance() {
        return MAX_BALANCE;
    }

    /**
     * Gibt das minimale Guthaben zurück
     */
    public BigDecimal getMinBalance() {
        return MIN_BALANCE;
    }

    /**
     * Bereinigt inaktive Locks (sollte periodisch aufgerufen werden)
     */
    public void cleanupLocks() {
        playerLocks.entrySet().removeIf(entry -> !entry.getValue().isLocked());
        lastTransaction.entrySet().removeIf(entry -> 
            System.currentTimeMillis() - entry.getValue() > 60000 // 1 Minute
        );
    }
}
