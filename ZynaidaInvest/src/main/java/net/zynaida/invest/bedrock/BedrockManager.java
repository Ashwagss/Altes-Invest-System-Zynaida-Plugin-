package net.zynaida.invest.bedrock;

import net.zynaida.invest.ZynaidaInvest;
import net.zynaida.invest.invest.InvestmentManager;
import net.zynaida.invest.invest.InvestmentManager.TradeResult;
import net.zynaida.invest.models.Asset;
import net.zynaida.invest.models.Asset.AssetType;
import net.zynaida.invest.models.Investment;
import net.zynaida.invest.models.Portfolio;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.ModalForm;
import org.geysermc.cumulus.form.SimpleForm;

import java.util.*;

public class BedrockManager {

    private final ZynaidaInvest plugin;
    private boolean floodgateEnabled = false;
    private FloodgateApi floodgateApi;

    public BedrockManager(ZynaidaInvest plugin) {
        this.plugin = plugin;
        setup();
    }

    private void setup() {
        if (Bukkit.getPluginManager().getPlugin("floodgate") == null) {
            plugin.getLogger().info("Floodgate nicht gefunden - Bedrock Forms deaktiviert.");
            return;
        }
        try {
            floodgateApi = FloodgateApi.getInstance();
            floodgateEnabled = true;
            plugin.getLogger().info("§aFloodgate gefunden - Bedrock Forms aktiviert!");
        } catch (Exception e) {
            plugin.getLogger().warning("Floodgate Fehler: " + e.getMessage());
        }
    }

    public boolean isBedrockPlayer(Player player) {
        if (!floodgateEnabled || floodgateApi == null) return false;
        try {
            return floodgateApi.isFloodgatePlayer(player.getUniqueId());
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== HAUPTMENÜ ====================

    public void openMainMenu(Player player) {
        if (!isBedrockPlayer(player)) return;

        plugin.getInvestmentManager().reloadPortfolioFromDB(player.getUniqueId());
        Portfolio portfolio = plugin.getInvestmentManager().getOrCreatePortfolio(player.getUniqueId());

        // Nicht freigeschaltet -> Kauf-Form
        if (!portfolio.isUnlocked()) {
            openUnlockForm(player);
            return;
        }

        double balance = plugin.getEcoMoneyHook().getBalance(player.getUniqueId());
        double portfolioValue = portfolio.getTotalValue(plugin.getInvestmentManager().getAssets());

        SimpleForm form = SimpleForm.builder()
            .title("§6§l⬢ Zynaida Börse ⬢")
            .content("§7Willkommen an der Börse!\n\n" +
                "§7Geld: §a" + plugin.getEcoMoneyHook().formatMoney(balance) + "\n" +
                "§7Portfolio: §e" + plugin.getEcoMoneyHook().formatMoney(portfolioValue) + "\n" +
                "§7Tier: " + portfolio.getTierName() + "\n" +
                "§7Gesamt: §6" + plugin.getEcoMoneyHook().formatMoney(balance + portfolioValue) + "\n\n" +
                "§7Markt: " + plugin.getMarketSimulator().getSentimentDisplay() + "\n\n" +
                "§7Wähle eine Kategorie:")
            .button("§a📈 Aktien\n§7Dividenden & Wachstum")
            .button("§6₿ Kryptowährungen\n§7Hohes Risiko, hohe Belohnung")
            .button("§b🏦 Anleihen\n§7Stabile Rendite")
            .button("§e⛏ Rohstoffe\n§7Sachwert-Investments")
            .button("§d📊 ETFs\n§7Diversifizierte Fonds")
            .button("§a💼 Mein Portfolio\n§7Deine Investments")
            .button("§d🏆 Top Investoren\n§7Leaderboard")
            .button("§cSchließen")
            .validResultHandler(response -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    switch (response.clickedButtonId()) {
                        case 0 -> openCategoryMenu(player, AssetType.STOCK);
                        case 1 -> openCategoryMenu(player, AssetType.CRYPTO);
                        case 2 -> openCategoryMenu(player, AssetType.BOND);
                        case 3 -> openCategoryMenu(player, AssetType.COMMODITY);
                        case 4 -> openCategoryMenu(player, AssetType.ETF);
                        case 5 -> openPortfolioForm(player);
                        case 6 -> openLeaderboardForm(player);
                    }
                });
            })
            .build();

