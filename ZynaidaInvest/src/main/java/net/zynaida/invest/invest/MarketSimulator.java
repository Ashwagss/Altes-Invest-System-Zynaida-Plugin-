package net.zynaida.invest.invest;

import net.zynaida.invest.ZynaidaInvest;
import net.zynaida.invest.data.MarketStateData;
import net.zynaida.invest.models.Asset;
import net.zynaida.invest.models.Asset.AssetType;
import net.zynaida.invest.models.Asset.Volatility;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Simuliert realistische Marktbewegungen
 * - Brownian Motion mit Drift
 * - Trend-System (Bull/Bear Märkte)
 * - Zufällige News-Events
 * - Crash & Boom Events
 * - Saisonale Effekte
 * - Mean Reversion
 * - Cross-Server: Master simuliert, Slaves syncen aus DB
 */
public class MarketSimulator {

    private final ZynaidaInvest plugin;
    private final Random random = new Random();
    private BukkitTask priceUpdateTask;
    private BukkitTask newsTask;
    private BukkitTask dividendTask;
    private BukkitTask syncTask;
    private int tickCount = 0;

    // Globaler Markt-Sentiment (-1 = Panik, 0 = Neutral, 1 = Gier)
    private double marketSentiment = 0;

    // Cross-Server
    private boolean crossServerEnabled;
    private boolean isMaster;
    private boolean isSlave;
    private String serverId;
    private long lastDividendTime = 0;

    // News-Pool
    private static final String[][] POSITIVE_NEWS = {
            {"§a📰 Rekord-Quartal!", "§7%s meldet Rekordgewinne"},
            {"§a📰 Neuer Großkunde!", "§7%s gewinnt Millionen-Deal"},
            {"§a📰 Innovation!", "§7%s stellt neues Produkt vor"},
            {"§a📰 Expansion!", "§7%s expandiert in neue Märkte"},
            {"§a📰 Übernahme!", "§7%s wird übernommen - Kurs explodiert"},
            {"§a📰 Analyst-Upgrade!", "§7Goldman empfiehlt %s zum Kauf"}
    };

    private static final String[][] NEGATIVE_NEWS = {
            {"§c📰 Quartalsverlust!", "§7%s meldet Verluste"},
            {"§c📰 CEO tritt zurück!", "§7%s verliert Führung"},
            {"§c📰 Skandal!", "§7%s in Betrugsermittlung"},
            {"§c📰 Produktrückruf!", "§7%s muss Produkte zurückrufen"},
            {"§c📰 Klage!", "§7%s wird auf Milliarden verklagt"},
            {"§c📰 Downgrade!", "§7Analysten stufen %s ab"}
    };

    private static final String[][] MARKET_NEWS = {
            {"§e📰 Zinsentscheid", "§7Zentralbank erhöht Zinsen - Märkte reagieren"},
            {"§e📰 Wirtschaftsdaten", "§7Arbeitsmarktbericht überrascht Analysten"},
            {"§e📰 Inflation", "§7Inflationsrate steigt über Prognose"},
            {"§e📰 Geopolitik", "§7Handelsspannungen belasten Märkte"},
            {"§a📰 Rally!", "§7Märkte im Aufwind - Anleger optimistisch"},
            {"§c📰 Crash-Warnung!", "§7Analysten warnen vor Korrektur"}
    };

    public MarketSimulator(ZynaidaInvest plugin) {
        this.plugin = plugin;
        this.crossServerEnabled = plugin.getConfig().getBoolean("cross-server.enabled", false);
        String role = plugin.getConfig().getString("cross-server.role", "master");
        this.isMaster = !crossServerEnabled || "master".equalsIgnoreCase(role);
        this.isSlave = crossServerEnabled && "slave".equalsIgnoreCase(role);
        this.serverId = plugin.getConfig().getString("cross-server.server-id", "server-1");
    }

    public void start() {
        int updateInterval = plugin.getConfig().getInt("market.update-interval-ticks", 1200);
        int newsInterval = plugin.getConfig().getInt("market.news-interval-ticks", 6000);
        int dividendInterval = plugin.getConfig().getInt("market.dividend-interval-ticks", 72000);

        if (!isSlave) {
            // Master oder Single-Server: normale Simulation
            priceUpdateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickMarket, 100L, updateInterval);
            newsTask = Bukkit.getScheduler().runTaskTimer(plugin, this::generateNews, 6000L, newsInterval);
            dividendTask = Bukkit.getScheduler().runTaskTimer(plugin, this::payDividends, 12000L, dividendInterval);
            plugin.getLogger().info("§aMarkt-Simulator gestartet! Update alle " + (updateInterval / 20) + "s" +
                (crossServerEnabled ? " (Master-Modus)" : ""));
        }

