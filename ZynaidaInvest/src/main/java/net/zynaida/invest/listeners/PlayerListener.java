package net.zynaida.invest.listeners;

import net.zynaida.invest.ZynaidaInvest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {

    private final ZynaidaInvest plugin;

    public PlayerListener(ZynaidaInvest plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Portfolio laden/erstellen
        plugin.getInvestmentManager().getOrCreatePortfolio(player.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getInvestmentManager().savePortfolio(event.getPlayer().getUniqueId());
    }
}
