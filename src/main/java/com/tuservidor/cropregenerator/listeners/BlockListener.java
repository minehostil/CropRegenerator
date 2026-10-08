package com.tuservidor.cropregenerator.listeners;

import com.tuservidor.cropregenerator.CropRegeneratorPlugin;
import com.tuservidor.cropregenerator.model.RegeneratorBlock;
import com.tuservidor.cropregenerator.util.ItemUtil;
import com.tuservidor.cropregenerator.util.MessageUtil;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;

public class BlockListener implements Listener {

    private final CropRegeneratorPlugin plugin;

    public BlockListener(CropRegeneratorPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Colocar bloque ───────────────────────────────────────

    // HIGHEST: corre DESPUÉS de plugins de protección (WorldGuard, etc.) y con
    // ignoreCancelled respetamos sus cancelaciones. Evita ghost blocks si otro
    // plugin cancela el evento.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItemInHand();

        if (!ItemUtil.isRegeneratorItem(item)) return;

        int[] levels = ItemUtil.getLevelsFromItem(item); // {time, radius, crops}
        Block block = event.getBlockPlaced();

        // ── Verificar isla (SuperiorSkyblock2) ────────────────
        if (plugin.hasSuperior()) {
            if (!plugin.getSuperiorHook().isOnOwnIsland(player, block.getLocation())) {
                MessageUtil.send(player, "not-your-island");
                event.setCancelled(true);
                return;
            }

            String islandId = plugin.getSuperiorHook().getIslandIdAt(block.getLocation());
            int current     = plugin.getBlockDataManager().countBlocksForIsland(islandId);
            int max         = plugin.getConfig().getInt("limits.max-blocks-per-island", 5);

            if (current >= max) {
                MessageUtil.send(player, "limit-reached", "{max}", String.valueOf(max));
                event.setCancelled(true);
                return;
            }

            RegeneratorBlock rb = new RegeneratorBlock(block.getLocation(), player.getUniqueId(),
                    levels[0], levels[1], levels[2], false, System.currentTimeMillis());
            plugin.getBlockDataManager().addBlock(rb, islandId);
            scheduleHologram(rb);

        } else {
            // Sin SSB2: sin límite de isla
            RegeneratorBlock rb = new RegeneratorBlock(block.getLocation(), player.getUniqueId(),
                    levels[0], levels[1], levels[2], false, System.currentTimeMillis());
            plugin.getBlockDataManager().addBlock(rb, null);
            scheduleHologram(rb);
        }

        MessageUtil.send(player, "placed");
    }

    // ── Romper bloque ────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();

        RegeneratorBlock rb = plugin.getBlockDataManager().getBlock(block.getLocation());
        if (rb == null) return;

        Player player = event.getPlayer();

        // Solo el dueño o admin puede romperlo
        if (!player.getUniqueId().equals(rb.getOwnerUUID())
                && !player.hasPermission("cropregenerator.admin")) {
            MessageUtil.send(player, "no-permission");
            event.setCancelled(true);
            return;
        }

        // Quitar holograma y datos
        plugin.getHologramManager().remove(rb);
        plugin.getBlockDataManager().removeBlock(rb);

        // Devolver el ítem con los TRES niveles originales (no el máximo)
        event.setDropItems(false);
        ItemStack drop = ItemUtil.createRegeneratorItem(rb.getTimeLevel(), rb.getRadiusLevel(), rb.getCropsLevel());
        block.getWorld().dropItemNaturally(block.getLocation(), drop);

        MessageUtil.send(player, "removed");
    }

    // ── Protección: pistones ─────────────────────────────────
    // Si un pistón mueve un regenerador, el bloque físico se desincronizaría
    // de los datos (holograma fantasma + regeneración en el vacío). Se cancela.

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block b : event.getBlocks()) {
            if (plugin.getBlockDataManager().isRegeneratorBlock(b.getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block b : event.getBlocks()) {
            if (plugin.getBlockDataManager().isRegeneratorBlock(b.getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // ── Protección: explosiones ──────────────────────────────
    // La explosión ocurre con normalidad, pero el regenerador es inmune:
    // se elimina de la lista de bloques afectados en vez de cancelar todo.

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(b -> plugin.getBlockDataManager().isRegeneratorBlock(b.getLocation()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(b -> plugin.getBlockDataManager().isRegeneratorBlock(b.getLocation()));
    }

    // ── Helper ───────────────────────────────────────────────

    private void scheduleHologram(RegeneratorBlock rb) {
        // El holograma se spawna 1 tick después para que el bloque ya exista
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> plugin.getHologramManager().spawnOrUpdate(rb), 1L);
    }
}