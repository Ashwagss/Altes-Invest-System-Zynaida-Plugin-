package net.zynaida.invest.gui;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.zynaida.invest.ZynaidaInvest;
import net.zynaida.invest.gui.InvestHolder.GUIType;
import net.zynaida.invest.models.Asset;
import net.zynaida.invest.models.Asset.AssetType;
import net.zynaida.invest.models.Investment;
import net.zynaida.invest.models.Portfolio;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class InvestGUI {

    private final ZynaidaInvest plugin;

    public InvestGUI(ZynaidaInvest plugin) {
        this.plugin = plugin;
    }

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    // ==================== INVENTORY ERSTELLEN ====================

    private Inventory createGUI(int size, String title, GUIType type) {
        return createGUI(size, title, type, "");
    }

    private Inventory createGUI(int size, String title, GUIType type, String data) {
        InvestHolder holder = new InvestHolder(type, data);
        Inventory gui = Bukkit.createInventory(holder, size, LEGACY.deserialize(title));
        holder.setInventory(gui);
        return gui;
    }

    // ==================== HAUPTMENÜ ====================

    public void openMainMenu(Player player) {
        plugin.getInvestmentManager().reloadPortfolioFromDB(player.getUniqueId());
        Portfolio portfolio = plugin.getInvestmentManager().getOrCreatePortfolio(player.getUniqueId());

        if (!portfolio.isUnlocked()) {
            openUnlockGUI(player);
            return;
        }

        Inventory gui = createGUI(54, "§8§l「 §6§lZynaida §e§lBörse §8§l」", GUIType.MAIN);
        fillBackground(gui, Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < 9; i++) gui.setItem(i, createGlass(Material.GRAY_STAINED_GLASS_PANE));
        for (int i = 45; i < 54; i++) gui.setItem(i, createGlass(Material.GRAY_STAINED_GLASS_PANE));

        double balance = plugin.getEcoMoneyHook().getBalance(player.getUniqueId());
        double portfolioValue = portfolio.getTotalValue(plugin.getInvestmentManager().getAssets());

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        sm.setOwningPlayer(player);
        sm.setDisplayName("§6§l⬢ §e§l" + player.getName() + " §6§l⬢");
        List<String> headLore = new ArrayList<>(Arrays.asList(
            "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
            "§7Geld: §a" + plugin.getEcoMoneyHook().formatMoney(balance),
            "§7Portfolio: §e" + plugin.getEcoMoneyHook().formatMoney(portfolioValue),
            "§7Gesamt: §6" + plugin.getEcoMoneyHook().formatMoney(balance + portfolioValue),
            "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
            "§7Tier: " + portfolio.getTierName(),
            "§7Positionen: §f" + portfolio.getPositionCount() + "/" + portfolio.getMaxPositions(),
            "§7Dividenden: §a" + plugin.getEcoMoneyHook().formatMoney(portfolio.getTotalDividendsReceived()),
            "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"
        ));
        if (portfolio.getPortfolioTier() < 3) {
            headLore.add("§e↑ Upgrade verfügbar! Klicke hier");
        }
        sm.setLore(headLore);
        head.setItemMeta(sm);
        gui.setItem(4, head);

        gui.setItem(0, createItem(Material.COMPASS, "§e§l Markt-Stimmung",
            "§7Sentiment: " + plugin.getMarketSimulator().getSentimentDisplay()));

        gui.setItem(20, createCatItem(Material.EMERALD, "§a§l Aktien", AssetType.STOCK, "§7Dividenden & Wachstum"));
        gui.setItem(22, createCatItem(Material.GOLD_INGOT, "§6§l Krypto", AssetType.CRYPTO, "§7Hohes Risiko"));
        gui.setItem(24, createCatItem(Material.PAPER, "§b§l Anleihen", AssetType.BOND, "§7Stabile Rendite"));
        gui.setItem(30, createCatItem(Material.DIAMOND, "§e§l Rohstoffe", AssetType.COMMODITY, "§7Sachwerte"));
        gui.setItem(32, createCatItem(Material.BOOK, "§d§l ETFs", AssetType.ETF, "§7Diversifiziert"));

        gui.setItem(38, createItem(Material.CHEST, "§a§l Portfolio", "§eKlicke zum Öffnen!"));
        gui.setItem(40, createItem(Material.GOLDEN_HELMET, "§d§l Top Investoren", "§eKlicke zum Anzeigen!"));

        createTickerBar(gui);
        player.openInventory(gui);
    }

    // ==================== PORTFOLIO KAUF ====================

    public void openUnlockGUI(Player player) {
        Inventory gui = createGUI(54, "§8§l「 §d§lPortfolio kaufen §8§l」", GUIType.UNLOCK);
        fillBackground(gui, Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < 9; i++) gui.setItem(i, createGlass(Material.GRAY_STAINED_GLASS_PANE));
        for (int i = 45; i < 54; i++) gui.setItem(i, createGlass(Material.GRAY_STAINED_GLASS_PANE));

        Portfolio portfolio = plugin.getInvestmentManager().getOrCreatePortfolio(player.getUniqueId());
        double balance = plugin.getEcoMoneyHook().getBalance(player.getUniqueId());
        long shards = plugin.getShardsHook().isEnabled() ? plugin.getShardsHook().getShards(player.getUniqueId()) : 0;
        int currentTier = portfolio.getPortfolioTier();

        gui.setItem(4, createItem(Material.NETHER_STAR, "§d§l Portfolio kaufen",
            "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
            "§7Um an der Börse zu handeln,",
            "§7benötigst du ein Portfolio!",
            "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
            "§7Dein Geld: §a" + plugin.getEcoMoneyHook().formatMoney(balance),
            "§7Deine Shards: §d" + (plugin.getShardsHook().isEnabled() ? plugin.getShardsHook().formatShards(shards) : "§cN/A"),
            "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
            "§7Aktuell: " + portfolio.getTierName(),
            "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));

        // Tier 1
        double t1Money = plugin.getConfig().getDouble("portfolio.tiers.tier1.money-cost", 5000);
        long t1Shards = plugin.getConfig().getLong("portfolio.tiers.tier1.shards-cost", 500);
        boolean t1Owned = currentTier >= 1;
        boolean t1Afford = balance >= t1Money && shards >= t1Shards;
        gui.setItem(20, createItem(t1Owned ? Material.LIME_STAINED_GLASS_PANE : (t1Afford ? Material.EMERALD_BLOCK : Material.RED_STAINED_GLASS_PANE),
            "§a§l Basis Portfolio",
            "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
            "§7Max. Positionen: §f5",
            "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
            "§7Kosten:",
            "  §a" + plugin.getEcoMoneyHook().formatMoney(t1Money) + (balance >= t1Money ? " §a[OK]" : " §c[X]"),
            "  §d" + plugin.getShardsHook().formatShards(t1Shards) + " Shards" + (shards >= t1Shards ? " §a[OK]" : " §c[X]"),
            "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
            t1Owned ? "§a§l FREIGESCHALTET" : (t1Afford ? "§a§lKlicke zum Kaufen!" : "§c§lNicht genug Mittel!")));

        // Tier 2
        double t2Money = plugin.getConfig().getDouble("portfolio.tiers.tier2.money-cost", 25000);
        long t2Shards = plugin.getConfig().getLong("portfolio.tiers.tier2.shards-cost", 2500);
        boolean t2Owned = currentTier >= 2;
        boolean t2Afford = balance >= t2Money && shards >= t2Shards;
        gui.setItem(22, createItem(t2Owned ? Material.LIME_STAINED_GLASS_PANE : (t2Afford ? Material.DIAMOND_BLOCK : Material.RED_STAINED_GLASS_PANE),
            "§b§l Premium Portfolio",
            "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
            "§7Max. Positionen: §f10",
            "§7Bonus: §a-25% Gebühren",
            "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
            "§7Kosten:",
            "  §a" + plugin.getEcoMoneyHook().formatMoney(t2Money) + (balance >= t2Money ? " §a[OK]" : " §c[X]"),
            "  §d" + plugin.getShardsHook().formatShards(t2Shards) + " Shards" + (shards >= t2Shards ? " §a[OK]" : " §c[X]"),
            "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
            t2Owned ? "§a§l FREIGESCHALTET" : (t2Afford ? "§b§lKlicke zum Kaufen!" : "§c§lNicht genug Mittel!")));

        // Tier 3
        double t3Money = plugin.getConfig().getDouble("portfolio.tiers.tier3.money-cost", 100000);
        long t3Shards = plugin.getConfig().getLong("portfolio.tiers.tier3.shards-cost", 10000);
        boolean t3Owned = currentTier >= 3;
        boolean t3Afford = balance >= t3Money && shards >= t3Shards;
        gui.setItem(24, createItem(t3Owned ? Material.LIME_STAINED_GLASS_PANE : (t3Afford ? Material.NETHERITE_BLOCK : Material.RED_STAINED_GLASS_PANE),
            "§6§l Elite Portfolio",
            "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
            "§7Max. Positionen: §f20",
            "§7Bonus: §a-50% Gebühren",
            "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
            "§7Kosten:",
            "  §a" + plugin.getEcoMoneyHook().formatMoney(t3Money) + (balance >= t3Money ? " §a[OK]" : " §c[X]"),
            "  §d" + plugin.getShardsHook().formatShards(t3Shards) + " Shards" + (shards >= t3Shards ? " §a[OK]" : " §c[X]"),
            "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
            t3Owned ? "§a§l FREIGESCHALTET" : (t3Afford ? "§6§lKlicke zum Kaufen!" : "§c§lNicht genug Mittel!")));

        gui.setItem(31, createItem(Material.OAK_SIGN, "§e§l Info",
            "§7Portfolio-Tiers sind §fpermanent§7!",
            "§7Bezahlung: §aGeld §7+ §dShards"));
        gui.setItem(49, createItem(Material.BARRIER, "§c§lSchließen", ""));
        player.openInventory(gui);
    }

    // ==================== KATEGORIE ====================

    public void openCategory(Player player, AssetType type) {
        List<Asset> catAssets = plugin.getInvestmentManager().getAssetsByType(type);
        Inventory gui = createGUI(36, "§8§l「 " + type.getColor() + "§l" + type.getDisplayName() + " §8§l」",
            GUIType.CATEGORY, type.name());
        fillBackground(gui, Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < 9; i++) gui.setItem(i, createGlass(Material.GRAY_STAINED_GLASS_PANE));
        for (int i = 27; i < 36; i++) gui.setItem(i, createGlass(Material.GRAY_STAINED_GLASS_PANE));

        gui.setItem(4, createItem(type.getIcon(), type.getColor() + "§l" + type.getDisplayName(), "§7Wähle ein Asset zum Handeln"));

        Portfolio portfolio = plugin.getInvestmentManager().getOrCreatePortfolio(player.getUniqueId());
        int slot = 10;
        for (Asset asset : catAssets) {
            if (slot % 9 == 0) slot++;
            if (slot % 9 == 8) slot += 2;
            if (slot >= 27) break;
            gui.setItem(slot, createAssetItem(asset, portfolio));
            slot++;
        }

        gui.setItem(31, createItem(Material.ARROW, "§c§l<- Zurück", "§7Zum Hauptmenü"));
        player.openInventory(gui);
    }

    // ==================== ASSET DETAIL ====================

    public void openAssetDetail(Player player, Asset asset) {
        plugin.getInvestmentManager().reloadPortfolioFromDB(player.getUniqueId());
        Inventory gui = createGUI(54, "§8§l「 §f" + asset.getTicker() + " §8§l」",
            GUIType.ASSET_DETAIL, asset.getTicker());
        fillBackground(gui, Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < 9; i++) gui.setItem(i, createGlass(Material.GRAY_STAINED_GLASS_PANE));
        for (int i = 45; i < 54; i++) gui.setItem(i, createGlass(Material.GRAY_STAINED_GLASS_PANE));

        Portfolio portfolio = plugin.getInvestmentManager().getOrCreatePortfolio(player.getUniqueId());
        Investment inv = portfolio.getInvestment(asset.getId());

        List<String> info = new ArrayList<>(Arrays.asList(
            "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
            "§7Typ: " + asset.getType().getDisplayName(),
            "§7Volatilität: " + asset.getVolatility().getDisplay(),
            "§7Preis: §f" + asset.getFormattedPrice() + " " + asset.getChangeArrow(),
            "§7Änderung: " + asset.getFormattedChange(),
            "§7Chart: " + asset.getSparkline(),
            "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
            "§7ATH: §a" + String.format("%.2f$", asset.getAllTimeHigh()),
            "§7ATL: §c" + String.format("%.2f$", asset.getAllTimeLow())
        ));
        if (asset.getDividendYield() > 0) info.add("§7Dividende: §a" + String.format("%.1f%%", asset.getDividendYield()));
        if (asset.hasActiveNews()) { info.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"); info.add(asset.getCurrentNews()); }
        info.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        gui.setItem(4, createItem(asset.getGuiIcon(), asset.getType().getColor() + "§l" + asset.getName() + " §7(" + asset.getTicker() + ")", info.toArray(new String[0])));

        createChartDisplay(gui, asset);

        // Kauf-Buttons (nur Geld)
        double minI = plugin.getInvestmentManager().getMinInvestment();
        double[] buyAmts = {minI, minI * 5, minI * 10, minI * 50, minI * 100};
        int[] buySlots = {29, 30, 31, 32, 33};
        for (int i = 0; i < buyAmts.length; i++) {
            double shares = buyAmts[i] / asset.getCurrentPrice();
            gui.setItem(buySlots[i], createItem(Material.LIME_STAINED_GLASS_PANE,
                "§a§l " + plugin.getEcoMoneyHook().formatMoney(buyAmts[i]),
                "§7Anteile: §f" + String.format("%.4f", shares),
                "§7Gebühr: §c" + String.format("%.1f%%", plugin.getInvestmentManager().getTransactionFeePercent()),
                "§aKlicke zum Kaufen!"));
        }

        if (inv != null && !inv.isEmpty()) {
            double val = inv.getCurrentValue(asset.getCurrentPrice());
            double pl = inv.getProfitLoss(asset.getCurrentPrice());
            String plC = pl >= 0 ? "§a+" : "§c";
            gui.setItem(13, createItem(Material.GOLD_BLOCK, "§6§l Deine Position",
                "§7Anteile: §f" + String.format("%.4f", inv.getShares()),
                "§7Kaufpreis: §f" + String.format("%.2f$", inv.getBuyPrice()),
                "§7Wert: §e" + plugin.getEcoMoneyHook().formatMoney(val),
                "§7P/L: " + plC + String.format("%.2f%%", inv.getProfitLossPercent(asset.getCurrentPrice()))));

            gui.setItem(38, createItem(Material.RED_STAINED_GLASS_PANE, "§c25% Verkaufen", "§7= " + plugin.getEcoMoneyHook().formatMoney(val * 0.25)));
            gui.setItem(39, createItem(Material.RED_STAINED_GLASS_PANE, "§c50% Verkaufen", "§7= " + plugin.getEcoMoneyHook().formatMoney(val * 0.50)));
            gui.setItem(40, createItem(Material.RED_STAINED_GLASS_PANE, "§c75% Verkaufen", "§7= " + plugin.getEcoMoneyHook().formatMoney(val * 0.75)));
            gui.setItem(41, createItem(Material.RED_STAINED_GLASS_PANE, "§4§lALLES Verkaufen", "§7= " + plugin.getEcoMoneyHook().formatMoney(val)));
            gui.setItem(42, createItem(Material.REPEATER, "§e§l Einstellungen",
                "§7Stop-Loss: " + (inv.getStopLossPercent() >= 0 ? "§c" + inv.getStopLossPercent() + "%" : "§7Aus"),
                "§7Take-Profit: " + (inv.getTakeProfitPercent() >= 0 ? "§a" + inv.getTakeProfitPercent() + "%" : "§7Aus"),
                "§7Auto-Reinvest: " + (inv.isAutoReinvest() ? "§aAN" : "§cAUS"),
                "§eKlicke zum Ändern!"));
        }

        gui.setItem(49, createItem(Material.ARROW, "§c§l<- Zurück", "§7Zur Kategorie"));
        player.openInventory(gui);
    }

    // ==================== PORTFOLIO ====================

    public void openPortfolio(Player player) {
        plugin.getInvestmentManager().reloadPortfolioFromDB(player.getUniqueId());
        Portfolio portfolio = plugin.getInvestmentManager().getOrCreatePortfolio(player.getUniqueId());
        if (!portfolio.isUnlocked()) { openUnlockGUI(player); return; }

        Map<String, Asset> assets = plugin.getInvestmentManager().getAssets();
        Inventory gui = createGUI(54, "§8§l「 §a§lMein Portfolio §8§l」", GUIType.PORTFOLIO);
        fillBackground(gui, Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < 9; i++) gui.setItem(i, createGlass(Material.GRAY_STAINED_GLASS_PANE));
        for (int i = 45; i < 54; i++) gui.setItem(i, createGlass(Material.GRAY_STAINED_GLASS_PANE));

        double totalV = portfolio.getTotalValue(assets);
        double totalI = portfolio.getTotalInvested();
        double perf = portfolio.getPerformancePercent(assets);
        String pC = perf >= 0 ? "§a+" : "§c";

        gui.setItem(4, createItem(Material.CHEST, "§6§l Übersicht §7| " + portfolio.getTierName(),
            "§7Wert: §e" + plugin.getEcoMoneyHook().formatMoney(totalV),
            "§7Investiert: §f" + plugin.getEcoMoneyHook().formatMoney(totalI),
            "§7Performance: " + pC + String.format("%.2f%%", perf),
            "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
            "§7Positionen: §f" + portfolio.getPositionCount() + "/" + portfolio.getMaxPositions(),
            "§7Realisiert: §a" + plugin.getEcoMoneyHook().formatMoney(portfolio.getTotalRealizedProfit()),
            "§7Dividenden: §a" + plugin.getEcoMoneyHook().formatMoney(portfolio.getTotalDividendsReceived())));

        int slot = 10;
        for (Investment inv : portfolio.getInvestments()) {
            if (inv.isEmpty()) continue;
            if (slot % 9 == 0) slot++;
            if (slot % 9 == 8) slot += 2;
            if (slot >= 45) break;
            Asset asset = assets.get(inv.getAssetId());
            if (asset == null) continue;
            double pl = inv.getProfitLossPercent(asset.getCurrentPrice());
            String c = pl >= 0 ? "§a+" : "§c";
            gui.setItem(slot, createItem(asset.getGuiIcon(),
                asset.getType().getColor() + asset.getName() + " §7(" + asset.getTicker() + ")",
                "§7Preis: §f" + asset.getFormattedPrice() + " " + asset.getChangeArrow(),
                "§7Anteile: §f" + String.format("%.4f", inv.getShares()),
                "§7Wert: §e" + plugin.getEcoMoneyHook().formatMoney(inv.getCurrentValue(asset.getCurrentPrice())),
                "§7P/L: " + c + String.format("%.2f%%", pl),
                "§eKlicke für Details"));
            slot++;
        }

        if (portfolio.getPositionCount() == 0) {
            gui.setItem(22, createItem(Material.BARRIER, "§cKeine Investments", "§7Kaufe dein erstes Asset!"));
        }

        gui.setItem(49, createItem(Material.ARROW, "§c§l<- Zurück", "§7Zum Hauptmenü"));
        player.openInventory(gui);
    }

    // ==================== LEADERBOARD ====================

    public void openLeaderboard(Player player) {
        Inventory gui = createGUI(54, "§8§l「 §d§lTop Investoren §8§l」", GUIType.LEADERBOARD);
        fillBackground(gui, Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < 9; i++) gui.setItem(i, createGlass(Material.GRAY_STAINED_GLASS_PANE));
        for (int i = 45; i < 54; i++) gui.setItem(i, createGlass(Material.GRAY_STAINED_GLASS_PANE));

        gui.setItem(4, createItem(Material.GOLDEN_HELMET, "§d§l Top 10 Investoren", "§7Die erfolgreichsten Anleger"));

        Material[] t = {Material.GOLD_BLOCK, Material.IRON_BLOCK, Material.COPPER_BLOCK,
            Material.EMERALD, Material.EMERALD, Material.EMERALD, Material.EMERALD, Material.EMERALD, Material.EMERALD, Material.EMERALD};
        String[] rc = {"§6§l", "§7§l", "§e§l", "§f", "§f", "§f", "§f", "§f", "§f", "§f"};

        List<Map.Entry<UUID, Double>> top = plugin.getInvestmentManager().getTopInvestors(10);
        int slot = 19; int rank = 0;
        for (Map.Entry<UUID, Double> e : top) {
            if (slot % 9 == 0) slot++;
            if (slot % 9 == 8) slot += 2;
            if (slot >= 36 || rank >= 10) break;
            String name = Bukkit.getOfflinePlayer(e.getKey()).getName();
            gui.setItem(slot, createItem(t[rank], rc[rank] + "#" + (rank + 1) + " §f" + (name != null ? name : "?"),
                "§7Wert: §6" + plugin.getEcoMoneyHook().formatMoney(e.getValue())));
            slot++; rank++;
        }

        gui.setItem(49, createItem(Material.ARROW, "§c§l<- Zurück", "§7Zum Hauptmenü"));
        player.openInventory(gui);
    }

    // ==================== SETTINGS ====================

    public void openSettingsGUI(Player player, Asset asset) {
        Portfolio portfolio = plugin.getInvestmentManager().getOrCreatePortfolio(player.getUniqueId());
        Investment inv = portfolio.getInvestment(asset.getId());
        if (inv == null) return;

        Inventory gui = createGUI(27, "§8§l「 §e§l " + asset.getTicker() + " §8§l」",
            GUIType.SETTINGS, asset.getTicker());
        fillBackground(gui, Material.BLACK_STAINED_GLASS_PANE);

        gui.setItem(11, createItem(inv.isAutoReinvest() ? Material.LIME_DYE : Material.GRAY_DYE,
            "§e§l Auto-Reinvest", "§7Status: " + (inv.isAutoReinvest() ? "§aAKTIV" : "§cINAKTIV"),
            "§7Dividenden automatisch reinvestieren", "§eKlicke zum Umschalten!"));

        gui.setItem(13, createItem(Material.RED_DYE, "§c§l Stop-Loss",
            "§7Aktuell: " + (inv.getStopLossPercent() >= 0 ? "§c-" + inv.getStopLossPercent() + "%" : "§7Aus"),
            "§eLinks: -5% | Rechts: +5%", "§eShift: Deaktivieren"));

        gui.setItem(15, createItem(Material.LIME_DYE, "§a§l Take-Profit",
            "§7Aktuell: " + (inv.getTakeProfitPercent() >= 0 ? "§a+" + inv.getTakeProfitPercent() + "%" : "§7Aus"),
            "§eLinks: -5% | Rechts: +5%", "§eShift: Deaktivieren"));

        gui.setItem(22, createItem(Material.ARROW, "§c§l<- Zurück", "§7Zum Asset"));
        player.openInventory(gui);
    }

    // ==================== HELPER ====================

    private void createChartDisplay(Inventory gui, Asset asset) {
        LinkedList<Double> h = asset.getPriceHistory();
        if (h.size() < 7) return;
        List<Double> r = new ArrayList<>(h.subList(Math.max(0, h.size() - 7), h.size()));
        int[] slots = {19, 20, 21, 22, 23, 24, 25};
        for (int i = 0; i < r.size() && i < slots.length; i++) {
            Material m = (i > 0 && r.get(i) >= r.get(i - 1)) ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
            if (i == 0) m = Material.YELLOW_STAINED_GLASS_PANE;
            gui.setItem(slots[i], createItem(m, "§f" + String.format("%.2f$", r.get(i))));
        }
    }

    private void createTickerBar(Inventory gui) {
        List<Asset> all = new ArrayList<>(plugin.getInvestmentManager().getAssets().values());
        int[] tickerSlots = {45, 46, 47, 48, 49, 50, 51, 52, 53};
        for (int i = 0; i < Math.min(all.size(), tickerSlots.length); i++) {
            Asset a = all.get(i);
            gui.setItem(tickerSlots[i], createItem(
                a.getChangePercent() >= 0 ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE,
                a.getChangeArrow() + " §f" + a.getTicker() + " " + a.getFormattedPrice(), a.getFormattedChange()));
        }
    }

    private ItemStack createCatItem(Material m, String name, AssetType type, String desc) {
        List<Asset> a = plugin.getInvestmentManager().getAssetsByType(type);
        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add(desc);
        lore.add("§7Assets: §f" + a.size());
        lore.add("");
        for (Asset asset : a) lore.add("  " + asset.getChangeArrow() + " §f" + asset.getTicker() + " §7" + asset.getFormattedPrice());
        lore.add("");
        lore.add("§eKlicke zum Handeln!");
        return createItem(m, name, lore.toArray(new String[0]));
    }

    private ItemStack createAssetItem(Asset asset, Portfolio portfolio) {
        List<String> lore = new ArrayList<>(Arrays.asList(
            "§7Preis: §f" + asset.getFormattedPrice() + " " + asset.getChangeArrow(),
            "§7Änderung: " + asset.getFormattedChange(),
            "§7Chart: " + asset.getSparkline(),
            "§7Volatilität: " + asset.getVolatility().getDisplay()));
        if (asset.getDividendYield() > 0) lore.add("§7Dividende: §a" + String.format("%.1f%%", asset.getDividendYield()));
        Investment inv = portfolio.getInvestment(asset.getId());
        if (inv != null && !inv.isEmpty()) {
            String c = inv.getProfitLossPercent(asset.getCurrentPrice()) >= 0 ? "§a+" : "§c";
            lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            lore.add("§7Anteile: §f" + String.format("%.4f", inv.getShares()));
            lore.add("§7P/L: " + c + String.format("%.2f%%", inv.getProfitLossPercent(asset.getCurrentPrice())));
        }
        lore.add(""); lore.add("§eKlicke für Details");
        return createItem(asset.getGuiIcon(), asset.getType().getColor() + asset.getName() + " §7(" + asset.getTicker() + ")", lore.toArray(new String[0]));
    }

    public static ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore.length > 0) meta.setLore(Arrays.asList(lore));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack createGlass(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" ");
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    public static void fillBackground(Inventory gui, Material material) {
        ItemStack bg = createGlass(material);
        for (int i = 0; i < gui.getSize(); i++) gui.setItem(i, bg);
    }
}
