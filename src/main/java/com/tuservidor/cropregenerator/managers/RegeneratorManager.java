package com.tuservidor.cropregenerator.managers;

import com.fastasyncworldedit.core.FaweAPI;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gestiona los timers de regeneración usando FAWE para edición async eficiente.
 */
public class RegeneratorManager {

    private static final Map<String, Integer> CROP_MAX_AGE = new HashMap<>();

    static {
        CROP_MAX_AGE.put("minecraft:wheat",            7);
        CROP_MAX_AGE.put("minecraft:carrots",          7);
        CROP_MAX_AGE.put("minecraft:potatoes",         7);
        CROP_MAX_AGE.put("minecraft:beetroots",        3);
        CROP_MAX_AGE.put("minecraft:nether_wart",      3);
        CROP_MAX_AGE.put("minecraft:cocoa",            2);
        CROP_MAX_AGE.put("minecraft:melon_stem",       7);
        CROP_MAX_AGE.put("minecraft:pumpkin_stem",     7);
        CROP_MAX_AGE.put("minecraft:sweet_berry_bush", 3);
        CROP_MAX_AGE.put("minecraft:pitcher_crop",     4);
        CROP_MAX_AGE.put("minecraft:torchflower_crop", 1);
    }

    private final CropRegeneratorPlugin plugin;
    private BukkitTask globalTask;

    public RegeneratorManager(CropRegeneratorPlugin plugin) {
        this.plugin = plugin;
    }

    public void startAll() {
        if (globalTask != null) globalTask.cancel();

        // Leer intervalo de actualización del holograma desde config (mínimo 1 segundo)
        int updateSecs = Math.max(1, plugin.getConfig().getInt("hologram.update-interval", 1));
        long updateTicks = updateSecs * 20L;

        globalTask = new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                for (RegeneratorBlock rb : plugin.getBlockDataManager().getAllBlocks()) {
                    plugin.getHologramManager().updateText(rb);

                    if (now >= rb.getNextRegenTimestamp()) {
                        long intervalMs = plugin.getUpgradeManager()
                                .getLevel(rb.getLevel()).regenInterval() * 1000L;
                        rb.setNextRegenTimestamp(now + intervalMs);
                        regenerateAsync(rb);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, updateTicks);
    }

    public void stopAll() {
        if (globalTask != null) {
            globalTask.cancel();
            globalTask = null;
        }
    }

    private void regenerateAsync(RegeneratorBlock rb) {
        Location center = rb.getLocation();
        if (center.getWorld() == null) return;

        int radius = plugin.getUpgradeManager().getLevel(rb.getLevel()).radius();

        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(center.getWorld());

        BlockVector3 min = BlockVector3.at(
                center.getBlockX() - radius,
                center.getBlockY() - radius,
                center.getBlockZ() - radius);
        BlockVector3 max = BlockVector3.at(
                center.getBlockX() + radius,
                center.getBlockY() + radius,
                center.getBlockZ() + radius);
        CuboidRegion region = new CuboidRegion(weWorld, min, max);

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // ── Paso 1: escaneo con Bukkit API (fuente de verdad real) ──
                // FAWE en fastMode puede leer de su propio cache en vez del
                // mundo real. Usamos Bukkit directamente para el escaneo.
                List<BlockVector3> toGrow = new ArrayList<>();

                org.bukkit.World bukkitWorld = center.getWorld();
                for (BlockVector3 pos : region) {
                    org.bukkit.block.Block block = bukkitWorld.getBlockAt(
                            pos.x(), pos.y(), pos.z());

                    if (!CROP_MAX_AGE.containsKey(block.getType().getKey().toString())) continue;

                    if (!(block.getBlockData() instanceof org.bukkit.block.data.Ageable ageable)) continue;
                    if (ageable.getAge() >= ageable.getMaximumAge()) continue;

                    toGrow.add(pos);
                }

                // Sin cultivos inmaduros — no abrir FAWE
                if (toGrow.isEmpty()) return;

                plugin.getLogger().info("[CropRegen] " + toGrow.size() + " cultivos inmaduros encontrados, regenerando...");

                // ── Paso 2: escritura con FAWE (solo posiciones necesarias) ──
                try (EditSession editSession = WorldEdit.getInstance()
                        .newEditSessionBuilder()
                        .world(weWorld)
                        .fastMode(true)
                        .limitUnlimited()
                        .build()) {

                    int grown = 0;
                    for (BlockVector3 pos : toGrow) {
                        BlockState state = editSession.getBlock(pos);
                        BlockType type   = state.getBlockType();
                        if (type == null) continue;

                        Property<?> rawProp = type.getProperty("age");
                        if (!(rawProp instanceof IntegerProperty ageProp)) continue;

                        int maxAge = CROP_MAX_AGE.get(type.id());
                        BlockState mature = state.with(ageProp, maxAge);
                        editSession.setBlock(pos, mature);
                        grown++;
                    }

                    plugin.getLogger().info("[CropRegen] Madurados " + grown + " cultivos en "
                            + center.getWorld().getName()
                            + " (" + center.getBlockX() + ","
                            + center.getBlockY() + ","
                            + center.getBlockZ() + ")");
                }

            } catch (Exception e) {
                plugin.getLogger().warning("[FAWE] Error al regenerar cultivos: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
}
