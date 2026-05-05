package com.tuservidor.cropregenerator.hooks;

import com.fastasyncworldedit.core.FaweAPI;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import com.tuservidor.cropregenerator.CropRegeneratorPlugin;
import com.tuservidor.cropregenerator.model.RegeneratorBlock;
import org.bukkit.Location;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Usa la API de FAWE para madurar cultivos de forma asíncrona y en batch,
 * sin bloquear el hilo principal del servidor.
 *
 * Flujo:
 *  1. Se obtiene un EditSession con FAWE (async por defecto).
 *  2. Se itera la región con getBlock() — lectura barata en FAWE.
 *  3. Los bloques que son cultivos inmaduros se reescriben con su estado maduro.
 *  4. Al cerrar el EditSession FAWE envía los cambios al mundo en batch.
 */
public class FAWEHook {

    // Mapa de tipo de cultivo → BlockState con age máximo pre-calculado
    // FAWE trabaja con BlockState directamente, necesitamos el estado "maduro" de cada cultivo.
    private static final Map<BlockType, Integer> MAX_AGES = new HashMap<>();

    static {
        // age máximo de cada cultivo soportado
        putIfPresent(BlockTypes.WHEAT,            7);
        putIfPresent(BlockTypes.CARROTS,          7);
        putIfPresent(BlockTypes.POTATOES,         7);
        putIfPresent(BlockTypes.BEETROOTS,        3);
        putIfPresent(BlockTypes.NETHER_WART,      3);
        putIfPresent(BlockTypes.COCOA,            2);
        putIfPresent(BlockTypes.MELON_STEM,       7);
        putIfPresent(BlockTypes.PUMPKIN_STEM,     7);
        putIfPresent(BlockTypes.SWEET_BERRY_BUSH, 3);
        putIfPresent(BlockTypes.PITCHER_CROP,     4);
        putIfPresent(BlockTypes.TORCHFLOWER_CROP, 1);
    }

    private static void putIfPresent(BlockType type, int maxAge) {
        if (type != null) MAX_AGES.put(type, maxAge);
    }

    private final CropRegeneratorPlugin plugin;

    public FAWEHook(CropRegeneratorPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Regenera todos los cultivos en el radio del bloque usando FAWE async.
     * Este método es seguro llamarlo desde cualquier hilo.
     */
    public void regenerateAsync(RegeneratorBlock rb) {
        Location center = rb.getLocation();
        if (center.getWorld() == null) return;

        int radius = plugin.getUpgradeManager().getLevel(rb.getLevel()).radius();

        // Adaptador de mundo Bukkit → WorldEdit
        World weWorld = BukkitAdapter.adapt(center.getWorld());

        BlockVector3 min = BlockVector3.at(
                center.getBlockX() - radius,
                center.getBlockY() - radius,
                center.getBlockZ() - radius);
        BlockVector3 max = BlockVector3.at(
                center.getBlockX() + radius,
                center.getBlockY() + radius,
                center.getBlockZ() + radius);

        CuboidRegion region = new CuboidRegion(weWorld, min, max);

        // EditSession de FAWE — async por defecto, sin límite de bloques
        try (EditSession editSession = WorldEdit.getInstance()
                .newEditSessionBuilder()
                .world(weWorld)
                .allowedRegionsEverywhere()
                .limitUnlimited()
                .build()) {

            int count = 0;

            for (BlockVector3 pos : region) {
                BlockState state = editSession.getBlock(pos);
                BlockType type   = state.getBlockType();

                Integer maxAge = MAX_AGES.get(type);
                if (maxAge == null) continue;

                // Leer el age actual del BlockState
                var ageProp = type.getProperty("age");
                if (ageProp == null) continue;

                int currentAge = (int) state.getState(ageProp);
                if (currentAge >= maxAge) continue;

                // Construir el estado maduro y escribirlo
                BlockState mature = state.with(ageProp, maxAge);
                editSession.setBlock(pos, mature);
                count++;
            }

            if (count > 0) {
                plugin.getLogger().fine("FAWE: madurados " + count + " cultivos en "
                        + center.getWorld().getName()
                        + " (" + center.getBlockX() + "," + center.getBlockY() + "," + center.getBlockZ() + ")");
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "FAWE: error al regenerar cultivos en " + rb.getKey(), e);
        }
    }
}
