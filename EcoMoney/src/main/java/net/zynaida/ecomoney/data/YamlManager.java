package net.zynaida.ecomoney.data;

import net.zynaida.ecomoney.EcoMoney;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * YAML-basierte Datenspeicherung
 */
public class YamlManager implements DataManager {

    private final EcoMoney plugin;
    private File dataFile;
    private FileConfiguration dataConfig;
    
    // Cache für schnelleren Zugriff
    private final Map<UUID, BigDecimal> balanceCache = new ConcurrentHashMap<>();
    private final Map<UUID, String> nameCache = new ConcurrentHashMap<>();
    
    // Auto-Save Intervall
    private int saveTaskId = -1;
    private boolean dirty = false;

    public YamlManager(EcoMoney plugin) {
        this.plugin = plugin;
    }

    @Override
    public void initialize() {
        dataFile = new File(plugin.getDataFolder(), "data.yml");
        
        if (!dataFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Konnte data.yml nicht erstellen!", e);
            }
        }
        
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        
        // Daten in Cache laden
        loadCache();
        
        // Auto-Save Task starten
        int saveInterval = plugin.getConfig().getInt("storage.auto-save-interval", 300) * 20; // in Ticks
        saveTaskId = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (dirty) {
                saveAll();
            }
        }, saveInterval, saveInterval).getTaskId();
    }

    private void loadCache() {
        if (dataConfig.contains("players")) {
            for (String uuidString : dataConfig.getConfigurationSection("players").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidString);
                    double balance = dataConfig.getDouble("players." + uuidString + ".balance", 0.0);
                    String name = dataConfig.getString("players." + uuidString + ".name", "Unknown");
                    
                    balanceCache.put(uuid, BigDecimal.valueOf(balance));
                    nameCache.put(uuid, name);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Ungültige UUID in data.yml: " + uuidString);
                }
            }
        }
        plugin.getLogger().info("Geladen: " + balanceCache.size() + " Spielerkonten");
    }

    @Override
    public void shutdown() {
        if (saveTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(saveTaskId);
        }
        saveAll();
    }

    @Override
    public synchronized void saveAll() {
        if (dataConfig == null || dataFile == null) return;
        
        for (Map.Entry<UUID, BigDecimal> entry : balanceCache.entrySet()) {
            String path = "players." + entry.getKey().toString();
            dataConfig.set(path + ".balance", entry.getValue().doubleValue());
            
            String name = nameCache.get(entry.getKey());
            if (name != null) {
                dataConfig.set(path + ".name", name);
            }
        }
        
        try {
            dataConfig.save(dataFile);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Konnte data.yml nicht speichern!", e);
        }
    }

    @Override
    public boolean hasAccount(UUID uuid) {
        return balanceCache.containsKey(uuid);
    }

    @Override
    public boolean createAccount(UUID uuid, BigDecimal startBalance) {
        if (hasAccount(uuid)) return true;
        
        balanceCache.put(uuid, startBalance);
        dirty = true;
        return true;
    }

    @Override
    public BigDecimal getBalance(UUID uuid) {
        return balanceCache.getOrDefault(uuid, BigDecimal.ZERO);
    }

    @Override
    public boolean setBalance(UUID uuid, BigDecimal amount) {
        // Sicherheitscheck
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            plugin.getLogger().warning("Versuch, negatives Guthaben zu setzen für: " + uuid);
            return false;
        }
        
        balanceCache.put(uuid, amount);
        dirty = true;
        return true;
    }

    @Override
    public LinkedHashMap<UUID, BigDecimal> getTopBalances(int limit) {
        return balanceCache.entrySet().stream()
            .sorted(Map.Entry.<UUID, BigDecimal>comparingByValue().reversed())
            .limit(limit)
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (e1, e2) -> e1,
                LinkedHashMap::new
            ));
    }

    @Override
    public void updatePlayerName(UUID uuid, String name) {
        nameCache.put(uuid, name);
        dirty = true;
    }

    @Override
    public String getPlayerName(UUID uuid) {
        return nameCache.getOrDefault(uuid, "Unbekannt");
    }
}
