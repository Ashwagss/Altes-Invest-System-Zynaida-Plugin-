package net.zynaida.invest.invest;

import net.zynaida.invest.ZynaidaInvest;
import net.zynaida.invest.models.Asset;
import net.zynaida.invest.models.Asset.AssetType;
import net.zynaida.invest.models.Asset.Volatility;
import net.zynaida.invest.models.Investment;
import net.zynaida.invest.models.Portfolio;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verwaltet alle Assets, Portfolios und Transaktionen
 */
public class InvestmentManager {

    private final ZynaidaInvest plugin;
    private final Map<String, Asset> assets = new LinkedHashMap<>();
    private final Map<UUID, Portfolio> portfolios = new ConcurrentHashMap<>();

    // Cooldown gegen Spam-Trading (UUID -> letzter Trade Timestamp)
    private final Map<UUID, Long> tradeCooldowns = new ConcurrentHashMap<>();
    private static final long TRADE_COOLDOWN_MS = 2000; // 2 Sekunden

    // Transaktionsgebühr
    private double transactionFeePercent;
    private double minInvestment;
    private double maxInvestment;
    private int maxPositions;

    public InvestmentManager(ZynaidaInvest plugin) {
        this.plugin = plugin;
        this.transactionFeePercent = plugin.getConfig().getDouble("investment.transaction-fee-percent", 1.5);
        this.minInvestment = plugin.getConfig().getDouble("investment.min-amount", 100);
        this.maxInvestment = plugin.getConfig().getDouble("investment.max-amount", 10000000);
        this.maxPositions = plugin.getConfig().getInt("investment.max-positions", 15);

        initializeAssets();
        loadAllPortfolios();
    }

    /**
     * Initialisiert alle handelbaren Assets aus der Config
     */
    private void initializeAssets() {
        // === AKTIEN ===
        registerAsset(new Asset("zynaida_corp", "Zynaida Corp.", "ZYN", AssetType.STOCK,
                Volatility.MEDIUM, 250.0, 2.5, Material.EMERALD_BLOCK));
        registerAsset(new Asset("redstone_tech", "Redstone Technologies", "RST", AssetType.STOCK,
                Volatility.HIGH, 180.0, 1.0, Material.REDSTONE_BLOCK));
        registerAsset(new Asset("ender_industries", "Ender Industries", "END", AssetType.STOCK,
                Volatility.MEDIUM, 420.0, 3.0, Material.ENDER_PEARL));
        registerAsset(new Asset("nether_energy", "Nether Energy AG", "NRG", AssetType.STOCK,
                Volatility.HIGH, 95.0, 0.5, Material.BLAZE_POWDER));
        registerAsset(new Asset("diamond_mining", "Diamond Mining Inc.", "DMI", AssetType.STOCK,
                Volatility.LOW, 550.0, 4.0, Material.DIAMOND));
        registerAsset(new Asset("creeper_defense", "Creeper Defense Corp.", "CDC", AssetType.STOCK,
                Volatility.MEDIUM, 310.0, 2.0, Material.TNT));

        // === KRYPTO ===
        registerAsset(new Asset("bitcoin_mc", "MineCoin", "MNC", AssetType.CRYPTO,
                Volatility.EXTREME, 5000.0, 0, Material.GOLD_BLOCK));
        registerAsset(new Asset("ether_mc", "BlockEther", "BET", AssetType.CRYPTO,
                Volatility.EXTREME, 2500.0, 0, Material.GOLD_INGOT));
        registerAsset(new Asset("doge_mc", "DogeCraft", "DCR", AssetType.CRYPTO,
                Volatility.EXTREME, 15.0, 0, Material.BONE));

        // === ANLEIHEN ===
        registerAsset(new Asset("govt_bond", "Staatsanleihe", "GOV", AssetType.BOND,
                Volatility.LOW, 1000.0, 5.0, Material.PAPER));
        registerAsset(new Asset("corp_bond", "Unternehmensanleihe", "CRP", AssetType.BOND,
                Volatility.LOW, 500.0, 7.5, Material.MAP));

        // === ROHSTOFFE ===
        registerAsset(new Asset("iron_commodity", "Eisen-Futures", "FE", AssetType.COMMODITY,
                Volatility.MEDIUM, 75.0, 0, Material.IRON_INGOT));
        registerAsset(new Asset("gold_commodity", "Gold-Futures", "AU", AssetType.COMMODITY,
                Volatility.MEDIUM, 350.0, 0, Material.GOLD_INGOT));
        registerAsset(new Asset("netherite_commodity", "Netherit-Futures", "NTR", AssetType.COMMODITY,
                Volatility.HIGH, 2000.0, 0, Material.NETHERITE_INGOT));

        // === ETFs ===
        registerAsset(new Asset("market_etf", "Zynaida Total Market", "ZTM", AssetType.ETF,
                Volatility.LOW, 200.0, 3.0, Material.BOOK));
        registerAsset(new Asset("tech_etf", "Redstone Tech ETF", "RTE", AssetType.ETF,
                Volatility.MEDIUM, 150.0, 1.5, Material.BOOKSHELF));

        plugin.getLogger().info("§a" + assets.size() + " Assets registriert!");
    }

