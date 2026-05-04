package net.zynaida.invest.data;

import net.zynaida.invest.models.Asset;
import net.zynaida.invest.models.Portfolio;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface DataManager {
    void initialize();
    void close();
    void savePortfolio(Portfolio portfolio);
    Portfolio loadPortfolio(UUID uuid, Map<String, Asset> assets);
    Map<UUID, Portfolio> loadAllPortfolios(Map<String, Asset> assets);
    void deletePortfolio(UUID uuid);
    void saveAssetPrices(Map<String, Asset> assets);
    void loadAssetPrices(Map<String, Asset> assets);

    // Cross-Server Sync
    default void saveMarketState(double sentiment, long lastDividendTime, String serverId) {}
    default MarketStateData loadMarketState() { return null; }
    default void saveAssetPricesFull(Map<String, Asset> assets) { saveAssetPrices(assets); }
    default void loadAssetPricesFull(Map<String, Asset> assets) { loadAssetPrices(assets); }
    default Portfolio reloadPortfolio(UUID uuid, Map<String, Asset> assets) { return loadPortfolio(uuid, assets); }
    default List<Map.Entry<UUID, Double>> getTopInvestorsFromDB(int limit, Map<String, Asset> assets) { return List.of(); }
}
