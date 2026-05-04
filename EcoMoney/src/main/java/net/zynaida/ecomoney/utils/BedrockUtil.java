package net.zynaida.ecomoney.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.UUID;

/**
 * Utility-Klasse für Bedrock-Spieler Kompatibilität
 * Unterstützt Geyser + Floodgate
 */
public class BedrockUtil {

    private static Boolean floodgateAvailable = null;

    private BedrockUtil() {
        // Utility-Klasse
    }

    /**
     * Prüft ob Floodgate verfügbar ist
     */
    public static boolean isFloodgateAvailable() {
        if (floodgateAvailable == null) {
            floodgateAvailable = Bukkit.getPluginManager().getPlugin("floodgate") != null;
        }
        return floodgateAvailable;
    }

    /**
     * Prüft ob ein Spieler ein Bedrock-Spieler ist
     */
    public static boolean isBedrockPlayer(Player player) {
        if (player == null) return false;
        return isBedrockPlayer(player.getUniqueId());
    }

    /**
     * Prüft ob eine UUID zu einem Bedrock-Spieler gehört
     */
    public static boolean isBedrockPlayer(UUID uuid) {
        if (uuid == null) return false;
        
        // Methode 1: Floodgate API
        if (isFloodgateAvailable()) {
            try {
                return FloodgateApi.getInstance().isFloodgatePlayer(uuid);
            } catch (Exception ignored) {
                // Floodgate nicht richtig geladen
            }
        }
        
        // Methode 2: UUID-Prefix Check (Floodgate UUID-Prefix)
        // Floodgate UUIDs beginnen typischerweise mit 00000000-0000-0000
        String uuidString = uuid.toString();
        return uuidString.startsWith("00000000-0000-0000");
    }

    /**
     * Holt den Bedrock-Namen eines Spielers (ohne Prefix)
     */
    public static String getBedrockName(Player player) {
        if (player == null) return null;
        
        if (isFloodgateAvailable() && isBedrockPlayer(player)) {
            try {
                var floodgatePlayer = FloodgateApi.getInstance().getPlayer(player.getUniqueId());
                if (floodgatePlayer != null) {
                    return floodgatePlayer.getUsername();
                }
            } catch (Exception ignored) {
                // Fallback
            }
        }
        
        // Fallback: Prefix entfernen wenn vorhanden
        String name = player.getName();
        if (name.startsWith(".")) {
            return name.substring(1);
        }
        return name;
    }

    /**
     * Gibt den Display-Namen zurück (für Bedrock ohne Prefix)
     */
    public static String getDisplayName(Player player) {
        if (isBedrockPlayer(player)) {
            return getBedrockName(player);
        }
        return player.getName();
    }

    /**
     * Prüft ob ein Name ein Bedrock-Prefix hat
     */
    public static boolean hasBedrockPrefix(String name) {
        if (name == null) return false;
        // Standard Floodgate Prefixe
        return name.startsWith(".") || name.startsWith("*") || name.startsWith("+");
    }

    /**
     * Entfernt das Bedrock-Prefix von einem Namen
     */
    public static String stripBedrockPrefix(String name) {
        if (name == null) return null;
        if (hasBedrockPrefix(name) && name.length() > 1) {
            return name.substring(1);
        }
        return name;
    }
}