        if (isSlave) {
            // Slave: Preise aus DB lesen
            int syncInterval = plugin.getConfig().getInt("cross-server.sync-interval-ticks", 100);
            syncTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::syncFromDB, 20L, syncInterval);
            plugin.getLogger().info("§eMarkt-Slave gestartet! Sync alle " + (syncInterval / 20) + "s");
        }
    }

    public void stop() {
        if (priceUpdateTask != null) priceUpdateTask.cancel();
        if (newsTask != null) newsTask.cancel();
        if (dividendTask != null) dividendTask.cancel();
        if (syncTask != null) syncTask.cancel();
    }

    // ==================== MASTER: PREIS-SIMULATION ====================

    private void tickMarket() {
        tickCount++;
        Map<String, Asset> assets = plugin.getInvestmentManager().getAssets();

        // Globales Sentiment leicht anpassen
        marketSentiment += (random.nextGaussian() * 0.05);
        marketSentiment = Math.max(-1, Math.min(1, marketSentiment));

        // Seltene Markt-Events
        if (random.nextDouble() < 0.005) {
            triggerMarketEvent();
        }

        for (Asset asset : assets.values()) {
            double newPrice = calculateNewPrice(asset);
            asset.updatePrice(newPrice);
            asset.tickTrend();
        }

        // Stop-Loss / Take-Profit prüfen
        plugin.getInvestmentManager().checkAutoTriggers();

        // Alle 20 Ticks = neuer "Tag"
        if (tickCount % 20 == 0) {
            for (Asset asset : assets.values()) {
                asset.resetDayOpen();
            }
        }

        // Cross-Server Master: Preise + State in DB schreiben
        if (crossServerEnabled && isMaster) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                plugin.getDataManager().saveAssetPricesFull(assets);
                plugin.getDataManager().saveMarketState(marketSentiment, lastDividendTime, serverId);
            });
        }
    }

    private double calculateNewPrice(Asset asset) {
        double price = asset.getCurrentPrice();
        double vol = asset.getVolatility().getMultiplier();

        double drift = 0.0001;
        double randomComponent = random.nextGaussian() * vol * 0.02;
        double trendInfluence = asset.getTrend() * 0.01;
        double sentimentInfluence = marketSentiment * 0.005;

        double meanReversion = 0;
        double deviation = (price - asset.getBasePrice()) / asset.getBasePrice();
        if (Math.abs(deviation) > 0.5) {
            meanReversion = -deviation * 0.002;
        }

        double typeModifier = switch (asset.getType()) {
            case BOND -> 0.3;
            case CRYPTO -> 1.8;
            case ETF -> 0.6;
            case COMMODITY -> 0.8;
            default -> 1.0;
        };

        double eventModifier = 0;
        if (asset.isCrashing()) {
            eventModifier = -random.nextDouble() * 0.05;
        } else if (asset.isBoosting()) {
            eventModifier = random.nextDouble() * 0.04;
        }

        double totalChange = (drift + randomComponent + trendInfluence +
                sentimentInfluence + meanReversion + eventModifier) * typeModifier;

        double maxMove = 0.15;
        totalChange = Math.max(-maxMove, Math.min(maxMove, totalChange));

        return price * (1 + totalChange);
    }

    // ==================== MASTER: NEWS ====================

    private void generateNews() {
        Map<String, Asset> assets = plugin.getInvestmentManager().getAssets();
        if (assets.isEmpty()) return;

        if (random.nextDouble() > 0.4) return;

        List<Asset> assetList = new ArrayList<>(assets.values());
        Asset target = assetList.get(random.nextInt(assetList.size()));

        double roll = random.nextDouble();

        if (roll < 0.35) {
            String[] news = POSITIVE_NEWS[random.nextInt(POSITIVE_NEWS.length)];
            String headline = String.format(news[1], target.getName());
            target.setNews(news[0] + " " + headline, 120000);
            target.setTrend(0.3 + random.nextDouble() * 0.4, 5 + random.nextInt(10));
            broadcastNews("§a§l[BÖRSEN-NEWS] " + news[0], headline);

        } else if (roll < 0.7) {
            String[] news = NEGATIVE_NEWS[random.nextInt(NEGATIVE_NEWS.length)];
            String headline = String.format(news[1], target.getName());
            target.setNews(news[0] + " " + headline, 120000);
            target.setTrend(-0.3 - random.nextDouble() * 0.4, 5 + random.nextInt(10));
            broadcastNews("§c§l[BÖRSEN-NEWS] " + news[0], headline);

        } else {
            String[] news = MARKET_NEWS[random.nextInt(MARKET_NEWS.length)];
            marketSentiment += (random.nextDouble() - 0.5) * 0.3;
            marketSentiment = Math.max(-1, Math.min(1, marketSentiment));
            broadcastNews("§e§l[MARKT-NEWS] " + news[0], news[1]);
        }
    }

    private void triggerMarketEvent() {
        Map<String, Asset> assets = plugin.getInvestmentManager().getAssets();

        if (random.nextBoolean()) {
            marketSentiment = -0.8;
            broadcastNews("§4§l⚠ MARKT-CRASH ⚠",
                    "§cPanik-Verkäufe an den Börsen! Alle Kurse fallen!");
            for (Asset asset : assets.values()) {
                asset.setCrashing(true);
                asset.setTrend(-0.7, 8 + random.nextInt(12));
            }
        } else {
            marketSentiment = 0.8;
            broadcastNews("§a§l🚀 MARKT-BOOM 🚀",
                    "§aEuphorie an den Börsen! Alle Kurse steigen!");
            for (Asset asset : assets.values()) {
                asset.setBoosting(true);
                asset.setTrend(0.6, 6 + random.nextInt(10));
            }
        }
    }

    // ==================== MASTER: DIVIDENDEN ====================

    private void payDividends() {
        plugin.getInvestmentManager().payDividends();
        lastDividendTime = System.currentTimeMillis();

        if (crossServerEnabled && isMaster) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                plugin.getDataManager().saveMarketState(marketSentiment, lastDividendTime, serverId));
        }
    }

    // ==================== SLAVE: DB SYNC ====================

    private void syncFromDB() {
        try {
            Map<String, Asset> assets = plugin.getInvestmentManager().getAssets();

            // Preise + Trend + News aus DB laden
            plugin.getDataManager().loadAssetPricesFull(assets);

            // Market State laden
            MarketStateData state = plugin.getDataManager().loadMarketState();
            if (state != null) {
                marketSentiment = state.sentiment();

                // Master-Ausfall erkennen (>5 Minuten kein Update)
                long staleThreshold = 5 * 60 * 1000;
                if (System.currentTimeMillis() - state.lastUpdate() > staleThreshold) {
                    plugin.getLogger().warning("§c[Cross-Server] Master '" + state.masterServerId() +
                        "' hat seit " + ((System.currentTimeMillis() - state.lastUpdate()) / 1000) + "s nicht aktualisiert!");
                }
            }

            // Auto-Triggers auf Main-Thread prüfen (Stop-Loss/Take-Profit)
            Bukkit.getScheduler().runTask(plugin, () ->
                plugin.getInvestmentManager().checkAutoTriggers());

        } catch (Exception e) {
            plugin.getLogger().warning("§c[Cross-Server] Sync fehlgeschlagen: " + e.getMessage());
        }
    }

    // ==================== HELPER ====================

    private void broadcastNews(String title, String subtitle) {
        boolean broadcast = plugin.getConfig().getBoolean("market.broadcast-news", true);
        if (!broadcast) return;

        String prefix = plugin.getConfig().getString("messages.prefix", "§8[§6§lInvest§8] ");
        Bukkit.getOnlinePlayers().forEach(p -> {
            p.sendMessage(prefix + title);
            p.sendMessage(prefix + subtitle);
        });
    }

    public double getMarketSentiment() {
        return marketSentiment;
    }

    public String getSentimentDisplay() {
        if (marketSentiment < -0.5) return "§4☠ Extreme Angst";
        if (marketSentiment < -0.2) return "§c😰 Angst";
        if (marketSentiment < 0.2) return "§e😐 Neutral";
        if (marketSentiment < 0.5) return "§a😊 Gier";
        return "§a🤑 Extreme Gier";
    }

    public long getLastDividendTime() {
        return lastDividendTime;
    }

    public boolean isCrossServerMaster() {
        return crossServerEnabled && isMaster;
    }

    public boolean isCrossServerSlave() {
        return isSlave;
    }

    public String getServerId() {
        return serverId;
    }
}
