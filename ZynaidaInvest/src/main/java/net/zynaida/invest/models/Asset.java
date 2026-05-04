package net.zynaida.invest.models;

import org.bukkit.Material;

import java.util.*;

/**
 * Represents an investable asset (stock/crypto/bond/commodity)
 */
public class Asset {

    public enum AssetType {
        STOCK("§a📈 Aktie", Material.EMERALD, "§a"),
        CRYPTO("§6₿ Krypto", Material.GOLD_INGOT, "§6"),
        BOND("§b🏦 Anleihe", Material.PAPER, "§b"),
        COMMODITY("§e⛏ Rohstoff", Material.DIAMOND, "§e"),
        ETF("§d📊 ETF", Material.BOOK, "§d");

        private final String displayName;
        private final Material icon;
        private final String color;

        AssetType(String displayName, Material icon, String color) {
            this.displayName = displayName;
            this.icon = icon;
            this.color = color;
        }

        public String getDisplayName() { return displayName; }
        public Material getIcon() { return icon; }
        public String getColor() { return color; }
    }

    public enum Volatility {
        LOW(0.5, "§a▂ Niedrig"),
        MEDIUM(1.0, "§e▄ Mittel"),
        HIGH(2.0, "§c▆ Hoch"),
        EXTREME(4.0, "§4█ Extrem");

        private final double multiplier;
        private final String display;

        Volatility(double multiplier, String display) {
            this.multiplier = multiplier;
            this.display = display;
        }

        public double getMultiplier() { return multiplier; }
        public String getDisplay() { return display; }
    }

    private final String id;
    private final String name;
    private final String ticker;
    private final AssetType type;
    private final Volatility volatility;
    private final Material guiIcon;

    // Preis-Daten (volatile für Cross-Server Thread-Safety)
    private volatile double currentPrice;
    private volatile double previousPrice;
    private volatile double dayOpenPrice;
    private volatile double allTimeHigh;
    private volatile double allTimeLow;
    private double basePrice;

    // Preis-Historie (letzte 48 Ticks = 24h simuliert)
    private final LinkedList<Double> priceHistory = new LinkedList<>();
    private static final int MAX_HISTORY = 48;

    // Dividende (nur für Stocks/Bonds/ETFs)
    private double dividendYield; // % pro Zyklus
    private long lastDividendTime;

    // Trend-System
    private double trend = 0; // -1.0 bis 1.0
    private int trendDuration = 0;
    private boolean isCrashing = false;
    private boolean isBoosting = false;

    // Nachrichten/Events
    private String currentNews = null;
    private long newsExpiry = 0;

    public Asset(String id, String name, String ticker, AssetType type,
                 Volatility volatility, double basePrice, double dividendYield, Material guiIcon) {
        this.id = id;
        this.name = name;
        this.ticker = ticker;
        this.type = type;
        this.volatility = volatility;
        this.basePrice = basePrice;
        this.currentPrice = basePrice;
        this.previousPrice = basePrice;
        this.dayOpenPrice = basePrice;
        this.allTimeHigh = basePrice;
        this.allTimeLow = basePrice;
        this.dividendYield = dividendYield;
        this.guiIcon = guiIcon;
        this.lastDividendTime = System.currentTimeMillis();

        // Init history
        for (int i = 0; i < MAX_HISTORY; i++) {
            priceHistory.add(basePrice);
        }
    }

    // ==================== PREIS UPDATE ====================

    public void updatePrice(double newPrice) {
        previousPrice = currentPrice;
        currentPrice = Math.max(0.01, newPrice); // Nie unter 0.01

        if (currentPrice > allTimeHigh) allTimeHigh = currentPrice;
        if (currentPrice < allTimeLow) allTimeLow = currentPrice;

        priceHistory.addLast(currentPrice);
        if (priceHistory.size() > MAX_HISTORY) {
            priceHistory.removeFirst();
        }
    }

    // ==================== BERECHNUNGEN ====================

    public double getChangePercent() {
        if (previousPrice == 0) return 0;
        return ((currentPrice - previousPrice) / previousPrice) * 100;
    }

    public double getDayChangePercent() {
        if (dayOpenPrice == 0) return 0;
        return ((currentPrice - dayOpenPrice) / dayOpenPrice) * 100;
    }

    public String getChangeArrow() {
        double change = getChangePercent();
        if (change > 2.0) return "§a▲▲";
        if (change > 0.5) return "§a▲";
        if (change > -0.5) return "§7►";
        if (change > -2.0) return "§c▼";
        return "§c▼▼";
    }