    private void registerAsset(Asset asset) {
        assets.put(asset.getId(), asset);
    }

    // ==================== KAUF / VERKAUF ====================

    public enum TradeResult {
        SUCCESS("§aErfolgreich!"),
        NOT_UNLOCKED("§cDu musst erst ein Portfolio kaufen! §e/invest"),
        INSUFFICIENT_FUNDS("§cNicht genug Geld!"),
        INVALID_AMOUNT("§cUngültiger Betrag!"),
        MIN_AMOUNT("§cMindestinvestition nicht erreicht!"),
        MAX_AMOUNT("§cMaximale Investition überschritten!"),
        MAX_POSITIONS("§cMaximale Anzahl Positionen für dein Tier erreicht!"),
        COOLDOWN("§cBitte warte kurz zwischen den Trades!"),
        ASSET_NOT_FOUND("§cAsset nicht gefunden!"),
        NO_SHARES("§cDu besitzt keine Anteile davon!"),
        ECONOMY_ERROR("§cFehler bei der Geldtransaktion!"),
        NOT_ENOUGH_SHARES("§cNicht genug Anteile zum Verkaufen!");

        private final String message;

        TradeResult(String message) {
            this.message = message;
        }

        public String getMessage() { return message; }
    }

    // ==================== PORTFOLIO FREISCHALTEN ====================

    public enum UnlockResult {
        SUCCESS("§aPortfolio freigeschaltet!"),
        ALREADY_UNLOCKED("§cDu hast bereits ein Portfolio dieses Tiers oder höher!"),
        INSUFFICIENT_MONEY("§cNicht genug Geld!"),
        INSUFFICIENT_SHARDS("§cNicht genug Shards!"),
        SHARDS_DISABLED("§cShards-System nicht verfügbar!"),
        ECONOMY_ERROR("§cFehler bei der Transaktion!");

        private final String message;
        UnlockResult(String message) { this.message = message; }
        public String getMessage() { return message; }
    }

    /**
     * Portfolio-Tier kaufen (benötigt Geld UND Shards)
     */
    public UnlockResult purchasePortfolio(UUID playerUUID, int tier) {
        if (tier < 1 || tier > 3) return UnlockResult.ECONOMY_ERROR;

        Portfolio portfolio = getOrCreatePortfolio(playerUUID);

        // Bereits gleiches oder höheres Tier?
        if (portfolio.getPortfolioTier() >= tier) return UnlockResult.ALREADY_UNLOCKED;

        // Kosten aus Config
        double moneyCost = plugin.getConfig().getDouble("portfolio.tiers.tier" + tier + ".money-cost", 5000);
        long shardsCost = plugin.getConfig().getLong("portfolio.tiers.tier" + tier + ".shards-cost", 500);

        // Shards prüfen
        if (!plugin.getShardsHook().isEnabled()) return UnlockResult.SHARDS_DISABLED;
        if (!plugin.getShardsHook().hasShards(playerUUID, shardsCost)) return UnlockResult.INSUFFICIENT_SHARDS;

        // Geld prüfen
        if (!plugin.getEcoMoneyHook().has(playerUUID, moneyCost)) return UnlockResult.INSUFFICIENT_MONEY;

        // Erst Shards abziehen
        if (!plugin.getShardsHook().withdrawShards(playerUUID, shardsCost)) return UnlockResult.ECONOMY_ERROR;

        // Dann Geld abziehen
        if (!plugin.getEcoMoneyHook().withdraw(playerUUID, moneyCost, "Portfolio Tier " + tier + " Kauf")) {
            // Rollback Shards
            plugin.getShardsHook().depositShards(playerUUID, shardsCost);
            return UnlockResult.ECONOMY_ERROR;
        }

        // Freischalten
        portfolio.setUnlocked(true);
        portfolio.setPortfolioTier(tier);
        portfolio.setUnlockTime(System.currentTimeMillis());
        savePortfolio(playerUUID);

        return UnlockResult.SUCCESS;
    }

