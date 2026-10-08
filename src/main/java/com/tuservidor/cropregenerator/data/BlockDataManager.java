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

/** Persistencia e índices O(1) de regeneradores. */
public class BlockDataManager {
    private final CropRegeneratorPlugin plugin;
    private final File dataFile;
    private final Map<String, RegeneratorBlock> blocksByKey = new HashMap<>();
    private final Map<String, Set<String>> blocksByIsland = new HashMap<>();
    private final Map<String, String> islandByBlockKey = new HashMap<>();
    private final Map<String, Set<String>> blocksByChunk = new HashMap<>();

    public BlockDataManager(CropRegeneratorPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data/blocks.yml");
    }

    public void loadAll() {
        blocksByKey.clear();
        blocksByIsland.clear();
        islandByBlockKey.clear();
        blocksByChunk.clear();
        if (!dataFile.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        for (String key : config.getKeys(false)) {
            try {
                String worldName = Objects.requireNonNull(config.getString(key + ".world"));
                World world = Bukkit.getWorld(worldName);
                if (world == null) continue;
                int x = config.getInt(key + ".x"), y = config.getInt(key + ".y"), z = config.getInt(key + ".z");
                UUID owner = UUID.fromString(Objects.requireNonNull(config.getString(key + ".owner")));
                int legacy = config.getInt(key + ".level", 1);
                int time = config.getInt(key + ".time-level", legacy);
                int radius = config.getInt(key + ".radius-level", legacy);
                int crops = config.getInt(key + ".crops-level", legacy);
                boolean particles = config.getBoolean(key + ".particles", false);
                long next = config.getLong(key + ".next-regen", 0L);
                if (next <= 0L) next = System.currentTimeMillis() + plugin.getUpgradeManager().getInterval(time) * 1000L;

                RegeneratorBlock rb = new RegeneratorBlock(new Location(world, x, y, z), owner,
                        time, radius, crops, particles, next);
                String islandId = config.getString(key + ".islandId", "");
                index(rb, islandId.isBlank() ? null : islandId);
            } catch (Exception e) {
                plugin.getLogger().warning("Error al cargar bloque " + key + ": " + e.getMessage());
            }
        }
        plugin.getLogger().info("Cargados " + blocksByKey.size() + " bloques regeneradores.");
    }

    public void saveAll() {
        YamlConfiguration config = new YamlConfiguration();
        for (RegeneratorBlock rb : blocksByKey.values()) {
            String key = rb.getKey();
            Location loc = rb.getLocation();

            if (loc.getWorld() == null) {
            plugin.getLogger().warning("[Data] Bloque con mundo descargado, se omite: " + key);
            continue;
        }

            config.set(key + ".world", loc.getWorld().getName());
            // ... el resto del método queda igual
            String key = rb.getKey();
            Location loc = rb.getLocation();
            config.set(key + ".world", loc.getWorld().getName());
            config.set(key + ".x", loc.getBlockX());
            config.set(key + ".y", loc.getBlockY());
            config.set(key + ".z", loc.getBlockZ());
            config.set(key + ".owner", rb.getOwnerUUID().toString());
            config.set(key + ".time-level", rb.getTimeLevel());
            config.set(key + ".radius-level", rb.getRadiusLevel());
            config.set(key + ".crops-level", rb.getCropsLevel());
            config.set(key + ".particles", rb.isParticlesEnabled());
            config.set(key + ".next-regen", rb.getNextRegenTimestamp());
            String islandId = islandByBlockKey.get(key);
            if (islandId != null) config.set(key + ".islandId", islandId);
        }
        try {
            dataFile.getParentFile().mkdirs();
            config.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Error al guardar bloques: " + e.getMessage());
        }
    }

    public void addBlock(RegeneratorBlock rb, String islandId) {
        index(rb, islandId);
    }

    private void index(RegeneratorBlock rb, String islandId) {
        String key = rb.getKey();
        blocksByKey.put(key, rb);
        String chunkKey = chunkKey(rb.getLocation());
        blocksByChunk.computeIfAbsent(chunkKey, k -> new HashSet<>()).add(key);
        if (islandId != null && !islandId.isBlank()) {
            blocksByIsland.computeIfAbsent(islandId, k -> new HashSet<>()).add(key);
            islandByBlockKey.put(key, islandId);
        }
    }

    public void removeBlock(RegeneratorBlock rb) {
        String key = rb.getKey();
        blocksByKey.remove(key);
        String island = islandByBlockKey.remove(key);
        if (island != null) {
            Set<String> set = blocksByIsland.get(island);
            if (set != null) { set.remove(key); if (set.isEmpty()) blocksByIsland.remove(island); }
        }
        String chunk = chunkKey(rb.getLocation());
        Set<String> set = blocksByChunk.get(chunk);
        if (set != null) { set.remove(key); if (set.isEmpty()) blocksByChunk.remove(chunk); }
    }

    public RegeneratorBlock getBlock(Location loc) {
        if (loc.getWorld() == null) return null;
        return blocksByKey.get(loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ());
    }

    public boolean isRegeneratorBlock(Location loc) { return getBlock(loc) != null; }
    public Collection<RegeneratorBlock> getAllBlocks() { return Collections.unmodifiableCollection(blocksByKey.values()); }

    public void spawnHologramsInLoadedChunks() {
        for (World world : Bukkit.getWorlds()) {
            for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
                for (RegeneratorBlock rb : getBlocksInChunk(chunk)) plugin.getHologramManager().spawnOrUpdate(rb);
            }
        }
    }

    public List<RegeneratorBlock> getBlocksInChunk(org.bukkit.Chunk chunk) {
        Set<String> keys = blocksByChunk.get(chunk.getWorld().getName() + "," + chunk.getX() + "," + chunk.getZ());
        if (keys == null || keys.isEmpty()) return List.of();
        List<RegeneratorBlock> result = new ArrayList<>(keys.size());
        for (String key : keys) { RegeneratorBlock rb = blocksByKey.get(key); if (rb != null) result.add(rb); }
        return result;
    }

    public int countBlocksForIsland(String islandId) {
        Set<String> keys = blocksByIsland.get(islandId);
        return keys == null ? 0 : keys.size();
    }

    public List<RegeneratorBlock> removeAllForIsland(String islandId) {
        Set<String> keys = blocksByIsland.remove(islandId);
        if (keys == null || keys.isEmpty()) return List.of();
        List<RegeneratorBlock> removed = new ArrayList<>(keys.size());
        for (String key : new ArrayList<>(keys)) {
            RegeneratorBlock rb = blocksByKey.get(key);
            if (rb != null) { removeBlock(rb); removed.add(rb); }
        }
        return removed;
    }

    public String getIslandIdForBlock(RegeneratorBlock rb) { return islandByBlockKey.get(rb.getKey()); }
    public String getIslandIdForKey(String key) { return islandByBlockKey.get(key); }

    private String chunkKey(Location loc) {
        return loc.getWorld().getName() + "," + (loc.getBlockX() >> 4) + "," + (loc.getBlockZ() >> 4);
    }
}
