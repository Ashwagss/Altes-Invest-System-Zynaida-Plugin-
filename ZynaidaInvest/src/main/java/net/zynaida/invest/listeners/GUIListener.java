package net.zynaida.invest.listeners;

import net.zynaida.invest.ZynaidaInvest;
import net.zynaida.invest.gui.InvestGUI;
import net.zynaida.invest.gui.InvestHolder;
import net.zynaida.invest.gui.InvestHolder.GUIType;
import net.zynaida.invest.invest.InvestmentManager;
import net.zynaida.invest.invest.InvestmentManager.TradeResult;
import net.zynaida.invest.models.Asset;
import net.zynaida.invest.models.Asset.AssetType;
import net.zynaida.invest.models.Investment;
import net.zynaida.invest.models.Portfolio;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;

import java.util.Map;

public class GUIListener implements Listener {

    private final ZynaidaInvest plugin;
    private final String prefix;

    public GUIListener(ZynaidaInvest plugin) {
        this.plugin = plugin;
        this.prefix = plugin.getConfig().getString("messages.prefix", "§8[§6§lInvest§8] ");
    }

    // ==================== KLICK HANDLER ====================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Prüfe ob es unser GUI ist via InventoryHolder
        // Paper nutzt getHolder(false) um den originalen Holder zu bekommen
        InvestHolder holder = getInvestHolder(event);
        if (holder == null) return;

        // ANTI-EXPLOIT: Alles canceln
        event.setCancelled(true);
        event.setResult(InventoryClickEvent.Result.DENY);

        // Nur Klicks im oberen Inventory
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;

        int slot = event.getRawSlot();
        InvestGUI gui = new InvestGUI(plugin);
        GUIType type = holder.getType();
        String data = holder.getData();

