package net.zynaida.invest.commands;

import net.zynaida.invest.ZynaidaInvest;
import net.zynaida.invest.gui.InvestGUI;
import net.zynaida.invest.invest.InvestmentManager.TradeResult;
import net.zynaida.invest.models.Asset;
import net.zynaida.invest.models.Investment;
import net.zynaida.invest.models.Portfolio;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

public class InvestCommand implements CommandExecutor, TabCompleter {

    private final ZynaidaInvest plugin;
    private final String prefix;

    public InvestCommand(ZynaidaInvest plugin) {
        this.plugin = plugin;
        this.prefix = plugin.getConfig().getString("messages.prefix", "§8[§6§lInvest§8] ");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cNur für Spieler!");
            return true;
        }

        if (!player.hasPermission("zynaidainvest.use")) {
            player.sendMessage(prefix + "§cKeine Berechtigung!");
            return true;
        }

        if (args.length == 0) {
            // GUI öffnen
            if (plugin.getBedrockManager().isBedrockPlayer(player)) {
                plugin.getBedrockManager().openMainMenu(player);
            } else {
                new InvestGUI(plugin).openMainMenu(player);
            }
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "portfolio", "p" -> {
                if (plugin.getBedrockManager().isBedrockPlayer(player)) {
                    plugin.getBedrockManager().openPortfolioForm(player);
                } else {
                    new InvestGUI(plugin).openPortfolio(player);
                }
            }
            case "buy", "kaufen" -> {
                if (args.length < 3) {
                    player.sendMessage(prefix + "§c/invest buy <ticker> <betrag>");
                    return true;
                }
                handleBuy(player, args[1], args[2]);
            }
            case "sell", "verkaufen" -> {
                if (args.length < 3) {
                    player.sendMessage(prefix + "§c/invest sell <ticker> <prozent>");
                    return true;
                }
                handleSell(player, args[1], args[2]);
            }
            case "info" -> {
                if (args.length < 2) {
                    player.sendMessage(prefix + "§c/invest info <ticker>");
                    return true;
                }
                handleInfo(player, args[1]);
            }
            case "top", "leaderboard" -> {
                if (plugin.getBedrockManager().isBedrockPlayer(player)) {
                    plugin.getBedrockManager().openLeaderboardForm(player);
                } else {
                    new InvestGUI(plugin).openLeaderboard(player);
                }
            }
            case "help", "hilfe" -> showHelp(player);
            default -> player.sendMessage(prefix + "§cUnbekannter Befehl. /invest help");
        }

        return true;
    }

    private void handleBuy(Player player, String ticker, String amountStr) {
        Asset asset = findByTicker(ticker);
        if (asset == null) {
            player.sendMessage(prefix + "§cAsset nicht gefunden: " + ticker);
            return;
        }
        try {
            double amount = Double.parseDouble(amountStr);
            TradeResult result = plugin.getInvestmentManager().buy(player.getUniqueId(), asset.getId(), amount);
            player.sendMessage(prefix + result.getMessage());
            if (result == TradeResult.SUCCESS) {
                player.sendMessage(prefix + "§a" + String.format("%.4f", amount / asset.getCurrentPrice()) +
                    " Anteile von §f" + asset.getName() + " §agekauft!");
            }
        } catch (NumberFormatException e) {
            player.sendMessage(prefix + "§cUngültiger Betrag!");
        }
    }

    private void handleSell(Player player, String ticker, String percentStr) {
        Asset asset = findByTicker(ticker);
        if (asset == null) {
            player.sendMessage(prefix + "§cAsset nicht gefunden: " + ticker);
            return;
        }
        try {
            double percent = Double.parseDouble(percentStr.replace("%", "")) / 100.0;
            percent = Math.max(0.01, Math.min(1.0, percent));

            Portfolio portfolio = plugin.getInvestmentManager().getOrCreatePortfolio(player.getUniqueId());
            Investment inv = portfolio.getInvestment(asset.getId());
            double valueBefore = inv != null ? inv.getCurrentValue(asset.getCurrentPrice()) : 0;

            TradeResult result = plugin.getInvestmentManager().sell(player.getUniqueId(), asset.getId(), percent);
            player.sendMessage(prefix + result.getMessage());
            if (result == TradeResult.SUCCESS) {
                double sold = valueBefore * percent;
                double fee = sold * (plugin.getInvestmentManager().getTransactionFeePercent() / 100.0);
                player.sendMessage(prefix + "§cVerkauft für §f" + plugin.getEcoMoneyHook().formatMoney(sold - fee));
            }
        } catch (NumberFormatException e) {
            player.sendMessage(prefix + "§cUngültiger Prozentsatz!");
        }
    }

    private void handleInfo(Player player, String ticker) {
        Asset asset = findByTicker(ticker);
        if (asset == null) {
            player.sendMessage(prefix + "§cAsset nicht gefunden: " + ticker);
            return;
        }

        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage(asset.getType().getColor() + "§l " + asset.getName() + " §7(" + asset.getTicker() + ")");
        player.sendMessage("§7Preis: §f" + asset.getFormattedPrice() + " " + asset.getChangeArrow());
        player.sendMessage("§7Änderung: " + asset.getFormattedChange());
        player.sendMessage("§7Volatilität: " + asset.getVolatility().getDisplay());
        player.sendMessage("§7ATH: §a" + String.format("%.2f$", asset.getAllTimeHigh()));
        player.sendMessage("§7ATL: §c" + String.format("%.2f$", asset.getAllTimeLow()));
        if (asset.getDividendYield() > 0) {
            player.sendMessage("§7Dividende: §a" + String.format("%.1f%%", asset.getDividendYield()));
        }
        if (asset.hasActiveNews()) {
            player.sendMessage(asset.getCurrentNews());
        }
        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }

    private void showHelp(Player player) {
        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("§6§l  ZynaidaInvest §7- §eHilfe");
        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("§e/invest §7- Börsen-GUI öffnen");
        player.sendMessage("§e/invest portfolio §7- Portfolio anzeigen");
        player.sendMessage("§e/invest buy <ticker> <betrag> §7- Kaufen");
        player.sendMessage("§e/invest sell <ticker> <prozent> §7- Verkaufen");
        player.sendMessage("§e/invest info <ticker> §7- Asset-Info");
        player.sendMessage("§e/invest top §7- Top Investoren");
        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }

    private Asset findByTicker(String ticker) {
        for (Asset a : plugin.getInvestmentManager().getAssets().values()) {
            if (a.getTicker().equalsIgnoreCase(ticker)) return a;
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("portfolio", "buy", "sell", "info", "top", "help"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("buy") || args[0].equalsIgnoreCase("sell") || args[0].equalsIgnoreCase("info"))) {
            List<String> tickers = plugin.getInvestmentManager().getAssets().values().stream()
                .map(Asset::getTicker).toList();
            return filter(tickers, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("buy")) {
            return List.of("100", "500", "1000", "5000", "10000");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("sell")) {
            return List.of("25%", "50%", "75%", "100%");
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String input) {
        return options.stream().filter(s -> s.toLowerCase().startsWith(input.toLowerCase())).toList();
    }
}
