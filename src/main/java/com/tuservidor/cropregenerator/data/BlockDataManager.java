package com.tuservidor.cropregenerator.data;

import com.tuservidor.cropregenerator.CropRegeneratorPlugin;
import com.tuservidor.cropregenerator.model.RegeneratorBlock;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Carga y guarda los RegeneratorBlocks en data/blocks.yml.
 * También indexa por UUID de isla (para borrado masivo con SuperiorSkyblock2).
 */
public class BlockDataManager {

    private final CropRegeneratorPlugin plugin;
    private final File dataFile;
    private YamlConfiguration dataConfig;

    // key (world,x,y,z) → bloque
    private final Map<String, RegeneratorBlock> blocksByKey = new HashMap<>();

    // islandId → lista de keys de bloques en esa isla
    private final Map<String, Set<String>> blocksByIsland = new HashMap<>();

    public BlockDataManager(CropRegeneratorPlugin plugin) {
        this.plugin   = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data/blocks.yml");
    }

    // ── Persistencia ────────────────────────────────────────

    public void loadAll() {
        if (!dataFile.exists()) return;
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        for (String key : dataConfig.getKeys(false)) {
            try {
                String worldName = dataConfig.getString(key + ".world");
                int bx = dataConfig.getInt(key + ".x");
                int by = dataConfig.getInt(key + ".y");
                int bz = dataConfig.getInt(key + ".z");
                UUID owner  = UUID.fromString(Objects.requireNonNull(dataConfig.getString(key + ".owner")));
                int level   = dataConfig.getInt(key + ".level", 1);
                String islandId = dataConfig.getString(key + ".islandId", "");

                World world = Bukkit.getWorld(worldName);
                if (world == null) continue;

                Location loc = new Location(world, bx, by, bz);
                RegeneratorBlock rb = new RegeneratorBlock(loc, owner, level);

                blocksByKey.put(rb.getKey(), rb);
                if (!islandId.isEmpty()) indexIsland(islandId, rb.getKey());

                // Restaurar holograma
                plugin.getHologramManager().spawnOrUpdate(rb);

            } catch (Exception e) {
                plugin.getLogger().warning("Error al cargar bloque " + key + ": " + e.getMessage());
            }
        }
        plugin.getLogger().info("Cargados " + blocksByKey.size() + " bloques regeneradores.");
    }

    public void saveAll() {
        dataConfig = new YamlConfiguration();
        for (RegeneratorBlock rb : blocksByKey.values()) {
            String key = rb.getKey();
            dataConfig.set(key + ".world", rb.getLocation().getWorld().getName());
            dataConfig.set(key + ".x",     rb.getLocation().getBlockX());
            dataConfig.set(key + ".y",     rb.getLocation().getBlockY());
            dataConfig.set(key + ".z",     rb.getLocation().getBlockZ());
            dataConfig.set(key + ".owner", rb.getOwnerUUID().toString());
            dataConfig.set(key + ".level", rb.getLevel());

            // Guardar islandId si existe
            String islandId = getIslandIdForKey(key);
            if (islandId != null) dataConfig.set(key + ".islandId", islandId);
        }
        try {
            dataFile.getParentFile().mkdirs();
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Error al guardar bloques: " + e.getMessage());
        }
    }

    // ── API pública ─────────────────────────────────────────

    public void addBlock(RegeneratorBlock rb, String islandId) {
        blocksByKey.put(rb.getKey(), rb);
        if (islandId != null && !islandId.isEmpty()) indexIsland(islandId, rb.getKey());
    }

    public void removeBlock(RegeneratorBlock rb) {
        blocksByKey.remove(rb.getKey());
        // Limpiar índice isla
        blocksByIsland.values().forEach(set -> set.remove(rb.getKey()));
    }

    public RegeneratorBlock getBlock(Location loc) {
        String key = loc.getWorld().getName() + ","
                + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
        return blocksByKey.get(key);
    }

    public boolean isRegeneratorBlock(Location loc) {
        return getBlock(loc) != null;
    }

    public Collection<RegeneratorBlock> getAllBlocks() {
        return blocksByKey.values();
    }

    /** Cuenta cuántos bloques tiene el jugador/isla. */
    public int countBlocksForIsland(String islandId) {
        Set<String> keys = blocksByIsland.get(islandId);
        return keys == null ? 0 : keys.size();
    }

    /** Elimina todos los bloques de una isla (cuando la isla es borrada). */
    public List<RegeneratorBlock> removeAllForIsland(String islandId) {
        Set<String> keys = blocksByIsland.remove(islandId);
        List<RegeneratorBlock> removed = new ArrayList<>();
        if (keys == null) return removed;

        for (String key : keys) {
            RegeneratorBlock rb = blocksByKey.remove(key);
            if (rb != null) removed.add(rb);
        }
        return removed;
    }

    // ── Helpers ─────────────────────────────────────────────

    private void indexIsland(String islandId, String blockKey) {
        blocksByIsland.computeIfAbsent(islandId, k -> new HashSet<>()).add(blockKey);
    }

    public String getIslandIdForBlock(com.tuservidor.cropregenerator.model.RegeneratorBlock rb) {
        return getIslandIdForKey(rb.getKey());
    }

    public String getIslandIdForKey(String key) {
        for (Map.Entry<String, Set<String>> entry : blocksByIsland.entrySet()) {
            if (entry.getValue().contains(key)) return entry.getKey();
        }
        return null;
    }
}
