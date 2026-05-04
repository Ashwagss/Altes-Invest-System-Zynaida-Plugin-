package net.zynaida.invest;

import net.zynaida.invest.bedrock.BedrockManager;
import net.zynaida.invest.commands.InvestCommand;
import net.zynaida.invest.commands.InvestAdminCommand;
import net.zynaida.invest.data.DataManager;
import net.zynaida.invest.data.MySQLManager;
import net.zynaida.invest.data.YamlManager;
import net.zynaida.invest.economy.EcoMoneyHook;
import net.zynaida.invest.economy.ShardsHook;
import net.zynaida.invest.invest.InvestmentManager;
import net.zynaida.invest.invest.MarketSimulator;
import net.zynaida.invest.listeners.GUIListener;
import net.zynaida.invest.listeners.PlayerListener;
import net.zynaida.invest.npc.NPCListener;
import net.zynaida.invest.npc.NPCManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class ZynaidaInvest extends JavaPlugin {

    private static ZynaidaInvest instance;
    private DataManager dataManager;
    private EcoMoneyHook ecoMoneyHook;
    private ShardsHook shardsHook;
    private InvestmentManager investmentManager;
    private MarketSimulator marketSimulator;
    private BedrockManager bedrockManager;
    private NPCManager npcManager;
    private boolean floodgateEnabled = false;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        floodgateEnabled = Bukkit.getPluginManager().getPlugin("floodgate") != null;
        if (floodgateEnabled) getLogger().info("§aFloodgate erkannt - Bedrock Forms aktiviert!");

        ecoMoneyHook = new EcoMoneyHook(this);
        if (!ecoMoneyHook.setup()) {
            getLogger().severe("§cEcoMoney nicht gefunden! Plugin wird deaktiviert.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("§aEcoMoney erfolgreich verbunden!");

        shardsHook = new ShardsHook(this);
        if (shardsHook.setup()) {
            getLogger().info("§aShards (lobby_players) erfolgreich verbunden!");
        } else {
            getLogger().warning("§eShards nicht verfügbar - nur Geld-Käufe möglich.");
        }

        initializeDataManager();

        // Cross-Server Validierung
        boolean crossServer = getConfig().getBoolean("cross-server.enabled", false);
        if (crossServer && !(dataManager instanceof MySQLManager)) {
            getLogger().severe("§cCross-Server-Modus erfordert MySQL! Bitte storage.type auf 'mysql' setzen.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        investmentManager = new InvestmentManager(this);
        marketSimulator = new MarketSimulator(this);
        marketSimulator.start();
        bedrockManager = new BedrockManager(this);
        npcManager = new NPCManager(this);

        InvestCommand investCmd = new InvestCommand(this);
        getCommand("invest").setExecutor(investCmd);
        getCommand("invest").setTabCompleter(investCmd);
        InvestAdminCommand adminCmd = new InvestAdminCommand(this);
        getCommand("investadmin").setExecutor(adminCmd);
        getCommand("investadmin").setTabCompleter(adminCmd);

        getServer().getPluginManager().registerEvents(new GUIListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new NPCListener(this), this);

        getLogger().info("§a══════════════════════════════════════");
        getLogger().info("§a  ZynaidaInvest v" + getDescription().getVersion());
        getLogger().info("§a  Währungen: §fGeld" + (shardsHook.isEnabled() ? " §a+ §dShards" : ""));
        getLogger().info("§a══════════════════════════════════════");
    }

    @Override
    public void onDisable() {
        if (marketSimulator != null) marketSimulator.stop();
        if (investmentManager != null) investmentManager.saveAll();

        // Master: Market-State beim Shutdown sichern
        if (marketSimulator != null && marketSimulator.isCrossServerMaster()) {
            dataManager.saveMarketState(
                    marketSimulator.getMarketSentiment(),
                    marketSimulator.getLastDividendTime(),
                    marketSimulator.getServerId()
            );
        }

        if (dataManager != null) dataManager.close();
        if (shardsHook != null) shardsHook.close();
        if (npcManager != null) npcManager.shutdown();
    }

    private void initializeDataManager() {
        String type = getConfig().getString("storage.type", "yaml");
        dataManager = type.equalsIgnoreCase("mysql") ? new MySQLManager(this) : new YamlManager(this);
        dataManager.initialize();
    }

    public static ZynaidaInvest getInstance() { return instance; }
    public DataManager getDataManager() { return dataManager; }
    public EcoMoneyHook getEcoMoneyHook() { return ecoMoneyHook; }
    public ShardsHook getShardsHook() { return shardsHook; }
    public InvestmentManager getInvestmentManager() { return investmentManager; }
    public MarketSimulator getMarketSimulator() { return marketSimulator; }
    public BedrockManager getBedrockManager() { return bedrockManager; }
    public NPCManager getNpcManager() { return npcManager; }
    public boolean isFloodgateEnabled() { return floodgateEnabled; }
}
