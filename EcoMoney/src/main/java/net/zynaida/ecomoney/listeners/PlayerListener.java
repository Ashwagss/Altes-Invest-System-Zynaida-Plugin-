package net.zynaida.ecomoney.listeners;

import net.zynaida.ecomoney.EcoMoney;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerListener implements Listener {

    private final EcoMoney plugin;

    public PlayerListener(EcoMoney plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Konto erstellen falls nicht vorhanden
        if (!plugin.getAPI().hasAccount(player.getUniqueId())) {
            plugin.getAPI().createAccount(player.getUniqueId());
            plugin.getLogger().info("Neues Konto erstellt für: " + player.getName());
        }
        
        // Spielername aktualisieren
        plugin.getDataManager().updatePlayerName(player.getUniqueId(), player.getName());
    }
}
