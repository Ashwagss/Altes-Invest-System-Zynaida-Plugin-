package net.zynaida.invest.npc;

import net.zynaida.invest.ZynaidaInvest;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * NPC Manager - Spawnt und verwaltet den Investment-Broker NPC
 * Nutzt Villager mit PersistentDataContainer (überlebt Restarts)
 * Hologramm über dem Kopf mit ArmorStands
 */
public class NPCManager {

    private final ZynaidaInvest plugin;
    private final NamespacedKey npcKey;
    private File npcFile;
    private FileConfiguration npcConfig;

    private final List<Entity> hologramLines = new ArrayList<>();
    private Villager npcEntity;
    private Location npcLocation;

    // Hologramm-Texte
    private static final String[] HOLOGRAM_LINES = {
            "§8§l« §6§lBÖRSEN-MAKLER §8§l»",
            "§7Rechtsklick zum Handeln",
            "§e§l/invest"
    };

    public NPCManager(ZynaidaInvest plugin) {
        this.plugin = plugin;
        this.npcKey = new NamespacedKey(plugin, "invest_npc");
        loadNPCConfig();

        // Verzögert laden (warten auf Welten)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            cleanupOldNPCs();
            loadAndSpawn();
        }, 40L);
    }

    // ==================== CONFIG ====================

    private void loadNPCConfig() {
        npcFile = new File(plugin.getDataFolder(), "npc.yml");
        if (!npcFile.exists()) {
            try {
                npcFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        npcConfig = YamlConfiguration.loadConfiguration(npcFile);
    }

    private void saveNPCConfig() {
        try {
            npcConfig.save(npcFile);
        } catch (IOException e) {
            plugin.getLogger().severe("NPC Config speichern fehlgeschlagen: " + e.getMessage());
        }
    }

    // ==================== SPAWN / DESPAWN ====================

    /**
     * Entfernt ALLE alten Investment NPCs beim Start
     */
    private void cleanupOldNPCs() {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getPersistentDataContainer().has(npcKey, PersistentDataType.STRING)) {
                    entity.remove();
                    removed++;
                }
            }
        }
        if (removed > 0) {
            plugin.getLogger().info("§e" + removed + " alte Investment-NPCs entfernt.");
        }
    }

    /**
     * Lädt NPC-Position aus Config und spawnt
     */
    private void loadAndSpawn() {
        if (!npcConfig.getBoolean("npc.set", false)) {
            plugin.getLogger().info("§eKein NPC gesetzt. Nutze /investadmin setnpc");
            return;
        }

        String worldName = npcConfig.getString("npc.world");
        if (worldName == null) return;

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("NPC Welt '" + worldName + "' nicht gefunden!");
            return;
        }

        double x = npcConfig.getDouble("npc.x");
        double y = npcConfig.getDouble("npc.y");
        double z = npcConfig.getDouble("npc.z");
        float yaw = (float) npcConfig.getDouble("npc.yaw", 0);
        float pitch = (float) npcConfig.getDouble("npc.pitch", 0);

        npcLocation = new Location(world, x, y, z, yaw, pitch);
        spawnNPC(npcLocation);
    }

    /**
     * Spawnt den NPC an einer Location
     */
    public void spawnNPC(Location location) {
        despawnNPC(); // Alte entfernen

        this.npcLocation = location;

        // Villager spawnen
        npcEntity = (Villager) location.getWorld().spawnEntity(location, EntityType.VILLAGER);
        npcEntity.setCustomName("§6§lBörsen-Makler");
        npcEntity.setCustomNameVisible(false); // Hologramm zeigt den Namen
        npcEntity.setProfession(Villager.Profession.LIBRARIAN);
        npcEntity.setVillagerType(Villager.Type.PLAINS);
        npcEntity.setAI(false);
        npcEntity.setInvulnerable(true);
        npcEntity.setSilent(true);
        npcEntity.setCollidable(false);
        npcEntity.setGravity(false);
        npcEntity.setRemoveWhenFarAway(false);
        npcEntity.setPersistent(true);

        // PersistentDataContainer Tag
        npcEntity.getPersistentDataContainer().set(npcKey, PersistentDataType.STRING, "invest_broker");

        // Hologramm über dem NPC
        spawnHologram(location);

        plugin.getLogger().info("§aInvestment-NPC gespawnt bei " +
                String.format("%.1f, %.1f, %.1f", location.getX(), location.getY(), location.getZ()));
    }

    /**
     * Hologramm-ArmorStands über dem NPC
     */
    private void spawnHologram(Location base) {
        hologramLines.clear();

        double startY = base.getY() + 2.5; // Über dem Villager

        for (int i = 0; i < HOLOGRAM_LINES.length; i++) {
            Location holoLoc = base.clone();
            holoLoc.setY(startY - (i * 0.3));

            ArmorStand stand = (ArmorStand) base.getWorld().spawnEntity(holoLoc, EntityType.ARMOR_STAND);
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setMarker(true);
            stand.setSmall(true);
            stand.setCustomName(HOLOGRAM_LINES[i]);
            stand.setCustomNameVisible(true);
            stand.setInvulnerable(true);
            stand.setRemoveWhenFarAway(false);
            stand.setPersistent(true);
            stand.getPersistentDataContainer().set(npcKey, PersistentDataType.STRING, "invest_hologram");

            hologramLines.add(stand);
        }
    }

    /**
     * Entfernt den NPC und das Hologramm
     */
    public void despawnNPC() {
        if (npcEntity != null && !npcEntity.isDead()) {
            npcEntity.remove();
            npcEntity = null;
        }
        for (Entity e : hologramLines) {
            if (e != null && !e.isDead()) e.remove();
        }
        hologramLines.clear();
    }

    // ==================== POSITION SETZEN ====================

    /**
     * Setzt den NPC an eine neue Position (Admin-Command)
     */
    public void setNPCLocation(Location location) {
        npcConfig.set("npc.set", true);
        npcConfig.set("npc.world", location.getWorld().getName());
        npcConfig.set("npc.x", location.getX());
        npcConfig.set("npc.y", location.getY());
        npcConfig.set("npc.z", location.getZ());
        npcConfig.set("npc.yaw", location.getYaw());
        npcConfig.set("npc.pitch", location.getPitch());
        saveNPCConfig();

        spawnNPC(location);
    }

    /**
     * Entfernt den NPC komplett
     */
    public void removeNPC() {
        despawnNPC();
        npcConfig.set("npc.set", false);
        saveNPCConfig();
        npcLocation = null;
    }

    // ==================== CHECKS ====================

    /**
     * Prüft ob eine Entity unser Investment-NPC ist
     */
    public boolean isInvestNPC(Entity entity) {
        if (entity == null) return false;
        String data = entity.getPersistentDataContainer().get(npcKey, PersistentDataType.STRING);
        return "invest_broker".equals(data);
    }

    public Villager getNpcEntity() {
        return npcEntity;
    }

    public Location getNpcLocation() {
        return npcLocation;
    }

    public boolean isSpawned() {
        return npcEntity != null && !npcEntity.isDead();
    }

    /**
     * Shutdown: Cleanup
     */
    public void shutdown() {
        // NPCs bleiben persistent dank PersistentDataContainer
        // Beim nächsten Start werden alte entfernt und neu gespawnt
    }
}
