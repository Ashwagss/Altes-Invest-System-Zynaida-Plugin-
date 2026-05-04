package net.zynaida.ecomoney.data;

import net.zynaida.ecomoney.EcoMoney;

import java.math.BigDecimal;
import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * MySQL-basierte Datenspeicherung mit einfachem JDBC (ohne HikariCP)
 */
public class MySQLManager implements DataManager {

    private final EcoMoney plugin;
    private final String jdbcUrl;
    private final String username;
    private final String password;
    
    // Cache für Performance
    private final Map<UUID, BigDecimal> balanceCache = new ConcurrentHashMap<>();
    private final Map<UUID, String> nameCache = new ConcurrentHashMap<>();

    public MySQLManager(EcoMoney plugin, String host, int port, String database, 
                        String username, String password, boolean useSSL) {
        this.plugin = plugin;
        this.jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database + 
                       "?useSSL=" + useSSL + 
                       "&autoReconnect=true" +
                       "&useUnicode=true" +
                       "&characterEncoding=UTF-8";
        this.username = username;
        this.password = password;
    }

    /**
     * Erstellt eine neue Datenbankverbindung
     */
    Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    public boolean connect() {
        try {
            // Verbindung testen
            try (Connection conn = getConnection()) {
                if (conn.isValid(5)) {
                    plugin.getLogger().info("MySQL-Verbindung erfolgreich hergestellt!");
                    return true;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "MySQL-Verbindungsfehler!", e);
        }
        return false;
    }

    @Override
    public void initialize() {
        createTable();
        loadCache();
    }

    private void createTable() {
        String sql = """
            CREATE TABLE IF NOT EXISTS ecomoney_accounts (
                uuid VARCHAR(36) PRIMARY KEY,
                name VARCHAR(16),
                balance DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_balance (balance DESC)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """;

        String notifSql = """
            CREATE TABLE IF NOT EXISTS ecomoney_notifications (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                target_uuid VARCHAR(36) NOT NULL,
                message TEXT NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_target (target_uuid),
                INDEX idx_created (created_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """;

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            stmt.execute(notifSql);
            // Index für Cross-Server Sync Polling
            try {
                stmt.execute("CREATE INDEX idx_updated_at ON ecomoney_accounts (updated_at)");
            } catch (SQLException ignored) {
                // Index existiert bereits
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Konnte Tabelle nicht erstellen!", e);
        }
    }

    private void loadCache() {
        String sql = "SELECT uuid, name, balance FROM ecomoney_accounts";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            int count = 0;
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                String name = rs.getString("name");
                BigDecimal balance = rs.getBigDecimal("balance");
                
                balanceCache.put(uuid, balance);
                if (name != null) {
                    nameCache.put(uuid, name);
                }
                count++;
            }
            plugin.getLogger().info("Geladen: " + count + " Spielerkonten aus MySQL");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Laden der Daten!", e);
        }
    }

    @Override
    public void shutdown() {
        // Nichts zu tun - Verbindungen werden automatisch geschlossen
        balanceCache.clear();
        nameCache.clear();
    }

    @Override
    public void saveAll() {
        // Bei MySQL werden Daten sofort geschrieben, kein Batch-Save nötig
    }

    @Override
    public boolean hasAccount(UUID uuid) {
        if (balanceCache.containsKey(uuid)) return true;
        
        // Fallback auf DB
        String sql = "SELECT 1 FROM ecomoney_accounts WHERE uuid = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler bei hasAccount!", e);
            return false;
        }
    }

    @Override
    public boolean createAccount(UUID uuid, BigDecimal startBalance) {
        if (hasAccount(uuid)) return true;
        
        String sql = "INSERT INTO ecomoney_accounts (uuid, balance) VALUES (?, ?) ON DUPLICATE KEY UPDATE uuid=uuid";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setBigDecimal(2, startBalance);
            stmt.executeUpdate();
            
            balanceCache.put(uuid, startBalance);
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler bei createAccount!", e);
            return false;
        }
    }

    @Override
    public BigDecimal getBalance(UUID uuid) {
        BigDecimal cached = balanceCache.get(uuid);
        if (cached != null) return cached;
        
        // Fallback auf DB
        String sql = "SELECT balance FROM ecomoney_accounts WHERE uuid = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    BigDecimal balance = rs.getBigDecimal("balance");
                    balanceCache.put(uuid, balance);
                    return balance;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler bei getBalance!", e);
        }
        return BigDecimal.ZERO;
    }

    @Override
    public boolean setBalance(UUID uuid, BigDecimal amount) {
        // Sicherheitscheck
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            plugin.getLogger().warning("Versuch, negatives Guthaben zu setzen für: " + uuid);
            return false;
        }
        
        String sql = "UPDATE ecomoney_accounts SET balance = ? WHERE uuid = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setBigDecimal(1, amount);
            stmt.setString(2, uuid.toString());
            int affected = stmt.executeUpdate();
            
            if (affected > 0) {
                balanceCache.put(uuid, amount);
                return true;
            } else {
                // Konto existiert noch nicht
                return createAccount(uuid, amount);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler bei setBalance!", e);
            return false;
        }
    }

    @Override
    public LinkedHashMap<UUID, BigDecimal> getTopBalances(int limit) {
        LinkedHashMap<UUID, BigDecimal> top = new LinkedHashMap<>();
        String sql = "SELECT uuid, balance FROM ecomoney_accounts ORDER BY balance DESC LIMIT ?";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    BigDecimal balance = rs.getBigDecimal("balance");
                    top.put(uuid, balance);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler bei getTopBalances!", e);
        }
        return top;
    }

    @Override
    public void updatePlayerName(UUID uuid, String name) {
        nameCache.put(uuid, name);
        
        String sql = "UPDATE ecomoney_accounts SET name = ? WHERE uuid = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler bei updatePlayerName!", e);
        }
    }

    @Override
    public String getPlayerName(UUID uuid) {
        String cached = nameCache.get(uuid);
        if (cached != null) return cached;
        
        String sql = "SELECT name FROM ecomoney_accounts WHERE uuid = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String name = rs.getString("name");
                    if (name != null) {
                        nameCache.put(uuid, name);
                        return name;
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler bei getPlayerName!", e);
        }
        return "Unbekannt";
    }

    @Override
    public BigDecimal getBalanceFromDB(UUID uuid) {
        String sql = "SELECT balance FROM ecomoney_accounts WHERE uuid = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    BigDecimal balance = rs.getBigDecimal("balance");
                    balanceCache.put(uuid, balance);
                    return balance;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler bei getBalanceFromDB!", e);
        }
        return BigDecimal.ZERO;
    }

    /**
     * Aktualisiert den lokalen Cache (wird vom BalanceSyncTask verwendet)
     */
    void updateCache(UUID uuid, BigDecimal balance, String name) {
        balanceCache.put(uuid, balance);
        if (name != null) {
            nameCache.put(uuid, name);
        }
    }

    /**
     * Speichert eine Benachrichtigung für einen Spieler (Cross-Server)
     */
    public void queueNotification(UUID targetUuid, String message) {
        String sql = "INSERT INTO ecomoney_notifications (target_uuid, message) VALUES (?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, targetUuid.toString());
            stmt.setString(2, message);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Fehler beim Speichern der Benachrichtigung!", e);
        }
    }

    /**
     * Holt und löscht alle Benachrichtigungen für einen Spieler
     */
    public java.util.List<String> pollNotifications(UUID targetUuid) {
        java.util.List<String> messages = new java.util.ArrayList<>();
        String selectSql = "SELECT id, message FROM ecomoney_notifications WHERE target_uuid = ? ORDER BY id ASC";
        String deleteSql = "DELETE FROM ecomoney_notifications WHERE target_uuid = ?";

        try (Connection conn = getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(selectSql)) {
                stmt.setString(1, targetUuid.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        messages.add(rs.getString("message"));
                    }
                }
            }
            if (!messages.isEmpty()) {
                try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
                    stmt.setString(1, targetUuid.toString());
                    stmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Fehler beim Abrufen der Benachrichtigungen!", e);
        }
        return messages;
    }
}
