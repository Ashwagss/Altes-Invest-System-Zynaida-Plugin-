package net.zynaida.invest.data;

import net.zynaida.invest.ZynaidaInvest;
import net.zynaida.invest.models.Asset;
import net.zynaida.invest.models.Investment;
import net.zynaida.invest.models.Portfolio;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class YamlManager implements DataManager {

    private final ZynaidaInvest plugin;
    private File portfolioFile;
    private File pricesFile;
    private YamlConfiguration portfolioConfig;
    private YamlConfiguration pricesConfig;

    public YamlManager(ZynaidaInvest plugin) {
        this.plugin = plugin;
    }

    @Override
    public void initialize() {
        portfolioFile = new File(plugin.getDataFolder(), "portfolios.yml");
        pricesFile = new File(plugin.getDataFolder(), "prices.yml");

        if (!portfolioFile.exists()) {
            try { portfolioFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        if (!pricesFile.exists()) {
            try { pricesFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }

        portfolioConfig = YamlConfiguration.loadConfiguration(portfolioFile);
        pricesConfig = YamlConfiguration.loadConfiguration(pricesFile);

        plugin.getLogger().info("§aYAML Speicher initialisiert.");
    }

    @Override
    public void close() {
        save();
    }

    private void save() {
        try {
            portfolioConfig.save(portfolioFile);
            pricesConfig.save(pricesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Fehler beim Speichern: " + e.getMessage());
        }
    }

    @Override
    public void savePortfolio(Portfolio portfolio) {
        String uuid = portfolio.getPlayerUUID().toString();
        String path = "portfolios." + uuid;

        portfolioConfig.set(path + ".realized-profit", portfolio.getTotalRealizedProfit());
        portfolioConfig.set(path + ".dividends-received", portfolio.getTotalDividendsReceived());
        portfolioConfig.set(path + ".trade-count", portfolio.getTotalTradeCount());
        portfolioConfig.set(path + ".unlocked", portfolio.isUnlocked());
        portfolioConfig.set(path + ".unlock-time", portfolio.getUnlockTime());
        portfolioConfig.set(path + ".tier", portfolio.getPortfolioTier());

        // Investments
        portfolioConfig.set(path + ".investments", null); // Clear
        for (Investment inv : portfolio.getInvestments()) {
            if (inv.isEmpty()) continue;
            String invPath = path + ".investments." + inv.getAssetId();
            portfolioConfig.set(invPath + ".id", inv.getId().toString());
            portfolioConfig.set(invPath + ".shares", inv.getShares());
            portfolioConfig.set(invPath + ".buy-price", inv.getBuyPrice());
            portfolioConfig.set(invPath + ".total-invested", inv.getTotalInvested());
            portfolioConfig.set(invPath + ".buy-time", inv.getBuyTime());
            portfolioConfig.set(invPath + ".last-dividend", inv.getLastDividendClaim());
            portfolioConfig.set(invPath + ".dividends-earned", inv.getTotalDividendsEarned());
            portfolioConfig.set(invPath + ".auto-reinvest", inv.isAutoReinvest());
            portfolioConfig.set(invPath + ".stop-loss", inv.getStopLossPercent());
            portfolioConfig.set(invPath + ".take-profit", inv.getTakeProfitPercent());
        }

        try {
            portfolioConfig.save(portfolioFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Portfolio speichern fehlgeschlagen: " + e.getMessage());
        }
    }

    @Override
    public Portfolio loadPortfolio(UUID uuid, Map<String, Asset> assets) {
        String path = "portfolios." + uuid;
        if (!portfolioConfig.contains(path)) return null;

        Portfolio portfolio = new Portfolio(uuid);
        portfolio.setUnlocked(portfolioConfig.getBoolean(path + ".unlocked", false));
        portfolio.setUnlockTime(portfolioConfig.getLong(path + ".unlock-time", 0));
        portfolio.setPortfolioTier(portfolioConfig.getInt(path + ".tier", 0));

        ConfigurationSection invSection = portfolioConfig.getConfigurationSection(path + ".investments");
        if (invSection != null) {
            for (String assetId : invSection.getKeys(false)) {
                if (!assets.containsKey(assetId)) continue;

                String invPath = path + ".investments." + assetId;
                UUID invId = UUID.fromString(portfolioConfig.getString(invPath + ".id", UUID.randomUUID().toString()));
                double shares = portfolioConfig.getDouble(invPath + ".shares");
                double buyPrice = portfolioConfig.getDouble(invPath + ".buy-price");
                double totalInvested = portfolioConfig.getDouble(invPath + ".total-invested");
                long buyTime = portfolioConfig.getLong(invPath + ".buy-time");
                long lastDividend = portfolioConfig.getLong(invPath + ".last-dividend");
                double dividendsEarned = portfolioConfig.getDouble(invPath + ".dividends-earned");
                boolean autoReinvest = portfolioConfig.getBoolean(invPath + ".auto-reinvest");
                double stopLoss = portfolioConfig.getDouble(invPath + ".stop-loss", -1);
                double takeProfit = portfolioConfig.getDouble(invPath + ".take-profit", -1);

                if (shares > 0) {
                    Investment inv = new Investment(invId, uuid, assetId, shares, buyPrice,
                            totalInvested, buyTime, lastDividend, dividendsEarned,
                            autoReinvest, stopLoss, takeProfit);
                    portfolio.addInvestment(inv);
                }
            }
        }

        return portfolio;
    }

    @Override
    public Map<UUID, Portfolio> loadAllPortfolios(Map<String, Asset> assets) {
        Map<UUID, Portfolio> result = new HashMap<>();
        ConfigurationSection section = portfolioConfig.getConfigurationSection("portfolios");
        if (section == null) return result;

        for (String uuidStr : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                Portfolio portfolio = loadPortfolio(uuid, assets);
                if (portfolio != null) {
                    result.put(uuid, portfolio);
                }
            } catch (IllegalArgumentException ignored) {}
        }

        // Lade Asset-Preise
        loadAssetPrices(assets);

        return result;
    }

    @Override
    public void deletePortfolio(UUID uuid) {
        portfolioConfig.set("portfolios." + uuid, null);
        try {
            portfolioConfig.save(portfolioFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Portfolio löschen fehlgeschlagen: " + e.getMessage());
        }
    }

    @Override
    public void saveAssetPrices(Map<String, Asset> assets) {
        for (Asset asset : assets.values()) {
            String path = "prices." + asset.getId();
            pricesConfig.set(path + ".current", asset.getCurrentPrice());
            pricesConfig.set(path + ".ath", asset.getAllTimeHigh());
            pricesConfig.set(path + ".atl", asset.getAllTimeLow());
        }
        try {
            pricesConfig.save(pricesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Preise speichern fehlgeschlagen: " + e.getMessage());
        }
    }

    @Override
    public void loadAssetPrices(Map<String, Asset> assets) {
        ConfigurationSection section = pricesConfig.getConfigurationSection("prices");
        if (section == null) return;

        for (String assetId : section.getKeys(false)) {
            Asset asset = assets.get(assetId);
            if (asset == null) continue;

            double price = pricesConfig.getDouble("prices." + assetId + ".current", asset.getBasePrice());
            asset.updatePrice(price);
        }
    }
}
