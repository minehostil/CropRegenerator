package com.tuservidor.cropregenerator.listeners;

import com.tuservidor.cropregenerator.CropRegeneratorPlugin;
import com.tuservidor.cropregenerator.model.RegeneratorBlock;
import com.tuservidor.cropregenerator.util.ItemUtil;
import com.tuservidor.cropregenerator.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

public class BlockListener implements Listener {

    private final CropRegeneratorPlugin plugin;

    public BlockListener(CropRegeneratorPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Colocar bloque ───────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItemInHand();

        if (!ItemUtil.isRegeneratorItem(item)) return;

        int itemLevel = ItemUtil.getLevelFromItem(item);
        Block block   = event.getBlockPlaced();

        // ── Verificar isla (SuperiorSkyblock2) ────────────────
        if (plugin.hasSuperior()) {
            if (!plugin.getSuperiorHook().isOnOwnIsland(player, block.getLocation())) {
                MessageUtil.send(player, "not-your-island");
                event.setCancelled(true);
                return;
            }

            String islandId = plugin.getSuperiorHook().getPlayerIslandId(player);
            int current     = plugin.getBlockDataManager().countBlocksForIsland(islandId);
            int max         = plugin.getUpgradeManager().getLevel(itemLevel).maxBlocksPerIsland();

            if (current >= max) {
                MessageUtil.send(player, "limit-reached", "{max}", String.valueOf(max));
                event.setCancelled(true);
                return;
            }

            // Registrar bloque
            RegeneratorBlock rb = new RegeneratorBlock(block.getLocation(), player.getUniqueId(), itemLevel);
            plugin.getBlockDataManager().addBlock(rb, islandId);
            scheduleHologram(rb);

        } else {
            // Sin SSB2: sin límite de isla
            RegeneratorBlock rb = new RegeneratorBlock(block.getLocation(), player.getUniqueId(), itemLevel);
            plugin.getBlockDataManager().addBlock(rb, null);
            scheduleHologram(rb);
        }

        MessageUtil.send(player, "placed");
    }

    // ── Romper bloque ────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
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

        // Devolver el ítem con el nivel correcto
        event.setDropItems(false);
        ItemStack drop = ItemUtil.createRegeneratorItem(rb.getLevel());
        block.getWorld().dropItemNaturally(block.getLocation(), drop);

        MessageUtil.send(player, "removed");
    }

    // ── Helper ───────────────────────────────────────────────

    private void scheduleHologram(RegeneratorBlock rb) {
        // El holograma se spawna 1 tick después para que el bloque ya exista
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> plugin.getHologramManager().spawnOrUpdate(rb), 1L);
    }
}
