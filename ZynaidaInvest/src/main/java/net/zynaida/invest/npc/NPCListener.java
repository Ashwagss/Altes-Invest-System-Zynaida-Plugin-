package net.zynaida.invest.npc;

import net.zynaida.invest.ZynaidaInvest;
import net.zynaida.invest.gui.InvestGUI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public class NPCListener implements Listener {

    private final ZynaidaInvest plugin;

    public NPCListener(ZynaidaInvest plugin) {
        this.plugin = plugin;
    }

    /**
     * Rechtsklick auf NPC -> Börse öffnen (verhindert auch Villager-Trade GUI)
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!plugin.getNpcManager().isInvestNPC(event.getRightClicked())) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        if (!player.hasPermission("zynaidainvest.use")) {
            player.sendMessage(plugin.getConfig().getString("messages.prefix", "§8[§6§lInvest§8] ") +
                    "§cKeine Berechtigung!");
            return;
        }

        openInvestMenu(player);
    }

    /**
     * Verhindere Schaden am NPC
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageEvent event) {
        if (plugin.getNpcManager().isInvestNPC(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    /**
     * Linksklick auf NPC öffnet auch das Menü
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!plugin.getNpcManager().isInvestNPC(event.getEntity())) return;
        event.setCancelled(true);

        if (event.getDamager() instanceof Player player && player.hasPermission("zynaidainvest.use")) {
            openInvestMenu(player);
        }
    }

    @EventHandler
    public void onEntityTarget(EntityTargetEvent event) {
        if (plugin.getNpcManager().isInvestNPC(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    private void openInvestMenu(Player player) {
        if (plugin.getBedrockManager().isBedrockPlayer(player)) {
            plugin.getBedrockManager().openMainMenu(player);
        } else {
            new InvestGUI(plugin).openMainMenu(player);
        }
    }
}
