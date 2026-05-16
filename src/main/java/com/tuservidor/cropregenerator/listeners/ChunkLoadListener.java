package com.tuservidor.cropregenerator.listeners;

import com.tuservidor.cropregenerator.CropRegeneratorPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

/**
 * Elimina TextDisplays huérfanas cuando se carga un chunk.
 * Solo actúa si el proveedor es NATIVE — GHoloProvider gestiona
 * sus propias entidades internamente.
 */
public class ChunkLoadListener implements Listener {

    private final CropRegeneratorPlugin plugin;

    public ChunkLoadListener(CropRegeneratorPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.isNewChunk()) {
            plugin.getHologramManager().purgeChunk(event.getChunk());
        }
    }
}