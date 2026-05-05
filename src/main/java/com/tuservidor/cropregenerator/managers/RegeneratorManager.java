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

import java.util.HashMap;
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
            try (EditSession editSession = WorldEdit.getInstance()
                    .newEditSessionBuilder()
                    .world(weWorld)
                    .fastMode(true)
                    .limitUnlimited()
                    .build()) {

                int grown = 0;

                for (BlockVector3 pos : region) {
                    BlockState state = editSession.getBlock(pos);
                    BlockType type   = state.getBlockType();

                    if (type == null) continue;
                    String typeId = type.id();
                    if (!CROP_MAX_AGE.containsKey(typeId)) continue;

                    int maxAge = CROP_MAX_AGE.get(typeId);

                    // Buscar la property "age" tipada como IntegerProperty
                    Property<?> rawProp = type.getProperty("age");
                    if (!(rawProp instanceof IntegerProperty ageProp)) continue;

                    Integer currentAge = state.getState(ageProp);
                    if (currentAge == null || currentAge >= maxAge) continue;

                    BlockState mature = state.with(ageProp, maxAge);
                    editSession.setBlock(pos, mature);
                    grown++;
                }

                if (grown > 0) {
                    plugin.getLogger().fine("[FAWE] Madurados " + grown + " cultivos en "
                            + center.getWorld().getName()
                            + " (" + center.getBlockX() + ","
                            + center.getBlockY() + ","
                            + center.getBlockZ() + ")");
                }

            } catch (Exception e) {
                plugin.getLogger().warning("[FAWE] Error al regenerar cultivos: " + e.getMessage());
            }
        });
    }
}