    /**
     * Prüft ob Spieler ein freigeschaltetes Portfolio hat
     */
    public boolean hasUnlockedPortfolio(UUID playerUUID) {
        Portfolio portfolio = getOrCreatePortfolio(playerUUID);
        return portfolio.isUnlocked() && portfolio.getPortfolioTier() > 0;
    }

    // ==================== KAUF / VERKAUF (NUR MIT GELD) ====================

    /**
     * Kauft Anteile mit GELD (EcoMoney) - Portfolio muss freigeschaltet sein!
     */
    public TradeResult buy(UUID playerUUID, String assetId, double amount) {
        // Portfolio muss freigeschaltet sein!
        if (!hasUnlockedPortfolio(playerUUID)) return TradeResult.NOT_UNLOCKED;

        // Validierungen
        if (amount <= 0) return TradeResult.INVALID_AMOUNT;
        if (amount < minInvestment) return TradeResult.MIN_AMOUNT;
        if (amount > maxInvestment) return TradeResult.MAX_AMOUNT;

        Asset asset = assets.get(assetId);
        if (asset == null) return TradeResult.ASSET_NOT_FOUND;

        // Cooldown
        Long lastTrade = tradeCooldowns.get(playerUUID);
        if (lastTrade != null && System.currentTimeMillis() - lastTrade < TRADE_COOLDOWN_MS) {
            return TradeResult.COOLDOWN;
        }

        // Gebühren berechnen
        double fee = amount * (transactionFeePercent / 100.0);
        double totalCost = amount + fee;

        // Geld prüfen
        if (!plugin.getEcoMoneyHook().has(playerUUID, totalCost)) {
            return TradeResult.INSUFFICIENT_FUNDS;
        }

        Portfolio portfolio = getOrCreatePortfolio(playerUUID);

        // Max Positionen basierend auf Portfolio-Tier
        if (!portfolio.hasInvestment(assetId) && portfolio.getPositionCount() >= portfolio.getMaxPositions()) {
            return TradeResult.MAX_POSITIONS;
        }

        // Anteile berechnen
        double shares = amount / asset.getCurrentPrice();

        // Geld abziehen
        if (!plugin.getEcoMoneyHook().withdraw(playerUUID, totalCost, "Investment: " + asset.getTicker() + " Kauf")) {
            return TradeResult.ECONOMY_ERROR;
        }

        // Investment erstellen oder erweitern
        Investment existing = portfolio.getInvestment(assetId);
        if (existing != null && !existing.isEmpty()) {
            existing.addShares(shares, asset.getCurrentPrice());
        } else {
            Investment inv = new Investment(playerUUID, assetId, shares, asset.getCurrentPrice());
            portfolio.addInvestment(inv);
        }

        portfolio.incrementTradeCount();
        tradeCooldowns.put(playerUUID, System.currentTimeMillis());

        // Speichern
        savePortfolio(playerUUID);

        return TradeResult.SUCCESS;
    }

    /**
     * Verkauft Anteile eines Assets
     */
    public TradeResult sell(UUID playerUUID, String assetId, double sharePercent) {
        // Portfolio muss freigeschaltet sein
        if (!hasUnlockedPortfolio(playerUUID)) return TradeResult.NOT_UNLOCKED;

        Asset asset = assets.get(assetId);
        if (asset == null) return TradeResult.ASSET_NOT_FOUND;

        Portfolio portfolio = getOrCreatePortfolio(playerUUID);
        Investment inv = portfolio.getInvestment(assetId);

        if (inv == null || inv.isEmpty()) return TradeResult.NO_SHARES;

        // Cooldown
        Long lastTrade = tradeCooldowns.get(playerUUID);
        if (lastTrade != null && System.currentTimeMillis() - lastTrade < TRADE_COOLDOWN_MS) {
            return TradeResult.COOLDOWN;
        }

        sharePercent = Math.max(0.01, Math.min(1.0, sharePercent));
        double sharesToSell = inv.getShares() * sharePercent;

        // Erlös berechnen
        double revenue = sharesToSell * asset.getCurrentPrice();
        double fee = revenue * (transactionFeePercent / 100.0);
        double netRevenue = revenue - fee;

        // Gewinn/Verlust
        double costBasis = (sharesToSell / inv.getShares()) * inv.getTotalInvested();
        double profitLoss = netRevenue - costBasis;

        // ERST Geld gutschreiben, DANN Investment ändern (kein Rollback nötig)
        if (netRevenue > 0) {
            plugin.getLogger().info("Sell: " + playerUUID + " verkauft " + sharesToSell + " Anteile von " +
                    asset.getTicker() + " für " + netRevenue + " (Gebühr: " + fee + ")");
            if (!plugin.getEcoMoneyHook().deposit(playerUUID, netRevenue, "Investment: " + asset.getTicker() + " Verkauf")) {
                plugin.getLogger().warning("Sell FEHLGESCHLAGEN: deposit returned false für " + playerUUID);
                return TradeResult.ECONOMY_ERROR;
            }
        }

        // Erst nach erfolgreichem Deposit die Anteile reduzieren
        inv.sellShares(sharesToSell, asset.getCurrentPrice());

        portfolio.addRealizedProfit(profitLoss);
        portfolio.incrementTradeCount();

        // Position entfernen wenn leer
        if (inv.isEmpty()) {
            portfolio.removeInvestment(assetId);
        }

        tradeCooldowns.put(playerUUID, System.currentTimeMillis());
        savePortfolio(playerUUID);

        return TradeResult.SUCCESS;
    }