        floodgateApi.sendForm(player.getUniqueId(), form);
    }

    // ==================== PORTFOLIO KAUF ====================

    public void openUnlockForm(Player player) {
        double balance = plugin.getEcoMoneyHook().getBalance(player.getUniqueId());
        long shards = plugin.getShardsHook().isEnabled() ? plugin.getShardsHook().getShards(player.getUniqueId()) : 0;
        Portfolio portfolio = plugin.getInvestmentManager().getOrCreatePortfolio(player.getUniqueId());

        double t1M = plugin.getConfig().getDouble("portfolio.tiers.tier1.money-cost", 5000);
        long t1S = plugin.getConfig().getLong("portfolio.tiers.tier1.shards-cost", 500);
        double t2M = plugin.getConfig().getDouble("portfolio.tiers.tier2.money-cost", 25000);
        long t2S = plugin.getConfig().getLong("portfolio.tiers.tier2.shards-cost", 2500);
        double t3M = plugin.getConfig().getDouble("portfolio.tiers.tier3.money-cost", 100000);
        long t3S = plugin.getConfig().getLong("portfolio.tiers.tier3.shards-cost", 10000);

        SimpleForm.Builder builder = SimpleForm.builder()
            .title("§d✦ Portfolio kaufen ✦")
            .content("§7Um an der Börse zu handeln,\n§7benötigst du ein Portfolio!\n\n" +
                "§7Dein Geld: §a" + plugin.getEcoMoneyHook().formatMoney(balance) + "\n" +
                "§7Deine Shards: §d✦ " + plugin.getShardsHook().formatShards(shards) + "\n" +
                "§7Aktuell: " + portfolio.getTierName() + "\n\n" +
                "§7Kosten: §aGeld §7+ §dShards §7(beides nötig!)");

        if (portfolio.getPortfolioTier() < 1) {
            builder.button("§a✦ Basis\n" + plugin.getEcoMoneyHook().formatMoney(t1M) + " + " + t1S + " Shards");
        }
        if (portfolio.getPortfolioTier() < 2) {
            builder.button("§b✦✦ Premium\n" + plugin.getEcoMoneyHook().formatMoney(t2M) + " + " + t2S + " Shards");
        }
        if (portfolio.getPortfolioTier() < 3) {
            builder.button("§6✦✦✦ Elite\n" + plugin.getEcoMoneyHook().formatMoney(t3M) + " + " + t3S + " Shards");
        }
        builder.button("§cSchließen");

        int currentTier = portfolio.getPortfolioTier();
        builder.validResultHandler(response -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                int clicked = response.clickedButtonId();
                int targetTier = currentTier + 1 + clicked;
                if (targetTier > 3) return;

                var result = plugin.getInvestmentManager().purchasePortfolio(player.getUniqueId(), targetTier);
                String pfx = plugin.getConfig().getString("messages.prefix", "§8[§6§lInvest§8] ");
                player.sendMessage(pfx + result.getMessage());
                if (result == InvestmentManager.UnlockResult.SUCCESS) {
                    Portfolio p = plugin.getInvestmentManager().getOrCreatePortfolio(player.getUniqueId());
                    player.sendMessage(pfx + "§a§l🎉 " + p.getTierName() + " §a§lPortfolio aktiviert!");
                    openMainMenu(player);
                }
            });
        });

        floodgateApi.sendForm(player.getUniqueId(), builder.build());
    }

    // ==================== KATEGORIE ====================

    public void openCategoryMenu(Player player, AssetType type) {
        List<Asset> assets = plugin.getInvestmentManager().getAssetsByType(type);

        SimpleForm.Builder builder = SimpleForm.builder()
            .title(type.getDisplayName())
            .content("§7Wähle ein Asset zum Handeln:\n");

        for (Asset asset : assets) {
            String label = asset.getChangeArrow() + " " + asset.getName() + " (" + asset.getTicker() + ")\n" +
                "§7" + asset.getFormattedPrice() + " " + asset.getFormattedChange();
            builder.button(label);
        }
        builder.button("§c← Zurück");

        builder.validResultHandler(response -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                int clicked = response.clickedButtonId();
                if (clicked < assets.size()) {
                    openAssetDetail(player, assets.get(clicked));
                } else {
                    openMainMenu(player);
                }
            });
        });

        floodgateApi.sendForm(player.getUniqueId(), builder.build());
    }

    // ==================== ASSET DETAIL ====================

    public void openAssetDetail(Player player, Asset asset) {
        Portfolio portfolio = plugin.getInvestmentManager().getOrCreatePortfolio(player.getUniqueId());
        Investment inv = portfolio.getInvestment(asset.getId());

        StringBuilder content = new StringBuilder();
        content.append("§f").append(asset.getName()).append(" (").append(asset.getTicker()).append(")\n\n");
        content.append("§7Typ: ").append(asset.getType().getDisplayName()).append("\n");
        content.append("§7Preis: §f").append(asset.getFormattedPrice()).append(" ").append(asset.getChangeArrow()).append("\n");
        content.append("§7Änderung: ").append(asset.getFormattedChange()).append("\n");
        content.append("§7Volatilität: ").append(asset.getVolatility().getDisplay()).append("\n");
        if (asset.getDividendYield() > 0) {
            content.append("§7Dividende: §a").append(String.format("%.1f%%", asset.getDividendYield())).append("\n");
        }
        content.append("§7ATH: §a").append(String.format("%.2f$", asset.getAllTimeHigh())).append("\n");
        content.append("§7ATL: §c").append(String.format("%.2f$", asset.getAllTimeLow())).append("\n");

        if (asset.hasActiveNews()) {
            content.append("\n").append(asset.getCurrentNews()).append("\n");
        }

        if (inv != null && !inv.isEmpty()) {
            double val = inv.getCurrentValue(asset.getCurrentPrice());
            double pl = inv.getProfitLossPercent(asset.getCurrentPrice());
            String c = pl >= 0 ? "§a+" : "§c";
            content.append("\n§6═══ Deine Position ═══\n");
            content.append("§7Anteile: §f").append(String.format("%.4f", inv.getShares())).append("\n");
            content.append("§7Wert: §e").append(plugin.getEcoMoneyHook().formatMoney(val)).append("\n");
            content.append("§7P/L: ").append(c).append(String.format("%.2f%%", pl)).append("\n");
        }

        SimpleForm.Builder builder = SimpleForm.builder()
            .title(asset.getChangeArrow() + " " + asset.getTicker() + " - " + asset.getFormattedPrice())
            .content(content.toString())
            .button("§a🛒 Kaufen")
            .button("§c💰 Verkaufen");

        if (inv != null && !inv.isEmpty()) {
            builder.button("§e⚙ Einstellungen");
        }
        builder.button("§c← Zurück");

        builder.validResultHandler(response -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                int clicked = response.clickedButtonId();
                if (clicked == 0) openBuyForm(player, asset);
                else if (clicked == 1) openSellForm(player, asset);
                else if (clicked == 2 && inv != null && !inv.isEmpty()) openSettingsForm(player, asset);
                else openCategoryMenu(player, asset.getType());
            });
        });

        floodgateApi.sendForm(player.getUniqueId(), builder.build());
    }

    // ==================== KAUF FORM ====================

    public void openBuyForm(Player player, Asset asset) {
        double balance = plugin.getEcoMoneyHook().getBalance(player.getUniqueId());
        double minI = plugin.getInvestmentManager().getMinInvestment();

        CustomForm form = CustomForm.builder()
            .title("§a🛒 " + asset.getName() + " kaufen")
            .label("§7Aktueller Preis: §f" + asset.getFormattedPrice() + "\n" +
                "§7Dein Geld: §a" + plugin.getEcoMoneyHook().formatMoney(balance) + "\n" +
                "§7Min. Investment: §f" + plugin.getEcoMoneyHook().formatMoney(minI) + "\n" +
                "§7Gebühr: §c" + plugin.getInvestmentManager().getTransactionFeePercent() + "%")
            .input("Betrag eingeben", String.valueOf((int) minI), String.valueOf((int) minI))
            .toggle("Schnellkauf-Beträge:", false)
            .dropdown("Vordefiniert:", List.of(
                plugin.getEcoMoneyHook().formatMoney(minI),
                plugin.getEcoMoneyHook().formatMoney(minI * 5),
                plugin.getEcoMoneyHook().formatMoney(minI * 10),
                plugin.getEcoMoneyHook().formatMoney(minI * 50)
            ), 0)
            .validResultHandler(response -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    double amount;
                    boolean usePreset = response.asToggle(2);
                    if (usePreset) {
                        int idx = response.asDropdown(3);
                        double[] presets = {minI, minI * 5, minI * 10, minI * 50};
                        amount = presets[idx];
                    } else {
                        try { amount = Double.parseDouble(response.asInput(1)); }
                        catch (NumberFormatException e) { player.sendMessage("§cUngültige Zahl!"); return; }
                    }
                    String pfx = plugin.getConfig().getString("messages.prefix", "§8[§6§lInvest§8] ");
                    InvestmentManager.TradeResult result = plugin.getInvestmentManager().buy(player.getUniqueId(), asset.getId(), amount);
                    player.sendMessage(pfx + result.getMessage());
                    if (result == InvestmentManager.TradeResult.SUCCESS) {
                        player.sendMessage(pfx + "§a" + String.format("%.4f", amount / asset.getCurrentPrice()) +
                            " Anteile von §f" + asset.getName() + " §agekauft!");
                    }
                });
            })
            .build();
        floodgateApi.sendForm(player.getUniqueId(), form);
    }

    // ==================== VERKAUF FORM ====================

    public void openSellForm(Player player, Asset asset) {
        Portfolio portfolio = plugin.getInvestmentManager().getOrCreatePortfolio(player.getUniqueId());
        Investment inv = portfolio.getInvestment(asset.getId());

        if (inv == null || inv.isEmpty()) {
            player.sendMessage("§cDu besitzt keine Anteile von " + asset.getName() + "!");
            return;
        }

        double val = inv.getCurrentValue(asset.getCurrentPrice());

        CustomForm form = CustomForm.builder()
            .title("§c💰 " + asset.getName() + " verkaufen")
            .label("§7Deine Anteile: §f" + String.format("%.4f", inv.getShares()) + "\n" +
                "§7Aktueller Wert: §e" + plugin.getEcoMoneyHook().formatMoney(val) + "\n" +
                "§7Gebühr: §c" + plugin.getInvestmentManager().getTransactionFeePercent() + "%")
            .slider("Verkaufsanteil (%)", 10, 100, 10, 100)
            .validResultHandler(response -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    float percent = response.asSlider(1);
                    double sellPercent = percent / 100.0;

                    TradeResult result = plugin.getInvestmentManager().sell(player.getUniqueId(), asset.getId(), sellPercent);
                    String prefix = plugin.getConfig().getString("messages.prefix", "§8[§6§lInvest§8] ");
                    player.sendMessage(prefix + result.getMessage());
                    if (result == TradeResult.SUCCESS) {
                        double fee = val * sellPercent * (plugin.getInvestmentManager().getTransactionFeePercent() / 100.0);
                        player.sendMessage(prefix + "§c" + String.format("%.0f%%", percent) +
                            " verkauft für §f" + plugin.getEcoMoneyHook().formatMoney(val * sellPercent - fee));
                    }
                });
            })
            .build();

        floodgateApi.sendForm(player.getUniqueId(), form);
    }

    // ==================== PORTFOLIO ====================

    public void openPortfolioForm(Player player) {
        Portfolio portfolio = plugin.getInvestmentManager().getOrCreatePortfolio(player.getUniqueId());
        Map<String, Asset> assets = plugin.getInvestmentManager().getAssets();

        double totalV = portfolio.getTotalValue(assets);
        double perf = portfolio.getPerformancePercent(assets);
        String pC = perf >= 0 ? "§a+" : "§c";

        StringBuilder content = new StringBuilder();
        content.append("§6═══ Portfolio Übersicht ═══\n\n");
        content.append("§7Gesamtwert: §e").append(plugin.getEcoMoneyHook().formatMoney(totalV)).append("\n");
        content.append("§7Performance: ").append(pC).append(String.format("%.2f%%", perf)).append("\n");
        content.append("§7Dividenden: §a").append(plugin.getEcoMoneyHook().formatMoney(portfolio.getTotalDividendsReceived())).append("\n\n");

        List<Investment> activeInvs = new ArrayList<>();
        for (Investment inv : portfolio.getInvestments()) {
            if (inv.isEmpty()) continue;
            activeInvs.add(inv);
            Asset asset = assets.get(inv.getAssetId());
            if (asset == null) continue;
            double pl = inv.getProfitLossPercent(asset.getCurrentPrice());
            String c = pl >= 0 ? "§a+" : "§c";
            content.append(asset.getChangeArrow()).append(" §f").append(asset.getTicker())
                .append(" §7").append(plugin.getEcoMoneyHook().formatMoney(inv.getCurrentValue(asset.getCurrentPrice())))
                .append(" ").append(c).append(String.format("%.1f%%", pl)).append("\n");
        }

        if (activeInvs.isEmpty()) {
            content.append("§cKeine Investments vorhanden.\n");
        }

        SimpleForm.Builder builder = SimpleForm.builder()
            .title("§a💼 Mein Portfolio")
            .content(content.toString());

        for (Investment inv : activeInvs) {
            Asset asset = assets.get(inv.getAssetId());
            if (asset != null) {
                builder.button(asset.getChangeArrow() + " " + asset.getName() + "\n§7" + asset.getFormattedPrice());
            }
        }
        builder.button("§c← Zurück");

        builder.validResultHandler(response -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                int clicked = response.clickedButtonId();
                if (clicked < activeInvs.size()) {
                    Asset asset = assets.get(activeInvs.get(clicked).getAssetId());
                    if (asset != null) openAssetDetail(player, asset);
                } else {
                    openMainMenu(player);
                }
            });
        });

        floodgateApi.sendForm(player.getUniqueId(), builder.build());
    }

    // ==================== EINSTELLUNGEN ====================

    public void openSettingsForm(Player player, Asset asset) {
        Portfolio portfolio = plugin.getInvestmentManager().getOrCreatePortfolio(player.getUniqueId());
        Investment inv = portfolio.getInvestment(asset.getId());
        if (inv == null) return;

        CustomForm form = CustomForm.builder()
            .title("§e⚙ " + asset.getTicker() + " Einstellungen")
            .toggle("Auto-Reinvest (Dividenden)", inv.isAutoReinvest())
            .slider("Stop-Loss (% Verlust, 0=Aus)", 0, 50, 5, (int) Math.max(0, inv.getStopLossPercent()))
            .slider("Take-Profit (% Gewinn, 0=Aus)", 0, 200, 5, (int) Math.max(0, inv.getTakeProfitPercent()))
            .validResultHandler(response -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    inv.setAutoReinvest(response.asToggle(0));
                    float sl = response.asSlider(1);
                    inv.setStopLossPercent(sl > 0 ? sl : -1);
                    float tp = response.asSlider(2);
                    inv.setTakeProfitPercent(tp > 0 ? tp : -1);

                    plugin.getInvestmentManager().savePortfolio(player.getUniqueId());
                    String prefix = plugin.getConfig().getString("messages.prefix", "§8[§6§lInvest§8] ");
                    player.sendMessage(prefix + "§aEinstellungen gespeichert!");
                });
            })
            .build();

        floodgateApi.sendForm(player.getUniqueId(), form);
    }

    // ==================== LEADERBOARD ====================

    public void openLeaderboardForm(Player player) {
        List<Map.Entry<UUID, Double>> top = plugin.getInvestmentManager().getTopInvestors(10);

        StringBuilder content = new StringBuilder("§d§l🏆 Top 10 Investoren\n\n");
        String[] medals = {"§6🥇", "§7🥈", "§e🥉", "§f4.", "§f5.", "§f6.", "§f7.", "§f8.", "§f9.", "§f10."};

        int rank = 0;
        for (Map.Entry<UUID, Double> entry : top) {
            if (rank >= 10) break;
            String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            content.append(medals[rank]).append(" §f").append(name != null ? name : "?")
                .append(" §7- §6").append(plugin.getEcoMoneyHook().formatMoney(entry.getValue())).append("\n");
            rank++;
        }

        if (top.isEmpty()) content.append("§7Noch keine Investoren.\n");

        SimpleForm form = SimpleForm.builder()
            .title("§d🏆 Top Investoren")
            .content(content.toString())
            .button("§c← Zurück")
            .validResultHandler(response -> {
                Bukkit.getScheduler().runTask(plugin, () -> openMainMenu(player));
            })
            .build();

        floodgateApi.sendForm(player.getUniqueId(), form);
    }
}
