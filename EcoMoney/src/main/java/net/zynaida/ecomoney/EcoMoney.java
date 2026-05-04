package net.zynaida.ecomoney;

import net.zynaida.ecomoney.api.EcoMoneyAPI;
import net.zynaida.ecomoney.commands.BalanceCommand;
import net.zynaida.ecomoney.commands.EcoAdminCommand;
import net.zynaida.ecomoney.commands.PayCommand;
import net.zynaida.ecomoney.commands.BalanceTopCommand;
import net.zynaida.ecomoney.data.BalanceSyncTask;
import net.zynaida.ecomoney.data.DataManager;
import net.zynaida.ecomoney.data.MySQLManager;
import net.zynaida.ecomoney.data.NotificationSyncTask;
import net.zynaida.ecomoney.data.YamlManager;
import net.zynaida.ecomoney.listeners.PlayerListener;
import net.zynaida.ecomoney.placeholders.EcoMoneyPlaceholders;
import net.zynaida.ecomoney.vault.VaultHook;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class EcoMoney extends JavaPlugin {

    private static EcoMoney instance;
    private DataManager dataManager;
    private EcoMoneyAPI api;
    private VaultHook vaultHook;
    private boolean vaultEnabled = false;
    private boolean placeholderApiEnabled = false;

    @Override
    public void onEnable() {
        instance = this;
        
        // Konfiguration speichern/laden
        saveDefaultConfig();
        
        // Datenmanager initialisieren
        initializeDataManager();
        
        // API initialisieren
        api = new EcoMoneyAPI(this);
        
        // Vault Hook
        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            vaultHook = new VaultHook(this);
            vaultEnabled = vaultHook.hook();
            if (vaultEnabled) {
                getLogger().info("Vault erfolgreich eingebunden!");
            } else {
                getLogger().warning("Vault konnte nicht eingebunden werden!");
            }
        } else {
            getLogger().info("Vault nicht gefunden - Economy läuft standalone.");
        }
        
        // PlaceholderAPI Hook
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new EcoMoneyPlaceholders(this).register();
            placeholderApiEnabled = true;
            getLogger().info("PlaceholderAPI erfolgreich eingebunden!");
        } else {
            getLogger().info("PlaceholderAPI nicht gefunden - Placeholder deaktiviert.");
        }
        
        // Bedrock/Floodgate Check
        if (getServer().getPluginManager().getPlugin("floodgate") != null) {
            getLogger().info("Floodgate erkannt - Bedrock-Spieler werden unterstützt!");
        }
        
        // Commands registrieren
        registerCommands();
        
        // Listener registrieren
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        
        getLogger().info("EcoMoney wurde erfolgreich aktiviert!");
        getLogger().info("Speichermethode: " + (dataManager instanceof MySQLManager ? "MySQL" : "YAML"));
    }

    @Override
    public void onDisable() {
        // Vault unhook
        if (vaultHook != null && vaultEnabled) {
            vaultHook.unhook();
        }
        
        // Daten speichern
        if (dataManager != null) {
            dataManager.saveAll();
            dataManager.shutdown();
        }
        
        getLogger().info("EcoMoney wurde deaktiviert!");
    }

    private void initializeDataManager() {
        String storageType = getConfig().getString("storage.type", "yaml").toLowerCase();
        
        if (storageType.equals("mysql")) {
            String host = getConfig().getString("storage.mysql.host", "localhost");
            int port = getConfig().getInt("storage.mysql.port", 3306);
            String database = getConfig().getString("storage.mysql.database", "ecomoney");
            String username = getConfig().getString("storage.mysql.username", "root");
            String password = getConfig().getString("storage.mysql.password", "");
            boolean useSSL = getConfig().getBoolean("storage.mysql.useSSL", false);
            
            MySQLManager mysqlManager = new MySQLManager(this, host, port, database, username, password, useSSL);
            if (mysqlManager.connect()) {
                dataManager = mysqlManager;
                getLogger().info("MySQL-Verbindung erfolgreich hergestellt!");
            } else {
                getLogger().severe("MySQL-Verbindung fehlgeschlagen! Wechsle zu YAML...");
                dataManager = new YamlManager(this);
            }
        } else {
            dataManager = new YamlManager(this);
        }
        
        dataManager.initialize();

        // Cross-Server Sync starten (nur MySQL)
        if (dataManager instanceof MySQLManager) {
            boolean syncEnabled = getConfig().getBoolean("storage.sync.enabled", false);
            if (syncEnabled) {
                int intervalSeconds = getConfig().getInt("storage.sync.interval", 3);
                long intervalTicks = intervalSeconds * 20L;
                new BalanceSyncTask(this, (MySQLManager) dataManager)
                    .runTaskTimerAsynchronously(this, intervalTicks, intervalTicks);
                new NotificationSyncTask(this, (MySQLManager) dataManager)
                    .runTaskTimerAsynchronously(this, intervalTicks, intervalTicks);
                getLogger().info("Cross-Server Sync aktiviert (Intervall: " + intervalSeconds + "s)");
            }
        }
    }

    private void registerCommands() {
        getCommand("balance").setExecutor(new BalanceCommand(this));
        getCommand("pay").setExecutor(new PayCommand(this));
        getCommand("ecoadmin").setExecutor(new EcoAdminCommand(this));
        getCommand("balancetop").setExecutor(new BalanceTopCommand(this));
    }

    public static EcoMoney getInstance() {
        return instance;
    }

    public DataManager getDataManager() {
        return dataManager;
    }

    public EcoMoneyAPI getAPI() {
        return api;
    }

    public boolean isVaultEnabled() {
        return vaultEnabled;
    }

    public boolean isPlaceholderApiEnabled() {
        return placeholderApiEnabled;
    }
}
