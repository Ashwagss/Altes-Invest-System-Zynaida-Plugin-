package net.zynaida.ecomoney.api.events;

import java.math.BigDecimal;

/**
 * Ergebnis einer Economy-Transaktion
 */
public class TransactionResult {

    private final boolean success;
    private final String message;
    private final BigDecimal amount;

    public TransactionResult(boolean success, String message) {
        this(success, message, BigDecimal.ZERO);
    }

    public TransactionResult(boolean success, String message, BigDecimal amount) {
        this.success = success;
        this.message = message;
        this.amount = amount;
    }

    /**
     * @return true wenn die Transaktion erfolgreich war
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * @return Nachricht/Fehlergrund
     */
    public String getMessage() {
        return message;
    }

    /**
     * @return Betrag der Transaktion
     */
    public BigDecimal getAmount() {
        return amount;
    }
}
