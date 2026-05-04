package net.zynaida.ecomoney.commands;

import net.zynaida.ecomoney.EcoMoney;
import net.zynaida.ecomoney.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class BalanceCommand implements CommandExecutor, TabCompleter {

    private final EcoMoney plugin;

    public BalanceCommand(EcoMoney plugin) {
        this.plugin = plugin;
        plugin.getCommand("balance").setTabCompleter(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        
        if (args.length == 0) {
            // Eigenes Guthaben anzeigen
            if (!(sender instanceof Player)) {
                sender.sendMessage(MessageUtil.color("&cDieser Befehl kann nur von Spielern ausgeführt werden!"));
                return true;
            }
            
            Player player = (Player) sender;
            BigDecimal balance = plugin.getAPI().getBalanceFresh(player.getUniqueId());
            
            sender.sendMessage(MessageUtil.color("&7Dein Kontostand: &a" + plugin.getAPI().formatMoney(balance)));
            return true;
        }
        
        // Guthaben eines anderen Spielers anzeigen
        if (!sender.hasPermission("ecomoney.balance.others")) {
            sender.sendMessage(MessageUtil.color("&cDu hast keine Berechtigung, das Guthaben anderer Spieler zu sehen!"));
            return true;
        }
        
        String targetName = args[0];
        
        // Spieler suchen (auch offline)
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        
        if (!plugin.getAPI().hasAccount(target.getUniqueId())) {
            sender.sendMessage(MessageUtil.color("&cDieser Spieler hat kein Konto!"));
            return true;
        }
        
        BigDecimal balance = plugin.getAPI().getBalanceFresh(target.getUniqueId());
        String displayName = target.getName() != null ? target.getName() : plugin.getDataManager().getPlayerName(target.getUniqueId());
        
        sender.sendMessage(MessageUtil.color("&7Kontostand von &e" + displayName + "&7: &a" + plugin.getAPI().formatMoney(balance)));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("ecomoney.balance.others")) {
            String input = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(input))
                .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
