package net.zynaida.invest.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Custom InventoryHolder - identifiziert unsere GUIs zuverlässig
 * ohne Title-String-Matching (Paper Adventure Components Problem)
 */
public class InvestHolder implements InventoryHolder {

    public enum GUIType {
        MAIN,
        UNLOCK,
        CATEGORY,
        ASSET_DETAIL,
        PORTFOLIO,
        LEADERBOARD,
        SETTINGS
    }

    private final GUIType type;
    private final String data; // z.B. Ticker, AssetType-Name, etc.
    private Inventory inventory;

    public InvestHolder(GUIType type) {
        this(type, "");
    }

    public InvestHolder(GUIType type, String data) {
        this.type = type;
        this.data = data;
    }

    public GUIType getType() {
        return type;
    }

    public String getData() {
        return data;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
