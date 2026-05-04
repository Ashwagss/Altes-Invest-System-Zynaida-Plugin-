package net.zynaida.invest.models;

import java.util.*;

/**
 * Portfolio eines Spielers - Enthält alle Investments
 */
public class Portfolio {

    private final UUID playerUUID;
    private final Map<String, Investment> investments = new LinkedHashMap<>();
    private double totalRealizedProfit = 0;
    private double totalDividendsReceived = 0;
    private int totalTradeCount = 0;

    // Portfolio-Freischaltung
    private boolean unlocked = false;
    private long unlockTime = 0;
    private int portfolioTier = 0; // 0=nicht freigeschaltet, 1=Basis, 2=Premium, 3=Elite

    public Portfolio(UUID playerUUID) {
        this.playerUUID = playerUUID;
    }

    /**
     * Gesamtwert des Portfolios
     */
    public double getTotalValue(Map<String, Asset> assets) {
        double total = 0;
        for (Investment inv : investments.values()) {
            Asset asset = assets.get(inv.getAssetId());
            if (asset != null) {
                total += inv.getCurrentValue(asset.getCurrentPrice());
            }
        }
        return total;
    }

    /**
     * Gesamter investierter Betrag
     */
    public double getTotalInvested() {
        return investments.values().stream()
                .mapToDouble(Investment::getTotalInvested)
                .sum();
    }

    /**
     * Gesamter unrealisierter Gewinn/Verlust
     */
    public double getUnrealizedPL(Map<String, Asset> assets) {
        double total = 0;
        for (Investment inv : investments.values()) {
            Asset asset = assets.get(inv.getAssetId());
            if (asset != null) {
                total += inv.getProfitLoss(asset.getCurrentPrice());
            }
        }
        return total;
    }

    /**
     * Portfolio Performance in %
     */
    public double getPerformancePercent(Map<String, Asset> assets) {
        double invested = getTotalInvested();
        if (invested == 0) return 0;
        return (getUnrealizedPL(assets) / invested) * 100;
    }

    public void addInvestment(Investment investment) {
        investments.put(investment.getAssetId(), investment);
    }

    public Investment getInvestment(String assetId) {
        return investments.get(assetId);
    }

    public void removeInvestment(String assetId) {
        investments.remove(assetId);
    }

    public boolean hasInvestment(String assetId) {
        return investments.containsKey(assetId) && !investments.get(assetId).isEmpty();
    }

    public Collection<Investment> getInvestments() {
        return Collections.unmodifiableCollection(investments.values());
    }

    public int getPositionCount() {
        return (int) investments.values().stream().filter(i -> !i.isEmpty()).count();
    }

    // ==================== GETTERS/SETTERS ====================

    public UUID getPlayerUUID() { return playerUUID; }
    public double getTotalRealizedProfit() { return totalRealizedProfit; }
    public void addRealizedProfit(double amount) { this.totalRealizedProfit += amount; }
    public void setTotalRealizedProfit(double amount) { this.totalRealizedProfit = amount; }
    public double getTotalDividendsReceived() { return totalDividendsReceived; }
    public void addDividendsReceived(double amount) { this.totalDividendsReceived += amount; }
    public void setTotalDividendsReceived(double amount) { this.totalDividendsReceived = amount; }
    public int getTotalTradeCount() { return totalTradeCount; }
    public void incrementTradeCount() { this.totalTradeCount++; }
    public void setTotalTradeCount(int count) { this.totalTradeCount = count; }

    // Portfolio-Unlock
    public boolean isUnlocked() { return unlocked; }
    public void setUnlocked(boolean unlocked) { this.unlocked = unlocked; }
    public long getUnlockTime() { return unlockTime; }
    public void setUnlockTime(long time) { this.unlockTime = time; }
    public int getPortfolioTier() { return portfolioTier; }
    public void setPortfolioTier(int tier) { this.portfolioTier = tier; }

    /**
     * Max. Positionen basierend auf Tier
     */
    public int getMaxPositions() {
        return switch (portfolioTier) {
            case 1 -> 5;   // Basis
            case 2 -> 10;  // Premium
            case 3 -> 20;  // Elite
            default -> 0;
        };
    }

    /**
     * Tier-Name
     */
    public String getTierName() {
        return switch (portfolioTier) {
            case 1 -> "§a✦ Basis";
            case 2 -> "§b✦✦ Premium";
            case 3 -> "§6✦✦✦ Elite";
            default -> "§c✘ Nicht freigeschaltet";
        };
    }

    /**
     * Tier-Farbe
     */
    public String getTierColor() {
        return switch (portfolioTier) {
            case 1 -> "§a";
            case 2 -> "§b";
            case 3 -> "§6";
            default -> "§c";
        };
    }
}
