package net.zynaida.invest.commands;

import net.zynaida.invest.ZynaidaInvest;
import net.zynaida.invest.models.Asset;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public class InvestAdminCommand implements CommandExecutor, TabCompleter {

    private final ZynaidaInvest plugin;
    private final String prefix;

    public InvestAdminCommand(ZynaidaInvest plugin) {
        this.plugin = plugin;
        this.prefix = "§8[§6§lInvest-Admin§8] ";
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("zynaidainvest.admin")) {
            sender.sendMessage(prefix + "§cKeine Berechtigung!");
            return true;
        }

        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reloadConfig();
                sender.sendMessage(prefix + "§aKonfiguration neu geladen!");
            }
            case "save" -> {
                plugin.getInvestmentManager().saveAll();
                sender.sendMessage(prefix + "§aAlle Daten gespeichert!");
            }
            case "setprice" -> {
                if (args.length < 3) {
                    sender.sendMessage(prefix + "§c/investadmin setprice <ticker> <preis>");
                    return true;
                }
                handleSetPrice(sender, args[1], args[2]);
            }
            case "crash" -> {
                sender.sendMessage(prefix + "§4Markt-Crash ausgelöst!");
                for (Asset asset : plugin.getInvestmentManager().getAssets().values()) {
                    asset.setCrashing(true);
                    asset.setTrend(-0.8, 15);
                }
                Bukkit.broadcastMessage(prefix + "§4§l⚠ MARKT-CRASH! ⚠ §cAlle Kurse fallen drastisch!");
            }
            case "boom" -> {
                sender.sendMessage(prefix + "§a§lMarkt-Boom ausgelöst!");
                for (Asset asset : plugin.getInvestmentManager().getAssets().values()) {
                    asset.setBoosting(true);
                    asset.setTrend(0.8, 15);
                }
                Bukkit.broadcastMessage(prefix + "§a§l🚀 MARKT-BOOM! 🚀 §aAlle Kurse steigen!");
            }
            case "reset" -> {
                if (args.length < 2) {
                    sender.sendMessage(prefix + "§c/investadmin reset <spieler>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(prefix + "§cSpieler nicht gefunden!");
                    return true;
                }
                plugin.getDataManager().deletePortfolio(target.getUniqueId());
                sender.sendMessage(prefix + "§aPortfolio von §f" + target.getName() + " §agelöscht!");
            }
            case "info" -> {
                sender.sendMessage(prefix + "§eAssets: §f" + plugin.getInvestmentManager().getAssets().size());
                sender.sendMessage(prefix + "§eSentiment: §f" + plugin.getMarketSimulator().getSentimentDisplay());
                for (Asset a : plugin.getInvestmentManager().getAssets().values()) {
                    sender.sendMessage("  " + a.getChangeArrow() + " §f" + a.getTicker() + " §7" +
                        a.getFormattedPrice() + " " + a.getFormattedChange() +
                        (a.isCrashing() ? " §4[CRASH]" : "") + (a.isBoosting() ? " §a[BOOM]" : ""));
                }
            }
            case "setnpc" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(prefix + "§cNur für Spieler!");
                    return true;
                }
                plugin.getNpcManager().setNPCLocation(p.getLocation());
                sender.sendMessage(prefix + "§aBörsen-Makler NPC gesetzt!");
            }
            case "removenpc" -> {
                plugin.getNpcManager().removeNPC();
                sender.sendMessage(prefix + "§cNPC entfernt!");
            }
            default -> showHelp(sender);
        }
        return true;
    }

    private void handleSetPrice(CommandSender sender, String ticker, String priceStr) {
        Asset asset = null;
        for (Asset a : plugin.getInvestmentManager().getAssets().values()) {
            if (a.getTicker().equalsIgnoreCase(ticker)) { asset = a; break; }
        }
        if (asset == null) {
            sender.sendMessage(prefix + "§cAsset nicht gefunden!");
            return;
        }
        try {
            double price = Double.parseDouble(priceStr);
            if (price <= 0) { sender.sendMessage(prefix + "§cPreis muss > 0 sein!"); return; }
            asset.updatePrice(price);
            sender.sendMessage(prefix + "§a" + asset.getTicker() + " Preis auf §f" + String.format("%.2f$", price) + " §agesetzt.");
        } catch (NumberFormatException e) {
            sender.sendMessage(prefix + "§cUngültiger Preis!");
        }
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("§6§l  InvestAdmin §7- §eHilfe");
        sender.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("§e/investadmin reload §7- Config neu laden");
        sender.sendMessage("§e/investadmin save §7- Alles speichern");
        sender.sendMessage("§e/investadmin setprice <ticker> <preis> §7- Preis setzen");
        sender.sendMessage("§e/investadmin crash §7- Markt-Crash auslösen");
        sender.sendMessage("§e/investadmin boom §7- Markt-Boom auslösen");
        sender.sendMessage("§e/investadmin reset <spieler> §7- Portfolio löschen");
        sender.sendMessage("§e/investadmin info §7- Markt-Info");
        sender.sendMessage("§e/investadmin setnpc §7- NPC an deiner Position setzen");
        sender.sendMessage("§e/investadmin removenpc §7- NPC entfernen");
        sender.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("zynaidainvest.admin")) return List.of();
        if (args.length == 1) {
            return List.of("reload", "save", "setprice", "crash", "boom", "reset", "info", "setnpc", "removenpc").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setprice")) {
            return plugin.getInvestmentManager().getAssets().values().stream()
                .map(Asset::getTicker).filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).toList();
        }
        return List.of();
    }
}
