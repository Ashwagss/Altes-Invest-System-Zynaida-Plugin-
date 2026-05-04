package net.zynaida.ecomoney.commands;

import net.zynaida.ecomoney.EcoMoney;
import net.zynaida.ecomoney.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class BalanceTopCommand implements CommandExecutor {

    private final EcoMoney plugin;
    
    // Cache für Balance Top (um Spam zu verhindern)
    private LinkedHashMap<UUID, BigDecimal> cachedTop;
    private long lastCacheTime = 0;
    private static final long CACHE_DURATION_MS = 30000; // 30 Sekunden Cache

    public BalanceTopCommand(EcoMoney plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        
        if (!sender.hasPermission("ecomoney.baltop")) {
            sender.sendMessage(MessageUtil.color("&cDu hast keine Berechtigung für diesen Befehl!"));
            return true;
        }
        
        int page = 1;
        if (args.length > 0) {
            try {
                page = Integer.parseInt(args[0]);
                if (page < 1) page = 1;
            } catch (NumberFormatException e) {
                sender.sendMessage(MessageUtil.color("&cUngültige Seitenzahl!"));
                return true;
            }
        }
        
        int entriesPerPage = plugin.getConfig().getInt("economy.baltop-entries-per-page", 10);
        int maxEntries = plugin.getConfig().getInt("economy.baltop-max-entries", 100);
        
        // Cache aktualisieren wenn nötig
        long now = System.currentTimeMillis();
        if (cachedTop == null || (now - lastCacheTime) > CACHE_DURATION_MS) {
            cachedTop = plugin.getDataManager().getTopBalances(maxEntries);
            lastCacheTime = now;
        }
        
        if (cachedTop.isEmpty()) {
            sender.sendMessage(MessageUtil.color("&cKeine Spieler mit Konten gefunden!"));
            return true;
        }
        
        int totalPages = (int) Math.ceil((double) cachedTop.size() / entriesPerPage);
        if (page > totalPages) page = totalPages;
        
        int startIndex = (page - 1) * entriesPerPage;
        int endIndex = Math.min(startIndex + entriesPerPage, cachedTop.size());
        
        sender.sendMessage(MessageUtil.color("&6&l=== Reichsten Spieler &7(Seite " + page + "/" + totalPages + ") &6&l==="));
        
        int rank = startIndex + 1;
        int index = 0;
        for (Map.Entry<UUID, BigDecimal> entry : cachedTop.entrySet()) {
            if (index >= startIndex && index < endIndex) {
                String playerName = plugin.getDataManager().getPlayerName(entry.getKey());
                
                // Fallback auf Bukkit wenn kein Name gespeichert
                if (playerName.equals("Unbekannt")) {
                    var offlinePlayer = Bukkit.getOfflinePlayer(entry.getKey());
                    if (offlinePlayer.getName() != null) {
                        playerName = offlinePlayer.getName();
                    }
                }
                
                String rankColor = getRankColor(rank);
                sender.sendMessage(MessageUtil.color(rankColor + "#" + rank + " &e" + playerName + " &7- &a" + 
                    plugin.getAPI().formatMoney(entry.getValue())));
                rank++;
            }
            index++;
            if (index >= endIndex) break;
        }
        
        if (totalPages > 1) {
            sender.sendMessage(MessageUtil.color("&7Verwende &e/baltop <Seite> &7für weitere Einträge"));
        }
        
        return true;
    }

    private String getRankColor(int rank) {
        return switch (rank) {
            case 1 -> "&6&l"; // Gold
            case 2 -> "&f&l"; // Silber
            case 3 -> "&c&l"; // Bronze
            default -> "&7";
        };
    }
}
