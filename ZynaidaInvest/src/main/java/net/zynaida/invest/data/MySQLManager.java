package net.zynaida.invest.data;

import net.zynaida.invest.ZynaidaInvest;
import net.zynaida.invest.models.Asset;
import net.zynaida.invest.models.Investment;
import net.zynaida.invest.models.Portfolio;

import java.sql.*;
import java.util.*;

public class MySQLManager implements DataManager {

    private final ZynaidaInvest plugin;
    private Connection connection;

    private String jdbcUrl;
    private String username;
    private String password;
    private boolean initialized = false;

    public MySQLManager(ZynaidaInvest plugin) {
        this.plugin = plugin;
    }

    @Override
    public void initialize() {
        try {
            String host = plugin.getConfig().getString("storage.mysql.host", "localhost");
            int port = plugin.getConfig().getInt("storage.mysql.port", 3306);
            String database = plugin.getConfig().getString("storage.mysql.database", "zynaidainvest");
            this.username = plugin.getConfig().getString("storage.mysql.username", "root");
            this.password = plugin.getConfig().getString("storage.mysql.password", "");
            boolean ssl = plugin.getConfig().getBoolean("storage.mysql.useSSL", false);

            this.jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database +
                "?useSSL=" + ssl +
                "&autoReconnect=true" +
                "&useUnicode=true" +
                "&characterEncoding=UTF-8";

            connection = DriverManager.getConnection(jdbcUrl, username, password);
            createTables();
            initialized = true;
            plugin.getLogger().info("MySQL Verbindung hergestellt!");
        } catch (SQLException e) {
            plugin.getLogger().severe("MySQL Verbindung fehlgeschlagen: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS invest_portfolios (
                    uuid VARCHAR(36) PRIMARY KEY,
                    realized_profit DOUBLE DEFAULT 0,
                    dividends_received DOUBLE DEFAULT 0,
                    trade_count INT DEFAULT 0,
                    unlocked BOOLEAN DEFAULT FALSE,
                    unlock_time BIGINT DEFAULT 0,
                    tier INT DEFAULT 0
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS invest_investments (
                    id VARCHAR(36) PRIMARY KEY,
                    player_uuid VARCHAR(36) NOT NULL,
                    asset_id VARCHAR(64) NOT NULL,
                    shares DOUBLE NOT NULL,
                    buy_price DOUBLE NOT NULL,
                    total_invested DOUBLE NOT NULL,
                    buy_time BIGINT NOT NULL,
                    last_dividend BIGINT DEFAULT 0,
                    dividends_earned DOUBLE DEFAULT 0,
                    auto_reinvest BOOLEAN DEFAULT FALSE,
                    stop_loss DOUBLE DEFAULT -1,
                    take_profit DOUBLE DEFAULT -1,
                    UNIQUE KEY unique_player_asset (player_uuid, asset_id),
                    INDEX idx_player (player_uuid)
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS invest_prices (
                    asset_id VARCHAR(64) PRIMARY KEY,
                    current_price DOUBLE NOT NULL,
                    ath DOUBLE DEFAULT 0,
                    atl DOUBLE DEFAULT 999999999
                )
            """);

            // Cross-Server Tabelle
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS invest_market_state (
                    id INT PRIMARY KEY DEFAULT 1,
                    sentiment DOUBLE DEFAULT 0,
                    last_dividend_time BIGINT DEFAULT 0,
                    master_server_id VARCHAR(64) NOT NULL DEFAULT 'unknown',
                    last_update BIGINT NOT NULL DEFAULT 0
                )
            """);
        }

        // invest_prices erweitern (ALTER TABLE - Spalten nur hinzufügen wenn nicht vorhanden)
        String[] alterStatements = {
            "ALTER TABLE invest_prices ADD COLUMN previous_price DOUBLE DEFAULT 0",
            "ALTER TABLE invest_prices ADD COLUMN day_open_price DOUBLE DEFAULT 0",
            "ALTER TABLE invest_prices ADD COLUMN trend DOUBLE DEFAULT 0",
            "ALTER TABLE invest_prices ADD COLUMN trend_duration INT DEFAULT 0",
            "ALTER TABLE invest_prices ADD COLUMN is_crashing BOOLEAN DEFAULT FALSE",
            "ALTER TABLE invest_prices ADD COLUMN is_boosting BOOLEAN DEFAULT FALSE",
            "ALTER TABLE invest_prices ADD COLUMN current_news TEXT DEFAULT NULL",
            "ALTER TABLE invest_prices ADD COLUMN news_expiry BIGINT DEFAULT 0"
        };

        for (String sql : alterStatements) {
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate(sql);
            } catch (SQLException e) {
                // Spalte existiert bereits - ignorieren
                if (!e.getMessage().contains("Duplicate column")) {
                    // Nur loggen wenn es kein "Spalte existiert"-Fehler ist
                    plugin.getLogger().fine("ALTER TABLE: " + e.getMessage());
                }
            }
        }
    }

