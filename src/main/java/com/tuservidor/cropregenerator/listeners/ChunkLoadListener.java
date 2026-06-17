package com.tuservidor.cropregenerator.listeners;

import com.tuservidor.cropregenerator.CropRegeneratorPlugin;
import com.tuservidor.cropregenerator.model.RegeneratorBlock;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

public class ChunkLoadListener implements Listener {

    private final CropRegeneratorPlugin plugin;

    public ChunkLoadListener(CropRegeneratorPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (event.isNewChunk()) return;

        Chunk chunk = event.getChunk();

        // Respawnear hologramas de bloques en este chunk
        // runTask asegura que el chunk esté completamente inicializado
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (RegeneratorBlock rb : plugin.getBlockDataManager().getAllBlocks()) {
                if (rb.getLocation().getWorld() == null) continue;
                if (!rb.getLocation().getWorld().equals(chunk.getWorld())) continue;
                if ((rb.getLocation().getBlockX() >> 4) != chunk.getX()) continue;
                if ((rb.getLocation().getBlockZ() >> 4) != chunk.getZ()) continue;

                // Respawnear — spawnOrUpdate limpia el anterior si existe
                plugin.getHologramManager().spawnOrUpdate(rb);
            }
        });
    }
}