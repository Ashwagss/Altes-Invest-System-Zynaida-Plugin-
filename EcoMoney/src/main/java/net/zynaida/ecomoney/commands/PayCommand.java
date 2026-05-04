package net.zynaida.ecomoney.commands;

import net.zynaida.ecomoney.EcoMoney;
import net.zynaida.ecomoney.api.events.TransactionResult;
import net.zynaida.ecomoney.data.MySQLManager;
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

public class PayCommand implements CommandExecutor, TabCompleter {

    private final EcoMoney plugin;

    public PayCommand(EcoMoney plugin) {
        this.plugin = plugin;
        plugin.getCommand("pay").setTabCompleter(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        
        if (!(sender instanceof Player)) {
            sender.sendMessage(MessageUtil.color("&cDieser Befehl kann nur von Spielern ausgeführt werden!"));
            return true;
        }
        
        if (!sender.hasPermission("ecomoney.pay")) {
            sender.sendMessage(MessageUtil.color("&cDu hast keine Berechtigung für diesen Befehl!"));
            return true;
        }
        
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.color("&cVerwendung: /pay <Spieler> <Betrag>"));
            return true;
        }
        
        Player player = (Player) sender;
        String targetName = args[0];
        
        // Betrag parsen
        BigDecimal amount = plugin.getAPI().parseAmount(args[1]);
        if (amount == null) {
            sender.sendMessage(MessageUtil.color("&cUngültiger Betrag!"));
            return true;
        }
        
        // Sicherheitscheck: Betrag muss positiv sein
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            sender.sendMessage(MessageUtil.color("&cDer Betrag muss größer als 0 sein!"));
            return true;
        }
        
        // Maximaler Transfer-Betrag
        BigDecimal maxTransfer = BigDecimal.valueOf(plugin.getConfig().getDouble("economy.max-transfer", 1000000000.0));
        if (amount.compareTo(maxTransfer) > 0) {
            sender.sendMessage(MessageUtil.color("&cMaximaler Überweisungsbetrag: " + plugin.getAPI().formatMoney(maxTransfer)));
            return true;
        }
        
        // Zielspieler suchen
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        
        // Prüfen ob sich selbst
        if (target.getUniqueId().equals(player.getUniqueId())) {
            sender.sendMessage(MessageUtil.color("&cDu kannst dir selbst kein Geld senden!"));
            return true;
        }
        
        // Prüfen ob Ziel ein Konto hat
        if (!plugin.getAPI().hasAccount(target.getUniqueId())) {
            sender.sendMessage(MessageUtil.color("&cDieser Spieler hat kein Konto!"));
            return true;
        }
        
        // Transaktion ausführen
        TransactionResult result = plugin.getAPI().transfer(
            player.getUniqueId(), 
            target.getUniqueId(), 
            amount, 
            "Pay-Befehl"
        );
        
        if (result.isSuccess()) {
            String targetDisplayName = target.getName() != null ? target.getName() : 
                plugin.getDataManager().getPlayerName(target.getUniqueId());
            
            sender.sendMessage(MessageUtil.color("&aDu hast &e" + plugin.getAPI().formatMoney(amount) + 
                " &aan &e" + targetDisplayName + " &agesendet!"));
            
            // Empfänger benachrichtigen
            String receiveMsg = "&aDu hast &e" + plugin.getAPI().formatMoney(amount) + " &avon &e" + player.getName() + " &aerhalten!";
            if (target.isOnline()) {
                ((Player) target).sendMessage(MessageUtil.color(receiveMsg));
            } else if (plugin.getDataManager() instanceof MySQLManager) {
                // Cross-Server: Nachricht in DB speichern
                ((MySQLManager) plugin.getDataManager()).queueNotification(target.getUniqueId(), receiveMsg);
            }
            
            // Neues Guthaben anzeigen
            BigDecimal newBalance = plugin.getAPI().getBalance(player.getUniqueId());
            sender.sendMessage(MessageUtil.color("&7Neuer Kontostand: &a" + plugin.getAPI().formatMoney(newBalance)));
        } else {
            sender.sendMessage(MessageUtil.color("&c" + result.getMessage()));
        }
        
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> !name.equalsIgnoreCase(sender.getName())) // Sich selbst ausschließen
                .filter(name -> name.toLowerCase().startsWith(input))
                .collect(Collectors.toList());
        } else if (args.length == 2) {
            // Betrags-Vorschläge
            List<String> suggestions = new ArrayList<>();
            suggestions.add("100");
            suggestions.add("500");
            suggestions.add("1000");
            suggestions.add("5000");
            return suggestions.stream()
                .filter(s -> s.startsWith(args[1]))
                .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
