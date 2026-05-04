package net.zynaida.ecomoney.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;

/**
 * Utility-Klasse für Nachrichten-Formatierung
 */
public class MessageUtil {

    private MessageUtil() {
        // Utility-Klasse
    }

    /**
     * Konvertiert Farbcodes (&) zu Minecraft-Farben
     */
    public static String color(String message) {
        if (message == null) return "";
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    /**
     * Konvertiert zu Adventure Component
     */
    public static Component toComponent(String message) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(message);
    }

    /**
     * Entfernt alle Farbcodes aus einer Nachricht
     */
    public static String stripColors(String message) {
        if (message == null) return "";
        return ChatColor.stripColor(color(message));
    }
}
