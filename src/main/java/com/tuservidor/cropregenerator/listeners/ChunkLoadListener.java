package com.tuservidor.cropregenerator.listeners;

import com.tuservidor.cropregenerator.CropRegeneratorPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Elimina TextDisplays huérfanas del plugin cuando se carga un chunk.
 *
 * Caso que cubre: jugador que se une al servidor o viaja a una zona
 * donde el chunk no estaba cargado cuando corrió purgeAll() en onEnable.
 * Paper carga el NBT del chunk en ese momento y las entidades persistentes
 * reaparecen — este listener las elimina antes de que el jugador las vea.
 */
public class ChunkLoadListener implements Listener {

    private final CropRegeneratorPlugin plugin;
    private final NamespacedKey holoKey;

    public ChunkLoadListener(CropRegeneratorPlugin plugin) {
        this.plugin  = plugin;
        this.holoKey = new NamespacedKey(plugin, "cropgen_hologram");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        // Solo chunks que ya existían (no nuevos generados)
        if (!event.isNewChunk()) {
            // Saltar chunks sin entidades — la mayoría
            if (event.getChunk().getEntities().length == 0) return;

            for (Entity entity : event.getChunk().getEntities()) {
                if (entity instanceof TextDisplay td
                        && td.getPersistentDataContainer().has(holoKey, PersistentDataType.BYTE)) {
                    td.remove();
                }
            }
        }
    }
}
