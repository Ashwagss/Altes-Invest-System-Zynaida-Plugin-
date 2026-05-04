package net.zynaida.ecomoney.vault;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.zynaida.ecomoney.EcoMoney;
import net.zynaida.ecomoney.api.events.TransactionResult;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.ServicePriority;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * Vault Economy Provider für EcoMoney
 */
public class VaultHook implements Economy {

    private final EcoMoney plugin;

    public VaultHook(EcoMoney plugin) {
        this.plugin = plugin;
    }

    public boolean hook() {
        try {
            Bukkit.getServicesManager().register(Economy.class, this, plugin, ServicePriority.Highest);
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Fehler beim Registrieren der Vault Economy: " + e.getMessage());
            return false;
        }
    }

    public void unhook() {
        Bukkit.getServicesManager().unregister(Economy.class, this);
    }

    @Override
    public boolean isEnabled() {
        return plugin.isEnabled();
    }

    @Override
    public String getName() {
        return "EcoMoney";
    }

    @Override
    public boolean hasBankSupport() {
        return false; // Keine Bank-Unterstützung
    }

    @Override
    public int fractionalDigits() {
        return 2;
    }

    @Override
    public String format(double amount) {
        return plugin.getAPI().formatMoney(BigDecimal.valueOf(amount));
    }

    @Override
    public String currencyNamePlural() {
        return plugin.getAPI().getCurrencyName(true);
    }

    @Override
    public String currencyNameSingular() {
        return plugin.getAPI().getCurrencyName(false);
    }

    // ==================== ACCOUNT METHODS ====================

    @Override
    public boolean hasAccount(String playerName) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
        return hasAccount(player);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return plugin.getAPI().hasAccount(player);
    }

    @Override
    public boolean hasAccount(String playerName, String worldName) {
        return hasAccount(playerName); // Keine World-spezifischen Konten
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }

    @Override
    public boolean createPlayerAccount(String playerName) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
        return createPlayerAccount(player);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        return plugin.getAPI().createAccount(player);
    }

    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        return createPlayerAccount(playerName);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return createPlayerAccount(player);
    }

    // ==================== BALANCE METHODS ====================

    @Override
    public double getBalance(String playerName) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
        return getBalance(player);
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return plugin.getAPI().getBalance(player).doubleValue();
    }

    @Override
    public double getBalance(String playerName, String world) {
        return getBalance(playerName);
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }

    @Override
    public boolean has(String playerName, double amount) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
        return has(player, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return plugin.getAPI().has(player, BigDecimal.valueOf(amount));
    }

    @Override
    public boolean has(String playerName, String worldName, double amount) {
        return has(playerName, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    // ==================== WITHDRAW METHODS ====================

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        // Validierung
        if (amount < 0) {
            return new EconomyResponse(0, getBalance(player), 
                EconomyResponse.ResponseType.FAILURE, "Betrag kann nicht negativ sein");
        }
        
        TransactionResult result = plugin.getAPI().withdraw(player, BigDecimal.valueOf(amount), "Vault");
        
        if (result.isSuccess()) {
            return new EconomyResponse(amount, getBalance(player), 
                EconomyResponse.ResponseType.SUCCESS, null);
        } else {
            return new EconomyResponse(0, getBalance(player), 
                EconomyResponse.ResponseType.FAILURE, result.getMessage());
        }
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    // ==================== DEPOSIT METHODS ====================

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
        return depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        // Validierung
        if (amount < 0) {
            return new EconomyResponse(0, getBalance(player), 
                EconomyResponse.ResponseType.FAILURE, "Betrag kann nicht negativ sein");
        }
        
        // Konto erstellen falls nicht vorhanden
        if (!hasAccount(player)) {
            createPlayerAccount(player);
        }
        
        TransactionResult result = plugin.getAPI().deposit(player, BigDecimal.valueOf(amount), "Vault");
        
        if (result.isSuccess()) {
            return new EconomyResponse(amount, getBalance(player), 
                EconomyResponse.ResponseType.SUCCESS, null);
        } else {
            return new EconomyResponse(0, getBalance(player), 
                EconomyResponse.ResponseType.FAILURE, result.getMessage());
        }
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    // ==================== BANK METHODS (NOT SUPPORTED) ====================

    @Override
    public EconomyResponse createBank(String name, String player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banken werden nicht unterstützt");
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banken werden nicht unterstützt");
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banken werden nicht unterstützt");
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banken werden nicht unterstützt");
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banken werden nicht unterstützt");
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banken werden nicht unterstützt");
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banken werden nicht unterstützt");
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banken werden nicht unterstützt");
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banken werden nicht unterstützt");
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banken werden nicht unterstützt");
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banken werden nicht unterstützt");
    }

    @Override
    public List<String> getBanks() {
        return Collections.emptyList();
    }
}
