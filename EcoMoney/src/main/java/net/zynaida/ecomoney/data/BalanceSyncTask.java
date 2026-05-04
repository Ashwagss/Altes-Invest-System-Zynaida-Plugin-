package net.zynaida.ecomoney.data;

import net.zynaida.ecomoney.EcoMoney;
import org.bukkit.scheduler.BukkitRunnable;

import java.math.BigDecimal;
import java.sql.*;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Periodischer Task für Cross-Server Balance-Synchronisierung.
 * Pollt die MySQL-Datenbank nach kürzlich geänderten Konten
 * und aktualisiert den lokalen Cache.
 */
public class BalanceSyncTask extends BukkitRunnable {

    private final EcoMoney plugin;
    private final MySQLManager mysqlManager;
    private Timestamp lastSyncTime;
    private boolean initialized = false;

    public BalanceSyncTask(EcoMoney plugin, MySQLManager mysqlManager) {
        this.plugin = plugin;
        this.mysqlManager = mysqlManager;
    }

    @Override
    public void run() {
        try (Connection conn = mysqlManager.getConnection()) {
            // Beim ersten Aufruf: aktuelle DB-Zeit holen (vermeidet Timezone-Probleme)
            if (!initialized) {
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT NOW()")) {
                    if (rs.next()) {
                        lastSyncTime = rs.getTimestamp(1);
                    }
                }
                initialized = true;
                return;
            }

            // 1 Sekunde Buffer gegen Clock-Skew
            Timestamp queryTime = new Timestamp(lastSyncTime.getTime() - 1000);

            // Aktuelle DB-Zeit für nächsten Durchlauf merken
            Timestamp newSyncTime;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT NOW()")) {
                if (rs.next()) {
                    newSyncTime = rs.getTimestamp(1);
                } else {
                    return;
                }
            }

            String sql = "SELECT uuid, name, balance FROM ecomoney_accounts WHERE updated_at > ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setTimestamp(1, queryTime);
                try (ResultSet rs = stmt.executeQuery()) {
                    int count = 0;
                    while (rs.next()) {
                        UUID uuid = UUID.fromString(rs.getString("uuid"));
                        BigDecimal balance = rs.getBigDecimal("balance");
                        String name = rs.getString("name");
                        mysqlManager.updateCache(uuid, balance, name);
                        count++;
                    }
                    if (count > 0) {
                        plugin.getLogger().info("Sync: " + count + " Konto(en) aus DB aktualisiert");
                    }
                }
            }
            lastSyncTime = newSyncTime;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Balance-Sync fehlgeschlagen", e);
        }
    }
}
