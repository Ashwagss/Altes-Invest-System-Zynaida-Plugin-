package net.zynaida.invest.models;

import java.util.UUID;

/**
 * Einzelne Position im Portfolio eines Spielers
 */
public class Investment {

    private final UUID id;
    private final UUID playerUUID;
    private final String assetId;
    private double shares; // Anzahl Anteile
    private double buyPrice; // Durchschnittlicher Kaufpreis
    private double totalInvested; // Gesamtbetrag investiert
    private long buyTime; // Zeitpunkt des ersten Kaufs
    private long lastDividendClaim; // Letzter Dividenden-Claim
    private double totalDividendsEarned; // Gesamte erhaltene Dividenden
    private boolean autoReinvest; // Dividenden automatisch reinvestieren?

    // Stop-Loss / Take-Profit
    private double stopLossPercent = -1; // -1 = deaktiviert
    private double takeProfitPercent = -1; // -1 = deaktiviert

    public Investment(UUID playerUUID, String assetId, double shares, double buyPrice) {
        this.id = UUID.randomUUID();
        this.playerUUID = playerUUID;
        this.assetId = assetId;
        this.shares = shares;
        this.buyPrice = buyPrice;
        this.totalInvested = shares * buyPrice;
        this.buyTime = System.currentTimeMillis();
        this.lastDividendClaim = System.currentTimeMillis();
        this.totalDividendsEarned = 0;
        this.autoReinvest = false;
    }

    // Für Laden aus DB
    public Investment(UUID id, UUID playerUUID, String assetId, double shares,
                      double buyPrice, double totalInvested, long buyTime,
                      long lastDividendClaim, double totalDividendsEarned,
                      boolean autoReinvest, double stopLoss, double takeProfit) {
        this.id = id;
        this.playerUUID = playerUUID;
        this.assetId = assetId;
        this.shares = shares;
        this.buyPrice = buyPrice;
        this.totalInvested = totalInvested;
        this.buyTime = buyTime;
        this.lastDividendClaim = lastDividendClaim;
        this.totalDividendsEarned = totalDividendsEarned;
        this.autoReinvest = autoReinvest;
        this.stopLossPercent = stopLoss;
        this.takeProfitPercent = takeProfit;
    }

    // ==================== BERECHNUNGEN ====================

    /**
     * Aktueller Wert der Position
     */
    public double getCurrentValue(double currentAssetPrice) {
        return shares * currentAssetPrice;
    }

    /**
     * Gewinn/Verlust in absoluten Zahlen
     */
    public double getProfitLoss(double currentAssetPrice) {
        return getCurrentValue(currentAssetPrice) - totalInvested;
    }

    /**
     * Gewinn/Verlust in Prozent
     */
    public double getProfitLossPercent(double currentAssetPrice) {
        if (totalInvested == 0) return 0;
        return (getProfitLoss(currentAssetPrice) / totalInvested) * 100;
    }

    /**
     * Nachkaufen (Dollar-Cost-Averaging)
     */
    public void addShares(double newShares, double pricePerShare) {
        double newTotal = totalInvested + (newShares * pricePerShare);
        double totalShares = shares + newShares;
        this.buyPrice = newTotal / totalShares; // Neuer Durchschnittspreis
        this.shares = totalShares;
        this.totalInvested = newTotal;
    }

    /**
     * Teilverkauf
     */
    public double sellShares(double sellShares, double pricePerShare) {
        if (sellShares > shares) sellShares = shares;
        double revenue = sellShares * pricePerShare;
        double investedPortion = (sellShares / shares) * totalInvested;
        this.shares -= sellShares;
        this.totalInvested -= investedPortion;
        return revenue;
    }

    /**
     * Prüft ob Stop-Loss/Take-Profit ausgelöst werden soll
     */
    public boolean shouldTriggerStopLoss(double currentPrice) {
        if (stopLossPercent < 0) return false;
        return getProfitLossPercent(currentPrice) <= -stopLossPercent;
    }

    public boolean shouldTriggerTakeProfit(double currentPrice) {
        if (takeProfitPercent < 0) return false;
        return getProfitLossPercent(currentPrice) >= takeProfitPercent;
    }

    /**
     * Ist die Position komplett verkauft?
     */
    public boolean isEmpty() {
        return shares <= 0.0001;
    }

    // ==================== GETTERS/SETTERS ====================

    public UUID getId() { return id; }
    public UUID getPlayerUUID() { return playerUUID; }
    public String getAssetId() { return assetId; }
    public double getShares() { return shares; }
    public double getBuyPrice() { return buyPrice; }
    public double getTotalInvested() { return totalInvested; }
    public long getBuyTime() { return buyTime; }
    public long getLastDividendClaim() { return lastDividendClaim; }
    public void setLastDividendClaim(long time) { this.lastDividendClaim = time; }
    public double getTotalDividendsEarned() { return totalDividendsEarned; }
    public void addDividendsEarned(double amount) { this.totalDividendsEarned += amount; }
    public boolean isAutoReinvest() { return autoReinvest; }
    public void setAutoReinvest(boolean autoReinvest) { this.autoReinvest = autoReinvest; }
    public double getStopLossPercent() { return stopLossPercent; }
    public void setStopLossPercent(double percent) { this.stopLossPercent = percent; }
    public double getTakeProfitPercent() { return takeProfitPercent; }
    public void setTakeProfitPercent(double percent) { this.takeProfitPercent = percent; }
}
