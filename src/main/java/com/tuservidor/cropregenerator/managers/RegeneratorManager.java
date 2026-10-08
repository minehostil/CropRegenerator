package com.tuservidor.cropregenerator.managers;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.registry.state.IntegerProperty;
import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;
import com.tuservidor.cropregenerator.CropRegeneratorPlugin;
import com.tuservidor.cropregenerator.model.RegeneratorBlock;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Gestiona los timers y limita a una regeneración async por bloque. */
public class RegeneratorManager {

    private static final Map<String, Integer> ALL_CROPS = Map.ofEntries(
            Map.entry("wheat", 7),
            Map.entry("carrots", 7),
            Map.entry("potatoes", 7),
            Map.entry("beetroots", 3),
            Map.entry("nether_wart", 3),
            Map.entry("cocoa", 2),
            Map.entry("melon_stem", 7),
            Map.entry("pumpkin_stem", 7),
            Map.entry("sweet_berry_bush", 3),
            Map.entry("pitcher_crop", 4),
            Map.entry("torchflower_crop", 1));

    private final Map<String, Integer> cropMaxAge = new HashMap<>();
    private final Map<String, Boolean> enabledCrops = new HashMap<>();
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private final CropRegeneratorPlugin plugin;
    private BukkitTask globalTask;

    public RegeneratorManager(CropRegeneratorPlugin plugin) {
        this.plugin = plugin;
        loadCrops();
    }

    public void startAll() {
        if (globalTask != null) return;
        long updateTicks = Math.max(20L, plugin.getConfig().getLong("hologram.update-interval", 1L) * 20L);
        boolean updateCountdown = plugin.getConfig().getBoolean("hologram.update-countdown", true);
        boolean hasDynamic = updateCountdown && plugin.getConfig().getStringList("hologram.lines").stream().anyMatch(l -> l.contains("{next_regen}"));
        globalTask = new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                for (RegeneratorBlock rb : List.copyOf(plugin.getBlockDataManager().getAllBlocks())) {
                    Location loc = rb.getLocation();
                    if (loc.getWorld() == null || !loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) continue;
                    if (hasDynamic) plugin.getHologramManager().updateText(rb);
                    if (now >= rb.getNextRegenTimestamp() && inFlight.add(rb.getKey())) {
                        rb.setNextRegenTimestamp(now + plugin.getUpgradeManager().getInterval(rb.getTimeLevel()) * 1000L);
                        regenerateAsync(rb);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, updateTicks);
    }

    public void stopAll() {
        if (globalTask != null) { globalTask.cancel(); globalTask = null; }
    }

    public void pause() { stopAll(); }

    public void resume() {
        if (globalTask == null && !plugin.getServer().getOnlinePlayers().isEmpty()) startAll();
    }

    public void reload() {
        loadCrops();
        stopAll();
        if (!plugin.getServer().getOnlinePlayers().isEmpty()) startAll();
    }

    private void loadCrops() {
        cropMaxAge.clear();
        enabledCrops.clear();
        for (Map.Entry<String, Integer> entry : ALL_CROPS.entrySet()) {
            boolean enabled = plugin.getConfig().getBoolean("crops." + entry.getKey(), true);
            enabledCrops.put(entry.getKey(), enabled);
            if (enabled) cropMaxAge.put(entry.getKey(), entry.getValue());
        }
    }

    private void regenerateAsync(RegeneratorBlock rb) {
        Location center = rb.getLocation();
        if (center.getWorld() == null || !center.getWorld().isChunkLoaded(center.getBlockX() >> 4, center.getBlockZ() >> 4)) {
            inFlight.remove(rb.getKey());
            return;
        }
        int radius = plugin.getUpgradeManager().getRadius(rb.getRadiusLevel());
        Set<String> unlocked = new HashSet<>(plugin.getUpgradeManager().getUnlockedCrops(rb.getCropsLevel(), enabledCrops));
        if (unlocked.isEmpty()) {
            inFlight.remove(rb.getKey());
            return;
        }
        var world = BukkitAdapter.adapt(center.getWorld());
        BlockVector3 min = BlockVector3.at(center.getBlockX() - radius, center.getBlockY() - radius, center.getBlockZ() - radius);
        BlockVector3 max = BlockVector3.at(center.getBlockX() + radius, center.getBlockY() + radius, center.getBlockZ() + radius);
        CuboidRegion region = new CuboidRegion(world, min, max);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (EditSession session = WorldEdit.getInstance().newEditSessionBuilder().world(world).fastMode(true).limitUnlimited().build()) {
                int grown = 0;
                for (BlockVector3 pos : region) {
                    BlockState state = session.getBlock(pos);
                    BlockType type = state.getBlockType();
                    if (type == null) continue;
                    String id = type.id();
                    if (id.startsWith("minecraft:")) id = id.substring(10);
                    Integer maxAge = cropMaxAge.get(id);
                    if (maxAge == null || !unlocked.contains(id)) continue;
                    Property<?> raw = type.getProperty("age");
                    if (!(raw instanceof IntegerProperty age)) continue;
                    Integer current = state.getState(age);
                    if (current == null || current >= maxAge) continue;
                    session.setBlock(pos, state.with(age, maxAge));
                    grown++;
                }
                if (grown > 0 && plugin.getConfig().getBoolean("debug-crops", false)) {
                    plugin.getLogger().info("[CropRegen] Madurados " + grown + " cultivos en " + rb.getKey());
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[FAWE] Error al regenerar " + rb.getKey() + ": " + e.getMessage());
            } finally {
                inFlight.remove(rb.getKey());
            }
        });
    }
}