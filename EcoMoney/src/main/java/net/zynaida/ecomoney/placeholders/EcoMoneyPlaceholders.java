package net.zynaida.ecomoney.placeholders;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.zynaida.ecomoney.EcoMoney;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * PlaceholderAPI Expansion für EcoMoney
 * Unterstützt auch Bedrock-Spieler (Geyser/Floodgate)
 * 
 * Verfügbare Placeholder:
 * - %ecomoney_balance% - Formatiertes Guthaben
 * - %ecomoney_balance_raw% - Guthaben ohne Formatierung
 * - %ecomoney_balance_formatted% - Guthaben mit Währungssymbol
 * - %ecomoney_balance_short% - Kurzformat (1.5K, 2.3M, etc.)
 * - %ecomoney_balance_int% - Guthaben als Ganzzahl
 * - %ecomoney_currency% - Währungsname
 * - %ecomoney_currency_symbol% - Währungssymbol
 * - %ecomoney_top_<position>% - Kompletter Eintrag: "Name - Betrag" (z.B. %ecomoney_top_1%)
 * - %ecomoney_top_rank% - Eigene Platzierung in der Top-Liste
 */
public class EcoMoneyPlaceholders extends PlaceholderExpansion {

    private final EcoMoney plugin;
    
    // Cache für Top-Liste (Performance für Bedrock)
    private LinkedHashMap<UUID, BigDecimal> topCache;
    private long lastTopCacheTime = 0;
    private static final long TOP_CACHE_DURATION = 30000; // 30 Sekunden

    public EcoMoneyPlaceholders(EcoMoney plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "ecomoney";
    }

    @Override
    public @NotNull String getAuthor() {
        return plugin.getDescription().getAuthors().toString();
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true; // Bleibt nach Reload registriert
    }

    @Override
    public boolean canRegister() {
        return plugin.isEnabled();
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        // Für Placeholder die keinen Spieler brauchen
        if (params.equalsIgnoreCase("currency")) {
            return plugin.getAPI().getCurrencyName(true);
        }
        
        if (params.equalsIgnoreCase("currency_singular")) {
            return plugin.getAPI().getCurrencyName(false);
        }
        
        if (params.equalsIgnoreCase("currency_symbol")) {
            return plugin.getConfig().getString("economy.currency-symbol", "$");
        }
        
        // Top-Liste Placeholder - EINZELNER PLACEHOLDER für kompletten Eintrag
        // Format: %ecomoney_top_1% -> "Spielername - $1,234.56"
        if (params.toLowerCase().startsWith("top_") && !params.equalsIgnoreCase("top_rank")) {
            return getTopEntry(params);
        }
        
        // Ab hier brauchen wir einen Spieler
        if (player == null) {
            return "";
        }
        
        UUID uuid = player.getUniqueId();
        
        // Konto erstellen falls nicht vorhanden (wichtig für Bedrock-Spieler)
        if (!plugin.getAPI().hasAccount(uuid)) {
            plugin.getAPI().createAccount(uuid);
        }
        
        BigDecimal balance = plugin.getAPI().getBalance(uuid);
        
        return switch (params.toLowerCase()) {
            case "balance" -> formatBalance(balance);
            case "balance_raw" -> balance.toPlainString();
            case "balance_formatted" -> plugin.getAPI().formatMoney(balance);
            case "balance_short" -> formatShort(balance);
            case "balance_int" -> String.valueOf(balance.intValue());
            case "balance_long" -> String.valueOf(balance.longValue());
            case "balance_commas" -> formatWithCommas(balance);
            case "balance_dots" -> formatWithDots(balance);
            case "top_rank" -> String.valueOf(getPlayerRank(uuid));
            case "has_account" -> String.valueOf(plugin.getAPI().hasAccount(uuid));
            default -> null;
        };
    }

    /**
     * Formatiert das Guthaben mit 2 Dezimalstellen
     */
    private String formatBalance(BigDecimal balance) {
        DecimalFormat df = new DecimalFormat("#,##0.00");
        return df.format(balance);
    }

    /**
     * Formatiert mit Kommas als Tausendertrenner (für DE)
     */
    private String formatWithCommas(BigDecimal balance) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.GERMANY);
        DecimalFormat df = new DecimalFormat("#,##0.00", symbols);
        return df.format(balance);
    }

    /**
     * Formatiert mit Punkten als Tausendertrenner (für EN)
     */
    private String formatWithDots(BigDecimal balance) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        DecimalFormat df = new DecimalFormat("#,##0.00", symbols);
        return df.format(balance);
    }

    /**
     * Formatiert zu Kurzformat (K, M, B, T)
     * Besonders nützlich für Bedrock-Spieler mit kleinerem Bildschirm
     */
    private String formatShort(BigDecimal balance) {
        double value = balance.doubleValue();

        if (value < 1000) {
            return String.format("%.2f", value);
        } else if (value < 1_000_000) {
            return formatSuffix(value / 1000, "K");
        } else if (value < 1_000_000_000) {
            return formatSuffix(value / 1_000_000, "M");
        } else if (value < 1_000_000_000_000L) {
            return formatSuffix(value / 1_000_000_000, "B");
        } else {
            return formatSuffix(value / 1_000_000_000_000L, "T");
        }
    }

    /**
     * Formatiert einen Wert mit Suffix, ohne unnötige Dezimalstellen
     * 10.0K -> 10K, 10.5K -> 10.5K
     */
    private String formatSuffix(double value, String suffix) {
        if (value == Math.floor(value)) {
            return String.format("%.0f%s", value, suffix);
        }
        return String.format("%.1f%s", value, suffix);
    }

    /**
     * Holt die gecachte Top-Liste (Top 50)
     */
    private LinkedHashMap<UUID, BigDecimal> getTopList() {
        long now = System.currentTimeMillis();
        if (topCache == null || (now - lastTopCacheTime) > TOP_CACHE_DURATION) {
            topCache = plugin.getDataManager().getTopBalances(50);
            lastTopCacheTime = now;
        }
        return topCache;
    }

    /**
     * Gibt einen kompletten Top-Eintrag zurück: "Name - Betrag"
     * Format: %ecomoney_top_1% -> "Spielername - $1,234.56"
     */
    private String getTopEntry(String params) {
        try {
            int position = Integer.parseInt(params.substring("top_".length()));
            if (position < 1) return "-";
            
            LinkedHashMap<UUID, BigDecimal> top = getTopList();
            int index = 1;
            
            for (Map.Entry<UUID, BigDecimal> entry : top.entrySet()) {
                if (index == position) {
                    UUID uuid = entry.getKey();
                    BigDecimal balance = entry.getValue();
                    
                    // Spielername holen
                    String name = plugin.getDataManager().getPlayerName(uuid);
                    if (name.equals("Unbekannt")) {
                        var offlinePlayer = plugin.getServer().getOfflinePlayer(uuid);
                        if (offlinePlayer.getName() != null) {
                            name = offlinePlayer.getName();
                        }
                    }
                    
                    // Format: "Name - Betrag"
                    return name + " - " + plugin.getAPI().formatMoney(balance);
                }
                index++;
            }
            return "-";
        } catch (NumberFormatException e) {
            return "-";
        }
    }

    /**
     * Gibt die Platzierung eines Spielers in der Top-Liste zurück
     */
    private int getPlayerRank(UUID uuid) {
        LinkedHashMap<UUID, BigDecimal> top = getTopList();
        int rank = 1;
        
        for (UUID topUuid : top.keySet()) {
            if (topUuid.equals(uuid)) {
                return rank;
            }
            rank++;
        }
        return -1; // Nicht in Top-Liste
    }
}