    /**
     * Verkauft einen bestimmten Betrag in Geld
     */
    public TradeResult sellByAmount(UUID playerUUID, String assetId, double amount) {
        Asset asset = assets.get(assetId);
        if (asset == null) return TradeResult.ASSET_NOT_FOUND;

        Portfolio portfolio = getOrCreatePortfolio(playerUUID);
        Investment inv = portfolio.getInvestment(assetId);
        if (inv == null || inv.isEmpty()) return TradeResult.NO_SHARES;

        double currentValue = inv.getCurrentValue(asset.getCurrentPrice());
        if (amount > currentValue) return TradeResult.NOT_ENOUGH_SHARES;

        double percent = amount / currentValue;
        return sell(playerUUID, assetId, percent);
    }

    // ==================== DIVIDENDEN ====================

    public void payDividends() {
        // Cross-Server: Nur Master zahlt Dividenden
        if (isSlave()) return;

        Map<String, Asset> dividendAssets = new LinkedHashMap<>();
        for (Asset asset : assets.values()) {
            if (asset.getDividendYield() > 0) {
                dividendAssets.put(asset.getId(), asset);
            }
        }

        if (dividendAssets.isEmpty()) return;

        String prefix = plugin.getConfig().getString("messages.prefix", "§8[§6§lInvest§8] ");

        for (Portfolio portfolio : portfolios.values()) {
            double totalDividend = 0;

            for (Investment inv : portfolio.getInvestments()) {
                Asset asset = dividendAssets.get(inv.getAssetId());
                if (asset == null || inv.isEmpty()) continue;

                double value = inv.getCurrentValue(asset.getCurrentPrice());
                double dividend = value * (asset.getDividendYield() / 100.0);

                if (dividend <= 0) continue;

                if (inv.isAutoReinvest()) {
                    // Automatisch reinvestieren
                    double newShares = dividend / asset.getCurrentPrice();
                    inv.addShares(newShares, asset.getCurrentPrice());
                } else {
                    // Auszahlen
                    plugin.getEcoMoneyHook().deposit(portfolio.getPlayerUUID(), dividend,
                            "Dividende: " + asset.getTicker());
                }

                inv.addDividendsEarned(dividend);
                totalDividend += dividend;
            }

            if (totalDividend > 0) {
                portfolio.addDividendsReceived(totalDividend);

                Player player = Bukkit.getPlayer(portfolio.getPlayerUUID());
                if (player != null && player.isOnline()) {
                    player.sendMessage(prefix + "§a💰 Dividenden erhalten: §6" +
                            plugin.getEcoMoneyHook().formatMoney(totalDividend));
                }
            }
        }

        saveAll();
    }

    // ==================== STOP-LOSS / TAKE-PROFIT ====================

