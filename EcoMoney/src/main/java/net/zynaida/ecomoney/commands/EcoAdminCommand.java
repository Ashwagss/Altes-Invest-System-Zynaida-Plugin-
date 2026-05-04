package net.zynaida.ecomoney.commands;

import net.zynaida.ecomoney.EcoMoney;
import net.zynaida.ecomoney.api.events.TransactionResult;
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
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class EcoAdminCommand implements CommandExecutor, TabCompleter {

    private final EcoMoney plugin;

    public EcoAdminCommand(EcoMoney plugin) {
        this.plugin = plugin;
        plugin.getCommand("ecoadmin").setTabCompleter(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        
        if (!sender.hasPermission("ecomoney.admin")) {
            sender.sendMessage(MessageUtil.color("&cDu hast keine Berechtigung für diesen Befehl!"));
            return true;
        }
        
        if (args.length < 1) {
            sendHelp(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "set" -> handleSet(sender, args);
            case "give", "add" -> handleGive(sender, args);
            case "take", "remove" -> handleTake(sender, args);
            case "reset" -> handleReset(sender, args);
            case "reload" -> handleReload(sender);
            default -> sendHelp(sender);
        }
        
        return true;
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.color("&cVerwendung: /ecoadmin set <Spieler> <Betrag>"));
            return;
        }
        
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        BigDecimal amount = plugin.getAPI().parseAmount(args[2]);
        
        if (amount == null) {
            sender.sendMessage(MessageUtil.color("&cUngültiger Betrag!"));
            return;
        }
        
        // Konto erstellen falls nicht vorhanden
        if (!plugin.getAPI().hasAccount(target.getUniqueId())) {
            plugin.getAPI().createAccount(target.getUniqueId());
        }
        
        TransactionResult result = plugin.getAPI().setBalance(target.getUniqueId(), amount, "Admin-Set");
        
        if (result.isSuccess()) {
            String targetName = target.getName() != null ? target.getName() : args[1];
            sender.sendMessage(MessageUtil.color("&aGuthaben von &e" + targetName + " &aauf &e" + 
                plugin.getAPI().formatMoney(amount) + " &agesetzt!"));
            
            // Spieler benachrichtigen wenn online
            if (target.isOnline()) {
                ((Player) target).sendMessage(MessageUtil.color("&aDein Guthaben wurde auf &e" + 
                    plugin.getAPI().formatMoney(amount) + " &agesetzt!"));
            }
        } else {
            sender.sendMessage(MessageUtil.color("&c" + result.getMessage()));
        }
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.color("&cVerwendung: /ecoadmin give <Spieler> <Betrag>"));
            return;
        }
        
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        BigDecimal amount = plugin.getAPI().parseAmount(args[2]);
        
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            sender.sendMessage(MessageUtil.color("&cUngültiger Betrag!"));
            return;
        }
        
        // Konto erstellen falls nicht vorhanden
        if (!plugin.getAPI().hasAccount(target.getUniqueId())) {
            plugin.getAPI().createAccount(target.getUniqueId());
        }
        
        TransactionResult result = plugin.getAPI().deposit(target.getUniqueId(), amount, "Admin-Give");
        
        if (result.isSuccess()) {
            String targetName = target.getName() != null ? target.getName() : args[1];
            sender.sendMessage(MessageUtil.color("&e" + plugin.getAPI().formatMoney(amount) + 
                " &aan &e" + targetName + " &agegeben!"));
            
            // Spieler benachrichtigen wenn online
            if (target.isOnline()) {
                ((Player) target).sendMessage(MessageUtil.color("&aDu hast &e" + 
                    plugin.getAPI().formatMoney(amount) + " &aerhalten!"));
            }
        } else {
            sender.sendMessage(MessageUtil.color("&c" + result.getMessage()));
        }
    }

    private void handleTake(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.color("&cVerwendung: /ecoadmin take <Spieler> <Betrag>"));
            return;
        }
        
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        BigDecimal amount = plugin.getAPI().parseAmount(args[2]);
        
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            sender.sendMessage(MessageUtil.color("&cUngültiger Betrag!"));
            return;
        }
        
        if (!plugin.getAPI().hasAccount(target.getUniqueId())) {
            sender.sendMessage(MessageUtil.color("&cDieser Spieler hat kein Konto!"));
            return;
        }
        
        TransactionResult result = plugin.getAPI().withdraw(target.getUniqueId(), amount, "Admin-Take");
        
        if (result.isSuccess()) {
            String targetName = target.getName() != null ? target.getName() : args[1];
            sender.sendMessage(MessageUtil.color("&e" + plugin.getAPI().formatMoney(amount) + 
                " &avon &e" + targetName + " &aabgezogen!"));
            
            // Spieler benachrichtigen wenn online
            if (target.isOnline()) {
                ((Player) target).sendMessage(MessageUtil.color("&c" + 
                    plugin.getAPI().formatMoney(amount) + " &cwurden von deinem Konto abgezogen!"));
            }
        } else {
            sender.sendMessage(MessageUtil.color("&c" + result.getMessage()));
        }
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.color("&cVerwendung: /ecoadmin reset <Spieler>"));
            return;
        }
        
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        
        if (!plugin.getAPI().hasAccount(target.getUniqueId())) {
            sender.sendMessage(MessageUtil.color("&cDieser Spieler hat kein Konto!"));
            return;
        }
        
        BigDecimal startBalance = BigDecimal.valueOf(plugin.getConfig().getDouble("economy.starting-balance", 0.0));
        TransactionResult result = plugin.getAPI().setBalance(target.getUniqueId(), startBalance, "Admin-Reset");
        
        if (result.isSuccess()) {
            String targetName = target.getName() != null ? target.getName() : args[1];
            sender.sendMessage(MessageUtil.color("&aGuthaben von &e" + targetName + " &azurückgesetzt!"));
            
            // Spieler benachrichtigen wenn online
            if (target.isOnline()) {
                ((Player) target).sendMessage(MessageUtil.color("&cDein Guthaben wurde zurückgesetzt!"));
            }
        } else {
            sender.sendMessage(MessageUtil.color("&c" + result.getMessage()));
        }
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadConfig();
        sender.sendMessage(MessageUtil.color("&aKonfiguration neu geladen!"));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(MessageUtil.color("&6&l=== EcoMoney Admin ==="));
        sender.sendMessage(MessageUtil.color("&e/ecoadmin set <Spieler> <Betrag> &7- Guthaben setzen"));
        sender.sendMessage(MessageUtil.color("&e/ecoadmin give <Spieler> <Betrag> &7- Geld geben"));
        sender.sendMessage(MessageUtil.color("&e/ecoadmin take <Spieler> <Betrag> &7- Geld abziehen"));
        sender.sendMessage(MessageUtil.color("&e/ecoadmin reset <Spieler> &7- Guthaben zurücksetzen"));
        sender.sendMessage(MessageUtil.color("&e/ecoadmin reload &7- Konfiguration neu laden"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("ecomoney.admin")) {
            return new ArrayList<>();
        }
        
        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("set", "give", "take", "reset", "reload");
            return subCommands.stream()
                .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if (Arrays.asList("set", "give", "take", "reset").contains(subCommand)) {
                return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }
        } else if (args.length == 3) {
            String subCommand = args[0].toLowerCase();
            if (Arrays.asList("set", "give", "take").contains(subCommand)) {
                List<String> suggestions = Arrays.asList("100", "500", "1000", "5000", "10000");
                return suggestions.stream()
                    .filter(s -> s.startsWith(args[2]))
                    .collect(Collectors.toList());
            }
        }
        return new ArrayList<>();
    }
}
