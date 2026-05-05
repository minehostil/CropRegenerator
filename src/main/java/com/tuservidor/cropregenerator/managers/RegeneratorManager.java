package com.tuservidor.cropregenerator.managers;

import com.fastasyncworldedit.core.FaweAPI;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import com.tuservidor.cropregenerator.CropRegeneratorPlugin;
import com.tuservidor.cropregenerator.model.RegeneratorBlock;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;

/**
 * Gestiona los timers de regeneración usando FAWE para edición async eficiente.
 *
 * Estrategia:
 *  - Un BukkitRunnable global cada segundo actualiza hologramas y detecta cuándo
 *    toca regenerar cada bloque.
 *  - Cuando toca regenerar, se lanza un EditSession de FAWE en un thread async
 *    (FAWE maneja su propio pool). FAWE itera el CuboidRegion internamente de forma
 *    optimizada y hace flush al mundo sin tocar el hilo principal.
 *
 * Mapa de age máximo por cultivo (Ageable):
 *   WHEAT=7, CARROTS=7, POTATOES=7, BEETROOTS=3, NETHER_WART=3,
 *   COCOA=2, MELON_STEM=7, PUMPKIN_STEM=7, SWEET_BERRY_BUSH=3,
 *   PITCHER_CROP=4, TORCHFLOWER_CROP=1
 */
public class RegeneratorManager {

    // BlockType id → age máximo para ese cultivo
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

    // ── Lifecycle ────────────────────────────────────────────

    public void startAll() {
        if (globalTask != null) globalTask.cancel();

        globalTask = new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                for (RegeneratorBlock rb : plugin.getBlockDataManager().getAllBlocks()) {
                    // Actualizar holograma cada segundo
                    plugin.getHologramManager().updateText(rb);

                    // Regenerar si llegó la hora
                    if (now >= rb.getNextRegenTimestamp()) {
                        long intervalMs = plugin.getUpgradeManager()
                                .getLevel(rb.getLevel()).regenInterval() * 1000L;
                        rb.setNextRegenTimestamp(now + intervalMs);
                        // Lanzar la regeneración async con FAWE
                        regenerateAsync(rb);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void stopAll() {
        if (globalTask != null) {
            globalTask.cancel();
            globalTask = null;
        }
    }

    // ── Regeneración con FAWE ────────────────────────────────

    /**
     * Corre completamente fuera del hilo principal gracias a FAWE.
     * FAWE gestiona su propio thread pool de edición.
     */
    private void regenerateAsync(RegeneratorBlock rb) {
        Location center = rb.getLocation();
        if (center.getWorld() == null) return;

        int radius = plugin.getUpgradeManager().getLevel(rb.getLevel()).radius();

        // Obtener el mundo de WorldEdit
        com.sk89q.worldedit.world.World weWorld =
                BukkitAdapter.adapt(center.getWorld());

        // Región cúbica del área de efecto
        BlockVector3 min = BlockVector3.at(
                center.getBlockX() - radius,
                center.getBlockY() - radius,
                center.getBlockZ() - radius);
        BlockVector3 max = BlockVector3.at(
                center.getBlockX() + radius,
                center.getBlockY() + radius,
                center.getBlockZ() + radius);
        CuboidRegion region = new CuboidRegion(weWorld, min, max);

        // EditSession de FAWE — async por defecto con FaweAPI
        // try-with-resources garantiza el flush al cerrar
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (EditSession editSession = WorldEdit.getInstance()
                    .newEditSessionBuilder()
                    .world(weWorld)
                    .fastMode(true)          // FAWE fast mode: sin fire block events
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

                    // Obtener age actual del BlockState
                    var ageProperty = type.getProperty("age");
                    if (ageProperty == null) continue;

                    Object currentAge = state.getState(ageProperty);
                    if (!(currentAge instanceof Integer age)) continue;
                    if (age >= maxAge) continue;

                    // Setear age máximo
                    @SuppressWarnings("unchecked")
                    var prop = (com.sk89q.worldedit.registry.state.Property<Integer>) ageProperty;
                    BlockState mature = state.with(prop, maxAge);
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
