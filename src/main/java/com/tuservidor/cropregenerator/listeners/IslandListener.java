package com.tuservidor.cropregenerator.listeners;

import com.bgsoftware.superiorskyblock.api.events.IslandDisbandEvent;
import com.bgsoftware.superiorskyblock.api.events.IslandTransferEvent;
import com.tuservidor.cropregenerator.CropRegeneratorPlugin;
import com.tuservidor.cropregenerator.model.RegeneratorBlock;
import com.tuservidor.cropregenerator.util.ItemUtil;
import com.tuservidor.cropregenerator.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Eventos de SuperiorSkyblock2 que afectan a los regeneradores:
 *
 *  - IslandDisbandEvent:  isla disuelta → bloques devueltos a quien los colocó.
 *  - IslandTransferEvent: isla transferida → bloques devueltos a quien los colocó
 *                         (evita que el nuevo dueño herede mejoras que no pagó).
 *
 * El ítem devuelto conserva los TRES niveles originales del bloque.
 * Si el dueño no está conectado, queda warning en consola con UUID y niveles
 * para compensación manual.
 */
public class IslandListener implements Listener {

    private final CropRegeneratorPlugin plugin;

    public IslandListener(CropRegeneratorPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandDisband(IslandDisbandEvent event) {
        removeAndReturn(event.getIsland().getUniqueId().toString(), "disuelta");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandTransfer(IslandTransferEvent event) {
        removeAndReturn(event.getIsland().getUniqueId().toString(), "transferida");
    }

    // ── Helper ───────────────────────────────────────────────

    /** Retira todos los regeneradores de la isla y devuelve el ítem a quien lo colocó. */
    private void removeAndReturn(String islandId, String reason) {
        List<RegeneratorBlock> removed = plugin.getBlockDataManager().removeAllForIsland(islandId);
        plugin.getBlockDataManager().saveAll(); // persiste la eliminación ya, sin esperar al autosave

        for (RegeneratorBlock rb : removed) {
            // Quitar holograma
            plugin.getHologramManager().remove(rb);

            // Quitar bloque físico del mundo (async-safe con runTask)
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (rb.getLocation().getWorld() != null) {
                    rb.getLocation().getBlock().setType(Material.AIR);
                }
            });

            // Devolver el ítem con los tres niveles originales
            ItemStack item = ItemUtil.createRegeneratorItem(rb.getTimeLevel(), rb.getRadiusLevel(), rb.getCropsLevel());
            Player owner = plugin.getServer().getPlayer(rb.getOwnerUUID());
            if (owner != null && owner.isOnline()) {
                var leftover = owner.getInventory().addItem(item);
                // Inventario lleno: se tira a los pies del dueño (nunca en la isla)
                leftover.values().forEach(rest -> owner.getWorld().dropItemNaturally(owner.getLocation(), rest));
                MessageUtil.send(owner, "blocks-returned", "{reason}", reason);
            } else {
                // Dueño offline (admin disband / purga): el ítem no se puede entregar.
                // Queda registrado en consola para compensación manual.
                plugin.getLogger().warning("[IslandListener] Isla " + islandId + " " + reason
                        + ": el dueño del bloque " + rb.getKey()
                        + " (UUID " + rb.getOwnerUUID() + ") está offline — ítem nivel T:"
                        + rb.getTimeLevel() + "/R:" + rb.getRadiusLevel() + "/C:" + rb.getCropsLevel()
                        + " NO devuelto. Compensa manualmente si procede.");
            }
        }

        if (!removed.isEmpty()) {
            plugin.getLogger().info("Isla " + islandId + " " + reason
                    + " — retirados y devueltos " + removed.size() + " bloques regeneradores.");
        }
    }
}