    public void checkAutoTriggers() {
        String prefix = plugin.getConfig().getString("messages.prefix", "§8[§6§lInvest§8] ");

        for (Portfolio portfolio : portfolios.values()) {
            List<String> toAutoSell = new ArrayList<>();

            for (Investment inv : portfolio.getInvestments()) {
                if (inv.isEmpty()) continue;
                Asset asset = assets.get(inv.getAssetId());
                if (asset == null) continue;

                if (inv.shouldTriggerStopLoss(asset.getCurrentPrice())) {
                    toAutoSell.add(inv.getAssetId());
                    Player player = Bukkit.getPlayer(portfolio.getPlayerUUID());
                    if (player != null) {
                        player.sendMessage(prefix + "§c⚠ Stop-Loss ausgelöst für §f" +
                                asset.getName() + "§c! Position wird verkauft.");
                    }
                } else if (inv.shouldTriggerTakeProfit(asset.getCurrentPrice())) {
                    toAutoSell.add(inv.getAssetId());
                    Player player = Bukkit.getPlayer(portfolio.getPlayerUUID());
                    if (player != null) {
                        player.sendMessage(prefix + "§a✓ Take-Profit ausgelöst für §f" +
                                asset.getName() + "§a! Gewinne werden gesichert.");
                    }
                }
            }

            for (String assetId : toAutoSell) {
                sell(portfolio.getPlayerUUID(), assetId, 1.0); // Komplett verkaufen
            }
        }
    }

    // ==================== PORTFOLIO ====================

    public Portfolio getOrCreatePortfolio(UUID uuid) {
        if (isCrossServerEnabled() && !portfolios.containsKey(uuid)) {
            Portfolio loaded = plugin.getDataManager().loadPortfolio(uuid, assets);
            if (loaded != null) {
                portfolios.put(uuid, loaded);
                return loaded;
            }
        }
        return portfolios.computeIfAbsent(uuid, Portfolio::new);
    }

    /**
     * Lädt ein Portfolio frisch aus der DB (Cross-Server Sync)
     */
    public void reloadPortfolioFromDB(UUID uuid) {
        if (!isCrossServerEnabled()) return;
        Portfolio fresh = plugin.getDataManager().reloadPortfolio(uuid, assets);
        if (fresh != null) {
            portfolios.put(uuid, fresh);
        }
    }

    public Portfolio getPortfolio(UUID uuid) {
        return portfolios.get(uuid);
    }

    // ==================== LEADERBOARD ====================

    public List<Map.Entry<UUID, Double>> getTopInvestors(int limit) {
        if (isCrossServerEnabled()) {
            return plugin.getDataManager().getTopInvestorsFromDB(limit, assets);
        }

        Map<UUID, Double> values = new HashMap<>();
        for (Portfolio portfolio : portfolios.values()) {
            double value = portfolio.getTotalValue(assets) + portfolio.getTotalRealizedProfit();
            values.put(portfolio.getPlayerUUID(), value);
        }

        List<Map.Entry<UUID, Double>> sorted = new ArrayList<>(values.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        return sorted.subList(0, Math.min(limit, sorted.size()));
    }

    // ==================== PERSISTENZ ====================

    private void loadAllPortfolios() {
        if (isSlave()) {
            // Slave: Nur Preise laden, Portfolios on-demand
            plugin.getDataManager().loadAssetPrices(assets);
            plugin.getLogger().info("§eSlave-Modus: Portfolios werden on-demand geladen.");
            return;
        }

        Map<UUID, Portfolio> loaded = plugin.getDataManager().loadAllPortfolios(assets);
        if (loaded != null) {
            portfolios.putAll(loaded);
            plugin.getLogger().info("§a" + portfolios.size() + " Portfolios geladen.");
        }
    }

    public void savePortfolio(UUID uuid) {
        Portfolio portfolio = portfolios.get(uuid);
        if (portfolio != null) {
            plugin.getDataManager().savePortfolio(portfolio);
        }
    }

    public void saveAll() {
        for (Portfolio portfolio : portfolios.values()) {
            plugin.getDataManager().savePortfolio(portfolio);
        }
        // Asset-Preise speichern
        if (isCrossServerEnabled()) {
            plugin.getDataManager().saveAssetPricesFull(assets);
        } else {
            plugin.getDataManager().saveAssetPrices(assets);
        }
    }

    // ==================== GETTERS ====================

    // ==================== CROSS-SERVER HELPER ====================

    private boolean isCrossServerEnabled() {
        return plugin.getConfig().getBoolean("cross-server.enabled", false);
    }

    private boolean isSlave() {
        return isCrossServerEnabled() &&
            "slave".equalsIgnoreCase(plugin.getConfig().getString("cross-server.role", "master"));
    }

    public Map<String, Asset> getAssets() { return assets; }
    public Asset getAsset(String id) { return assets.get(id); }
    public double getTransactionFeePercent() { return transactionFeePercent; }
    public double getMinInvestment() { return minInvestment; }
    public double getMaxInvestment() { return maxInvestment; }

    public List<Asset> getAssetsByType(AssetType type) {
        return assets.values().stream()
                .filter(a -> a.getType() == type)
                .toList();
    }
}