    private Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            if (!initialized) {
                initialize();
            } else {
                connection = DriverManager.getConnection(jdbcUrl, username, password);
                plugin.getLogger().info("MySQL Verbindung wiederhergestellt.");
            }
        }
        return connection;
    }

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ==================== PORTFOLIO CRUD ====================

    @Override
    public void savePortfolio(Portfolio portfolio) {
        try {
            Connection conn = getConnection();
            conn.setAutoCommit(false);
            try {
                // Portfolio Meta - INSERT ... ON DUPLICATE KEY UPDATE
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO invest_portfolios (uuid, realized_profit, dividends_received, trade_count, unlocked, unlock_time, tier) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                        "realized_profit=?, dividends_received=?, trade_count=?, unlocked=?, unlock_time=?, tier=?")) {
                    String uuid = portfolio.getPlayerUUID().toString();
                    double rp = portfolio.getTotalRealizedProfit();
                    double dr = portfolio.getTotalDividendsReceived();
                    int tc = portfolio.getTotalTradeCount();
                    boolean ul = portfolio.isUnlocked();
                    long ut = portfolio.getUnlockTime();
                    int ti = portfolio.getPortfolioTier();

                    ps.setString(1, uuid);
                    ps.setDouble(2, rp);
                    ps.setDouble(3, dr);
                    ps.setInt(4, tc);
                    ps.setBoolean(5, ul);
                    ps.setLong(6, ut);
                    ps.setInt(7, ti);
                    ps.setDouble(8, rp);
                    ps.setDouble(9, dr);
                    ps.setInt(10, tc);
                    ps.setBoolean(11, ul);
                    ps.setLong(12, ut);
                    ps.setInt(13, ti);
                    ps.executeUpdate();
                }

                // Investments: Alte löschen, neue einfügen
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM invest_investments WHERE player_uuid = ?")) {
                    ps.setString(1, portfolio.getPlayerUUID().toString());
                    ps.executeUpdate();
                }

                for (Investment inv : portfolio.getInvestments()) {
                    if (inv.isEmpty()) continue;
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO invest_investments (id, player_uuid, asset_id, shares, buy_price, " +
                            "total_invested, buy_time, last_dividend, dividends_earned, auto_reinvest, stop_loss, take_profit) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                        ps.setString(1, inv.getId().toString());
                        ps.setString(2, inv.getPlayerUUID().toString());
                        ps.setString(3, inv.getAssetId());
                        ps.setDouble(4, inv.getShares());
                        ps.setDouble(5, inv.getBuyPrice());
                        ps.setDouble(6, inv.getTotalInvested());
                        ps.setLong(7, inv.getBuyTime());
                        ps.setLong(8, inv.getLastDividendClaim());
                        ps.setDouble(9, inv.getTotalDividendsEarned());
                        ps.setBoolean(10, inv.isAutoReinvest());
                        ps.setDouble(11, inv.getStopLossPercent());
                        ps.setDouble(12, inv.getTakeProfitPercent());
                        ps.executeUpdate();
                    }
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Portfolio speichern fehlgeschlagen: " + e.getMessage());
        }
    }

    @Override
    public Portfolio loadPortfolio(UUID uuid, Map<String, Asset> assets) {
        try {
            Portfolio portfolio = new Portfolio(uuid);

            try (PreparedStatement ps = getConnection().prepareStatement(
                    "SELECT * FROM invest_portfolios WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    portfolio.setUnlocked(rs.getBoolean("unlocked"));
                    portfolio.setUnlockTime(rs.getLong("unlock_time"));
                    portfolio.setPortfolioTier(rs.getInt("tier"));
                    portfolio.setTotalRealizedProfit(rs.getDouble("realized_profit"));
                    portfolio.setTotalDividendsReceived(rs.getDouble("dividends_received"));
                    portfolio.setTotalTradeCount(rs.getInt("trade_count"));
                } else {
                    return null; // Portfolio existiert nicht in DB
                }
            }

            try (PreparedStatement ps = getConnection().prepareStatement(
                    "SELECT * FROM invest_investments WHERE player_uuid = ?")) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();

                while (rs.next()) {
                    String assetId = rs.getString("asset_id");
                    if (!assets.containsKey(assetId)) continue;

                    Investment inv = new Investment(
                            UUID.fromString(rs.getString("id")),
                            uuid,
                            assetId,
                            rs.getDouble("shares"),
                            rs.getDouble("buy_price"),
                            rs.getDouble("total_invested"),
                            rs.getLong("buy_time"),
                            rs.getLong("last_dividend"),
                            rs.getDouble("dividends_earned"),
                            rs.getBoolean("auto_reinvest"),
                            rs.getDouble("stop_loss"),
                            rs.getDouble("take_profit")
                    );
                    portfolio.addInvestment(inv);
                }
            }

            return portfolio;
        } catch (SQLException e) {
            plugin.getLogger().severe("Portfolio laden fehlgeschlagen: " + e.getMessage());
            return null;
        }
    }

    @Override
    public Map<UUID, Portfolio> loadAllPortfolios(Map<String, Asset> assets) {
        Map<UUID, Portfolio> result = new HashMap<>();

        loadAssetPrices(assets);

        try (PreparedStatement ps = getConnection().prepareStatement("SELECT uuid FROM invest_portfolios");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                Portfolio portfolio = loadPortfolio(uuid, assets);
                if (portfolio != null) {
                    result.put(uuid, portfolio);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Portfolios laden fehlgeschlagen: " + e.getMessage());
        }

        return result;
    }

    @Override
    public void deletePortfolio(UUID uuid) {
        try {
            try (PreparedStatement ps = getConnection().prepareStatement(
                    "DELETE FROM invest_investments WHERE player_uuid = ?")) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = getConnection().prepareStatement(
                    "DELETE FROM invest_portfolios WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Portfolio löschen fehlgeschlagen: " + e.getMessage());
        }
    }

    // ==================== ASSET PREISE (Basis) ====================

    @Override
    public void saveAssetPrices(Map<String, Asset> assets) {
        try {
            for (Asset asset : assets.values()) {
                try (PreparedStatement ps = getConnection().prepareStatement(
                        "INSERT INTO invest_prices (asset_id, current_price, ath, atl) " +
                        "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                        "current_price=?, ath=?, atl=?")) {
                    ps.setString(1, asset.getId());
                    ps.setDouble(2, asset.getCurrentPrice());
                    ps.setDouble(3, asset.getAllTimeHigh());
                    ps.setDouble(4, asset.getAllTimeLow());
                    ps.setDouble(5, asset.getCurrentPrice());
                    ps.setDouble(6, asset.getAllTimeHigh());
                    ps.setDouble(7, asset.getAllTimeLow());
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Preise speichern fehlgeschlagen: " + e.getMessage());
        }
    }

    @Override
    public void loadAssetPrices(Map<String, Asset> assets) {
        try (PreparedStatement ps = getConnection().prepareStatement("SELECT * FROM invest_prices");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Asset asset = assets.get(rs.getString("asset_id"));
                if (asset != null) {
                    asset.updatePrice(rs.getDouble("current_price"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Preise laden fehlgeschlagen: " + e.getMessage());
        }
    }

    // ==================== CROSS-SERVER: ERWEITERTE PREISE ====================

    @Override
    public void saveAssetPricesFull(Map<String, Asset> assets) {
        try {
            for (Asset asset : assets.values()) {
                try (PreparedStatement ps = getConnection().prepareStatement(
                        "INSERT INTO invest_prices (asset_id, current_price, ath, atl, " +
                        "previous_price, day_open_price, trend, trend_duration, is_crashing, is_boosting, current_news, news_expiry) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                        "current_price=?, ath=?, atl=?, previous_price=?, day_open_price=?, " +
                        "trend=?, trend_duration=?, is_crashing=?, is_boosting=?, current_news=?, news_expiry=?")) {
                    // INSERT-Werte
                    ps.setString(1, asset.getId());
                    ps.setDouble(2, asset.getCurrentPrice());
                    ps.setDouble(3, asset.getAllTimeHigh());
                    ps.setDouble(4, asset.getAllTimeLow());
                    ps.setDouble(5, asset.getPreviousPrice());
                    ps.setDouble(6, asset.getDayOpenPrice());
                    ps.setDouble(7, asset.getTrend());
                    ps.setInt(8, asset.getTrendDuration());
                    ps.setBoolean(9, asset.isCrashing());
                    ps.setBoolean(10, asset.isBoosting());
                    ps.setString(11, asset.getCurrentNews());
                    ps.setLong(12, asset.hasActiveNews() ? asset.getNewsExpiry() : 0);
                    // UPDATE-Werte
                    ps.setDouble(13, asset.getCurrentPrice());
                    ps.setDouble(14, asset.getAllTimeHigh());
                    ps.setDouble(15, asset.getAllTimeLow());
                    ps.setDouble(16, asset.getPreviousPrice());
                    ps.setDouble(17, asset.getDayOpenPrice());
                    ps.setDouble(18, asset.getTrend());
                    ps.setInt(19, asset.getTrendDuration());
                    ps.setBoolean(20, asset.isCrashing());
                    ps.setBoolean(21, asset.isBoosting());
                    ps.setString(22, asset.getCurrentNews());
                    ps.setLong(23, asset.hasActiveNews() ? asset.getNewsExpiry() : 0);
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erweiterte Preise speichern fehlgeschlagen: " + e.getMessage());
        }
    }

    @Override
    public void loadAssetPricesFull(Map<String, Asset> assets) {
        try (PreparedStatement ps = getConnection().prepareStatement("SELECT * FROM invest_prices");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Asset asset = assets.get(rs.getString("asset_id"));
                if (asset == null) continue;

                double currentPrice = rs.getDouble("current_price");
                asset.updatePrice(currentPrice);
                asset.setAllTimeHigh(rs.getDouble("ath"));
                asset.setAllTimeLow(rs.getDouble("atl"));
                asset.setPreviousPrice(rs.getDouble("previous_price"));
                asset.setDayOpenPrice(rs.getDouble("day_open_price"));
                asset.setTrendDirect(rs.getDouble("trend"), rs.getInt("trend_duration"));
                asset.setCrashing(rs.getBoolean("is_crashing"));
                asset.setBoosting(rs.getBoolean("is_boosting"));

                String news = rs.getString("current_news");
                long newsExpiry = rs.getLong("news_expiry");
                if (news != null && newsExpiry > System.currentTimeMillis()) {
                    asset.setCurrentNewsDirect(news, newsExpiry);
                } else {
                    asset.setCurrentNewsDirect(null, 0);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erweiterte Preise laden fehlgeschlagen: " + e.getMessage());
        }
    }

    // ==================== CROSS-SERVER: MARKET STATE ====================

    @Override
    public void saveMarketState(double sentiment, long lastDividendTime, String serverId) {
        try (PreparedStatement ps = getConnection().prepareStatement(
                "INSERT INTO invest_market_state (id, sentiment, last_dividend_time, master_server_id, last_update) " +
                "VALUES (1, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                "sentiment=?, last_dividend_time=?, master_server_id=?, last_update=?")) {
            long now = System.currentTimeMillis();
            ps.setDouble(1, sentiment);
            ps.setLong(2, lastDividendTime);
            ps.setString(3, serverId);
            ps.setLong(4, now);
            ps.setDouble(5, sentiment);
            ps.setLong(6, lastDividendTime);
            ps.setString(7, serverId);
            ps.setLong(8, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Market State speichern fehlgeschlagen: " + e.getMessage());
        }
    }

    @Override
    public MarketStateData loadMarketState() {
        try (PreparedStatement ps = getConnection().prepareStatement(
                "SELECT * FROM invest_market_state WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return new MarketStateData(
                    rs.getDouble("sentiment"),
                    rs.getLong("last_dividend_time"),
                    rs.getString("master_server_id"),
                    rs.getLong("last_update")
                );
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Market State laden fehlgeschlagen: " + e.getMessage());
        }
        return null;
    }

    // ==================== CROSS-SERVER: LEADERBOARD ====================

    @Override
    public List<Map.Entry<UUID, Double>> getTopInvestorsFromDB(int limit, Map<String, Asset> assets) {
        List<Map.Entry<UUID, Double>> result = new ArrayList<>();
        try (PreparedStatement ps = getConnection().prepareStatement(
                "SELECT p.uuid, p.realized_profit, " +
                "COALESCE(SUM(i.shares * pr.current_price), 0) AS portfolio_value " +
                "FROM invest_portfolios p " +
                "LEFT JOIN invest_investments i ON p.uuid = i.player_uuid " +
                "LEFT JOIN invest_prices pr ON i.asset_id = pr.asset_id " +
                "WHERE p.unlocked = TRUE " +
                "GROUP BY p.uuid, p.realized_profit " +
                "ORDER BY (p.realized_profit + COALESCE(SUM(i.shares * pr.current_price), 0)) DESC " +
                "LIMIT ?")) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                double totalValue = rs.getDouble("realized_profit") + rs.getDouble("portfolio_value");
                result.add(Map.entry(uuid, totalValue));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Leaderboard laden fehlgeschlagen: " + e.getMessage());
        }
        return result;
    }
}
