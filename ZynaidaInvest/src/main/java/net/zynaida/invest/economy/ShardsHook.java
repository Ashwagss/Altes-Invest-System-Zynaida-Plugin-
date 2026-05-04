package net.zynaida.invest.economy;

import net.zynaida.invest.ZynaidaInvest;

import java.sql.*;
import java.util.UUID;

/**
 * Shards-Integration - Nutzt die GLEICHE MySQL-Tabelle wie das LobbyPlugin!
 * Tabelle: lobby_players
 * Spalten: uuid, name, shards, total_collected, daily_streak, last_daily, first_join, last_seen
 */
public class ShardsHook {

    private final ZynaidaInvest plugin;
    private Connection connection;
    private boolean enabled = false;

    public ShardsHook(ZynaidaInvest plugin) {
        this.plugin = plugin;
    }

    public boolean setup() {
        if (!plugin.getConfig().getBoolean("shards.enabled", true)) {
            plugin.getLogger().info("Shards-System deaktiviert in Config.");
            return false;
        }

        try {
            String host = plugin.getConfig().getString("shards.mysql.host", "localhost");
            int port = plugin.getConfig().getInt("shards.mysql.port", 3306);
            String database = plugin.getConfig().getString("shards.mysql.database", "lobby");
            String username = plugin.getConfig().getString("shards.mysql.username", "root");
            String password = plugin.getConfig().getString("shards.mysql.password", "");
            boolean ssl = plugin.getConfig().getBoolean("shards.mysql.useSSL", false);

            String url = "jdbc:mysql://" + host + ":" + port + "/" + database +
                    "?useSSL=" + ssl + "&autoReconnect=true&useUnicode=true&characterEncoding=UTF-8";
            connection = DriverManager.getConnection(url, username, password);

            // Prüfe ob Tabelle existiert
            try (Statement stmt = connection.createStatement()) {
                ResultSet rs = stmt.executeQuery("SELECT 1 FROM lobby_players LIMIT 1");
                rs.close();
            }

            enabled = true;
            plugin.getLogger().info("  §a✓ Shards (lobby_players) verbunden!");
            return true;

        } catch (SQLException e) {
            plugin.getLogger().severe("Shards MySQL Verbindung fehlgeschlagen: " + e.getMessage());
            plugin.getLogger().severe("Stelle sicher dass die lobby_players Tabelle existiert!");
            return false;
        }
    }

    private Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            setup();
        }
        return connection;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Shards abfragen
     */
    public long getShards(UUID uuid) {
        if (!enabled) return 0;
        try {
            String sql = "SELECT shards FROM lobby_players WHERE uuid = ?";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return rs.getLong("shards");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Shards getShards Fehler: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Prüfen ob genug Shards vorhanden
     */
    public boolean hasShards(UUID uuid, long amount) {
        return getShards(uuid) >= amount;
    }

    /**
     * Shards abziehen - Atomares Update mit WHERE-Bedingung gegen Race-Conditions
     */
    public boolean withdrawShards(UUID uuid, long amount) {
        if (!enabled || amount <= 0) return false;
        try {
            // Atomares Update: Nur erfolgreich wenn genug Shards vorhanden
            String sql = "UPDATE lobby_players SET shards = shards - ? WHERE uuid = ? AND shards >= ?";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setLong(1, amount);
                ps.setString(2, uuid.toString());
                ps.setLong(3, amount);
                int rows = ps.executeUpdate();
                return rows > 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Shards withdraw Fehler: " + e.getMessage());
        }
        return false;
    }

    /**
     * Shards gutschreiben (+ total_collected)
     */
    public boolean depositShards(UUID uuid, long amount) {
        if (!enabled || amount <= 0) return false;
        try {
            String sql = "UPDATE lobby_players SET shards = shards + ?, total_collected = total_collected + ? WHERE uuid = ?";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setLong(1, amount);
                ps.setLong(2, amount);
                ps.setString(3, uuid.toString());
                int rows = ps.executeUpdate();
                return rows > 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Shards deposit Fehler: " + e.getMessage());
        }
        return false;
    }

    /**
     * Shards formatieren
     */
    public String formatShards(long amount) {
        if (amount >= 1_000_000_000) return String.format("%.1fB", amount / 1_000_000_000.0);
        if (amount >= 1_000_000) return String.format("%.1fM", amount / 1_000_000.0);
        if (amount >= 1_000) return String.format("%.1fK", amount / 1_000.0);
        return String.valueOf(amount);
    }

    /**
     * Verbindung schließen
     */
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException ignored) {}
    }
}