    public String getFormattedPrice() {
        return String.format("%.2f$", currentPrice);
    }

    public String getFormattedChange() {
        double change = getChangePercent();
        String color = change >= 0 ? "§a+" : "§c";
        return color + String.format("%.2f%%", change);
    }

    /**
     * Mini-Sparkline für die GUI
     */
    public String getSparkline() {
        if (priceHistory.size() < 8) return "§7--------";

        List<Double> recent = new ArrayList<>(priceHistory.subList(
                Math.max(0, priceHistory.size() - 8), priceHistory.size()));

        double min = recent.stream().mapToDouble(d -> d).min().orElse(0);
        double max = recent.stream().mapToDouble(d -> d).max().orElse(1);
        double range = max - min;
        if (range == 0) range = 1;

        String[] blocks = {"▁", "▂", "▃", "▄", "▅", "▆", "▇", "█"};
        StringBuilder spark = new StringBuilder();

        for (double price : recent) {
            int level = (int) ((price - min) / range * 7);
            level = Math.max(0, Math.min(7, level));
            // Farbe basierend auf Position
            double change = price - recent.get(0);
            spark.append(change >= 0 ? "§a" : "§c");
            spark.append(blocks[level]);
        }

        return spark.toString();
    }

    // ==================== NEWS SYSTEM ====================

    public void setNews(String news, long durationMs) {
        this.currentNews = news;
        this.newsExpiry = System.currentTimeMillis() + durationMs;
    }

    public String getCurrentNews() {
        if (currentNews != null && System.currentTimeMillis() > newsExpiry) {
            currentNews = null;
        }
        return currentNews;
    }

    public boolean hasActiveNews() {
        return getCurrentNews() != null;
    }

    // ==================== TREND ====================

    public void setTrend(double trend, int duration) {
        this.trend = Math.max(-1.0, Math.min(1.0, trend));
        this.trendDuration = duration;
    }

    public void tickTrend() {
        if (trendDuration > 0) {
            trendDuration--;
            if (trendDuration == 0) {
                trend = 0;
                isCrashing = false;
                isBoosting = false;
            }
        }
    }

    public void resetDayOpen() {
        this.dayOpenPrice = currentPrice;
    }

    // ==================== GETTERS/SETTERS ====================

    public String getId() { return id; }
    public String getName() { return name; }
    public String getTicker() { return ticker; }
    public AssetType getType() { return type; }
    public Volatility getVolatility() { return volatility; }
    public Material getGuiIcon() { return guiIcon; }
    public double getCurrentPrice() { return currentPrice; }
    public double getPreviousPrice() { return previousPrice; }
    public double getDayOpenPrice() { return dayOpenPrice; }
    public double getAllTimeHigh() { return allTimeHigh; }
    public double getAllTimeLow() { return allTimeLow; }
    public double getBasePrice() { return basePrice; }
    public LinkedList<Double> getPriceHistory() { return priceHistory; }
    public double getDividendYield() { return dividendYield; }
    public long getLastDividendTime() { return lastDividendTime; }
    public void setLastDividendTime(long time) { this.lastDividendTime = time; }
    public double getTrend() { return trend; }
    public int getTrendDuration() { return trendDuration; }
    public boolean isCrashing() { return isCrashing; }
    public void setCrashing(boolean crashing) { this.isCrashing = crashing; }
    public boolean isBoosting() { return isBoosting; }
    public void setBoosting(boolean boosting) { this.isBoosting = boosting; }
    public void setDividendYield(double yield) { this.dividendYield = yield; }
    public long getNewsExpiry() { return newsExpiry; }
    public void setBasePrice(double basePrice) { this.basePrice = basePrice; }

    // ==================== CROSS-SERVER SYNC SETTERS ====================

    public void setPreviousPrice(double previousPrice) { this.previousPrice = previousPrice; }
    public void setDayOpenPrice(double dayOpenPrice) { this.dayOpenPrice = dayOpenPrice; }
    public void setAllTimeHigh(double ath) { this.allTimeHigh = ath; }
    public void setAllTimeLow(double atl) { this.allTimeLow = atl; }

    public void setTrendDirect(double trend, int duration) {
        this.trend = Math.max(-1.0, Math.min(1.0, trend));
        this.trendDuration = duration;
    }

    public void setCurrentNewsDirect(String news, long expiry) {
        this.currentNews = news;
        this.newsExpiry = expiry;
    }
}