        switch (type) {
            case MAIN -> handleMainMenu(player, slot, gui);
            case UNLOCK -> handleUnlock(player, slot, gui);
            case CATEGORY -> handleCategory(player, slot, data, gui);
            case ASSET_DETAIL -> handleAssetDetail(player, slot, data, event, gui);
            case PORTFOLIO -> handlePortfolio(player, slot, gui);
            case LEADERBOARD -> { if (slot == 49) gui.openMainMenu(player); }
            case SETTINGS -> handleSettings(player, slot, data, event, gui);
        }
    }

    // ==================== HAUPTMENÜ ====================

    private void handleMainMenu(Player player, int slot, InvestGUI gui) {
        switch (slot) {
            case 4 -> {
                Portfolio p = plugin.getInvestmentManager().getOrCreatePortfolio(player.getUniqueId());
                if (p.getPortfolioTier() < 3) gui.openUnlockGUI(player);
            }
            case 20 -> gui.openCategory(player, AssetType.STOCK);
            case 22 -> gui.openCategory(player, AssetType.CRYPTO);
            case 24 -> gui.openCategory(player, AssetType.BOND);
            case 30 -> gui.openCategory(player, AssetType.COMMODITY);
            case 32 -> gui.openCategory(player, AssetType.ETF);
            case 38 -> gui.openPortfolio(player);
            case 40 -> gui.openLeaderboard(player);
        }
    }

    // ==================== PORTFOLIO KAUF ====================

    private void handleUnlock(Player player, int slot, InvestGUI gui) {
        int tier = switch (slot) {
            case 20 -> 1;
            case 22 -> 2;
            case 24 -> 3;
            default -> -1;
        };

        if (tier == -1) {
            if (slot == 49) player.closeInventory();
            return;
        }

        var result = plugin.getInvestmentManager().purchasePortfolio(player.getUniqueId(), tier);
        player.sendMessage(prefix + result.getMessage());

        if (result == InvestmentManager.UnlockResult.SUCCESS) {
            Portfolio p = plugin.getInvestmentManager().getOrCreatePortfolio(player.getUniqueId());
            player.sendMessage(prefix + "§a§l " + p.getTierName() + " §a§lPortfolio aktiviert!");
            player.sendMessage(prefix + "§7Max. Positionen: §f" + p.getMaxPositions());
            gui.openMainMenu(player);
        } else {
            gui.openUnlockGUI(player);
        }
    }

    // ==================== KATEGORIE ====================

    private void handleCategory(Player player, int slot, String data, InvestGUI gui) {
        if (slot == 31) {
            gui.openMainMenu(player);
            return;
        }

        if (slot >= 10 && slot < 27 && slot % 9 != 0 && slot % 9 != 8) {
            AssetType type;
            try {
                type = AssetType.valueOf(data);
            } catch (Exception e) {
                return;
            }

            java.util.List<Asset> catAssets = plugin.getInvestmentManager().getAssetsByType(type);
            int checkSlot = 10;
            for (Asset asset : catAssets) {
                if (checkSlot % 9 == 0) checkSlot++;
                if (checkSlot % 9 == 8) checkSlot += 2;
                if (checkSlot == slot) {
                    if (plugin.getBedrockManager().isBedrockPlayer(player)) {
                        plugin.getBedrockManager().openAssetDetail(player, asset);
                    } else {
                        gui.openAssetDetail(player, asset);
                    }
                    return;
                }
                checkSlot++;
            }
        }
    }

    // ==================== ASSET DETAIL ====================

    private void handleAssetDetail(Player player, int slot, String ticker, InventoryClickEvent event, InvestGUI gui) {
        Asset asset = findAssetByTicker(ticker);
        if (asset == null) return;

        // KAUF (Slots 29-33)
        double minI = plugin.getInvestmentManager().getMinInvestment();
        double[] amounts = {minI, minI * 5, minI * 10, minI * 50, minI * 100};

        if (slot >= 29 && slot <= 33) {
            int index = slot - 29;
            double amount = amounts[index];

            if (plugin.getBedrockManager().isBedrockPlayer(player)) {
                plugin.getBedrockManager().openBuyForm(player, asset);
                return;
            }

            TradeResult result = plugin.getInvestmentManager().buy(player.getUniqueId(), asset.getId(), amount);
            player.sendMessage(prefix + result.getMessage());
            if (result == TradeResult.SUCCESS) {
                player.sendMessage(prefix + "§a" + String.format("%.4f", amount / asset.getCurrentPrice()) +
                    " Anteile von §f" + asset.getName() + " §agekauft!");
            }
            gui.openAssetDetail(player, asset);
            return;
        }

        // VERKAUF (Slots 38-41)
        Portfolio portfolio = plugin.getInvestmentManager().getOrCreatePortfolio(player.getUniqueId());
        Investment inv = portfolio.getInvestment(asset.getId());

        if (inv != null && !inv.isEmpty()) {
            double sellPercent = switch (slot) {
                case 38 -> 0.25;
                case 39 -> 0.50;
                case 40 -> 0.75;
                case 41 -> 1.0;
                default -> -1;
            };

            if (sellPercent > 0) {
                if (plugin.getBedrockManager().isBedrockPlayer(player)) {
                    plugin.getBedrockManager().openSellForm(player, asset);
                    return;
                }

                double valueBefore = inv.getCurrentValue(asset.getCurrentPrice());
                TradeResult result = plugin.getInvestmentManager().sell(player.getUniqueId(), asset.getId(), sellPercent);
                player.sendMessage(prefix + result.getMessage());
                if (result == TradeResult.SUCCESS) {
                    double sold = valueBefore * sellPercent;
                    double fee = sold * (plugin.getInvestmentManager().getTransactionFeePercent() / 100.0);
                    player.sendMessage(prefix + "§c" + String.format("%.0f%%", sellPercent * 100) +
                        " verkauft für §f" + plugin.getEcoMoneyHook().formatMoney(sold - fee));
                }
                gui.openAssetDetail(player, asset);
                return;
            }

            // Einstellungen
            if (slot == 42) {
                gui.openSettingsGUI(player, asset);
                return;
            }
        }

        // Zurück
        if (slot == 49) {
            gui.openCategory(player, asset.getType());
        }
    }

    // ==================== PORTFOLIO ====================

    private void handlePortfolio(Player player, int slot, InvestGUI gui) {
        if (slot == 49) {
            gui.openMainMenu(player);
            return;
        }

        if (slot == 22) {
            Portfolio p = plugin.getInvestmentManager().getOrCreatePortfolio(player.getUniqueId());
            if (p.getPositionCount() == 0) {
                gui.openMainMenu(player);
                return;
            }
        }

        if (slot >= 10 && slot < 45 && slot % 9 != 0 && slot % 9 != 8) {
            Portfolio portfolio = plugin.getInvestmentManager().getOrCreatePortfolio(player.getUniqueId());
            Map<String, Asset> assets = plugin.getInvestmentManager().getAssets();

            int checkSlot = 10;
            for (Investment inv : portfolio.getInvestments()) {
                if (inv.isEmpty()) continue;
                if (checkSlot % 9 == 0) checkSlot++;
                if (checkSlot % 9 == 8) checkSlot += 2;
                if (checkSlot == slot) {
                    Asset asset = assets.get(inv.getAssetId());
                    if (asset != null) gui.openAssetDetail(player, asset);
                    return;
                }
                checkSlot++;
            }
        }
    }

    // ==================== SETTINGS ====================

    private void handleSettings(Player player, int slot, String ticker, InventoryClickEvent event, InvestGUI gui) {
        Asset asset = findAssetByTicker(ticker);
        if (asset == null) return;

        Portfolio portfolio = plugin.getInvestmentManager().getOrCreatePortfolio(player.getUniqueId());
        Investment inv = portfolio.getInvestment(asset.getId());
        if (inv == null) return;

        switch (slot) {
            case 11 -> {
                inv.setAutoReinvest(!inv.isAutoReinvest());
                player.sendMessage(prefix + "§eAuto-Reinvest: " + (inv.isAutoReinvest() ? "§aAKTIV" : "§cINAKTIV"));
                plugin.getInvestmentManager().savePortfolio(player.getUniqueId());
                gui.openSettingsGUI(player, asset);
            }
            case 13 -> {
                if (event.isShiftClick()) {
                    inv.setStopLossPercent(-1);
                    player.sendMessage(prefix + "§cStop-Loss deaktiviert.");
                } else if (event.isLeftClick()) {
                    double newVal = Math.max(5, inv.getStopLossPercent() - 5);
                    if (inv.getStopLossPercent() < 0) newVal = 10;
                    inv.setStopLossPercent(newVal);
                    player.sendMessage(prefix + "§cStop-Loss: -" + newVal + "%");
                } else {
                    double newVal = Math.min(50, inv.getStopLossPercent() + 5);
                    if (inv.getStopLossPercent() < 0) newVal = 10;
                    inv.setStopLossPercent(newVal);
                    player.sendMessage(prefix + "§cStop-Loss: -" + newVal + "%");
                }
                plugin.getInvestmentManager().savePortfolio(player.getUniqueId());
                gui.openSettingsGUI(player, asset);
            }
            case 15 -> {
                if (event.isShiftClick()) {
                    inv.setTakeProfitPercent(-1);
                    player.sendMessage(prefix + "§aTake-Profit deaktiviert.");
                } else if (event.isLeftClick()) {
                    double newVal = Math.max(5, inv.getTakeProfitPercent() - 5);
                    if (inv.getTakeProfitPercent() < 0) newVal = 10;
                    inv.setTakeProfitPercent(newVal);
                    player.sendMessage(prefix + "§aTake-Profit: +" + newVal + "%");
                } else {
                    double newVal = Math.min(200, inv.getTakeProfitPercent() + 5);
                    if (inv.getTakeProfitPercent() < 0) newVal = 10;
                    inv.setTakeProfitPercent(newVal);
                    player.sendMessage(prefix + "§aTake-Profit: +" + newVal + "%");
                }
                plugin.getInvestmentManager().savePortfolio(player.getUniqueId());
                gui.openSettingsGUI(player, asset);
            }
            case 22 -> gui.openAssetDetail(player, asset);
        }
    }

    // ==================== ANTI-EXPLOIT ====================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (getInvestHolder(event) != null) {
            event.setCancelled(true);
        }
    }

    // ==================== HELPER ====================

    /**
     * Robuste Holder-Erkennung für Paper 1.21.4+
     * Paper kann getHolder() mit Snapshot wrappen - getHolder(false) gibt den originalen Holder
     */
    private static InvestHolder getInvestHolder(InventoryEvent event) {
        var topInv = event.getView().getTopInventory();
        // Paper-spezifisch: getHolder(false) gibt den originalen Holder ohne Snapshot
        try {
            if (topInv.getHolder(false) instanceof InvestHolder h) return h;
        } catch (Exception ignored) {
            // Fallback für ältere API-Versionen
        }
        if (topInv.getHolder() instanceof InvestHolder h) return h;
        return null;
    }

    private Asset findAssetByTicker(String ticker) {
        for (Asset asset : plugin.getInvestmentManager().getAssets().values()) {
            if (asset.getTicker().equals(ticker)) return asset;
        }
        return null;
    }
}
