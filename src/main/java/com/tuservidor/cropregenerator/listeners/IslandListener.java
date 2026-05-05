package com.tuservidor.cropregenerator.listeners;

import com.bgsoftware.superiorskyblock.api.events.IslandDisbandEvent;
import com.tuservidor.cropregenerator.CropRegeneratorPlugin;
import com.tuservidor.cropregenerator.model.RegeneratorBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.List;

/**
 * Escucha el evento de disolución de isla en SuperiorSkyblock2
 * para eliminar todos los bloques regeneradores asociados.
 */
public class IslandListener implements Listener {

    private final CropRegeneratorPlugin plugin;

    public IslandListener(CropRegeneratorPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandDisband(IslandDisbandEvent event) {
        String islandId = event.getIsland().getUniqueId().toString();

        List<RegeneratorBlock> removed =
                plugin.getBlockDataManager().removeAllForIsland(islandId);

        for (RegeneratorBlock rb : removed) {
            // Quitar holograma
            plugin.getHologramManager().remove(rb);

            // Quitar bloque físico del mundo (async-safe con runTask)
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (rb.getLocation().getWorld() != null) {
                    rb.getLocation().getBlock().setType(org.bukkit.Material.AIR);
                }
            });
        }

        if (!removed.isEmpty()) {
            plugin.getLogger().info("Isla " + islandId + " disuelta — eliminados "
                    + removed.size() + " bloques regeneradores.");
        }
    }
}
