package net.zynaida.ecomoney.data;

import net.zynaida.ecomoney.EcoMoney;
import net.zynaida.ecomoney.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.UUID;

/**
 * Periodischer Task für Cross-Server Benachrichtigungen.
 * Pollt die MySQL-Datenbank nach ausstehenden Nachrichten
 * und liefert sie an online Spieler aus.
 */
public class NotificationSyncTask extends BukkitRunnable {

    private final EcoMoney plugin;
    private final MySQLManager mysqlManager;

    public NotificationSyncTask(EcoMoney plugin, MySQLManager mysqlManager) {
        this.plugin = plugin;
        this.mysqlManager = mysqlManager;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            List<String> messages = mysqlManager.pollNotifications(uuid);
            if (!messages.isEmpty()) {
                // Nachrichten auf dem Main-Thread senden
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        for (String msg : messages) {
                            player.sendMessage(MessageUtil.color(msg));
                        }
                    }
                });
            }
        }
    }
}
