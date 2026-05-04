package net.zynaida.ecomoney.api.events;

/**
 * Typen von Economy-Transaktionen
 */
public enum TransactionType {
    DEPOSIT,     // Einzahlung
    WITHDRAW,    // Abhebung
    TRANSFER,    // Überweisung
    SET          // Admin-Setzung
}
