package net.zynaida.ecomoney.data;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.UUID;

/**
 * Interface für Datenspeicherung
 */
public interface DataManager {

    /**
     * Initialisiert die Datenquelle
     */
    void initialize();

    /**
     * Schließt die Datenquelle
     */
    void shutdown();

    /**
     * Speichert alle Daten
     */
    void saveAll();

    /**
     * Prüft ob ein Spieler ein Konto hat
     */
    boolean hasAccount(UUID uuid);

    /**
     * Erstellt ein Konto für einen Spieler
     */
    boolean createAccount(UUID uuid, BigDecimal startBalance);

    /**
     * Holt das Guthaben eines Spielers
     */
    BigDecimal getBalance(UUID uuid);

    /**
     * Holt das Guthaben direkt aus der Datenbank (ohne Cache).
     * Wird für kritische Operationen bei Cross-Server-Sync verwendet.
     */
    default BigDecimal getBalanceFromDB(UUID uuid) {
        return getBalance(uuid);
    }

    /**
     * Setzt das Guthaben eines Spielers
     */
    boolean setBalance(UUID uuid, BigDecimal amount);

    /**
     * Gibt die Top-Spieler nach Guthaben zurück
     * @param limit Anzahl der Spieler
     * @return Map von UUID zu Guthaben, sortiert nach Guthaben absteigend
     */
    LinkedHashMap<UUID, BigDecimal> getTopBalances(int limit);

    /**
     * Aktualisiert den Namen eines Spielers in der Datenbank
     */
    void updatePlayerName(UUID uuid, String name);

    /**
     * Holt den gespeicherten Namen eines Spielers
     */
    String getPlayerName(UUID uuid);
}
