package net.zynaida.ecomoney.api.events;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Event das bei jeder Economy-Transaktion gefeuert wird
 * Kann von anderen Plugins abgefangen und gecancelt werden
 */
public class EcoMoneyTransactionEvent extends Event implements Cancellable {

    private static final HandlerList handlers = new HandlerList();
    
    private final UUID player;
    private final UUID target; // Für Transfers
    private final BigDecimal oldBalance;
    private final BigDecimal newBalance;
    private final TransactionType type;
    private final String reason;
    private boolean cancelled = false;

    public EcoMoneyTransactionEvent(UUID player, UUID target, BigDecimal oldBalance, 
                                    BigDecimal newBalance, TransactionType type, String reason) {
        this.player = player;
        this.target = target;
        this.oldBalance = oldBalance;
        this.newBalance = newBalance;
        this.type = type;
        this.reason = reason;
    }

    /**
     * @return UUID des Hauptspielers
     */
    public UUID getPlayer() {
        return player;
    }

    /**
     * @return UUID des Zielenspielers (bei Transfers)
     */
    public UUID getTarget() {
        return target;
    }

    /**
     * @return Altes Guthaben vor der Transaktion
     */
    public BigDecimal getOldBalance() {
        return oldBalance;
    }

    /**
     * @return Neues Guthaben nach der Transaktion (oder Betrag bei Transfer)
     */
    public BigDecimal getNewBalance() {
        return newBalance;
    }

    /**
     * @return Typ der Transaktion
     */
    public TransactionType getType() {
        return type;
    }

    /**
     * @return Grund der Transaktion
     */
    public String getReason() {
        return reason;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
