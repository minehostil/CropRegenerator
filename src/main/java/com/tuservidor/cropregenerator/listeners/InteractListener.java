package com.tuservidor.cropregenerator.listeners;

import com.tuservidor.cropregenerator.CropRegeneratorPlugin;
import com.tuservidor.cropregenerator.model.RegeneratorBlock;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.block.Action;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Detecta clic derecho sobre el bloque regenerador y ejecuta
 * los comandos configurados en config.yml como el jugador.
 */
public class InteractListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final CropRegeneratorPlugin plugin;

    // UUID → timestamp (ms) del último uso, para cooldown
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public InteractListener(CropRegeneratorPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Solo clic derecho sobre un bloque
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        // Evitar doble disparo por off-hand
        if (event.getHand() != EquipmentSlot.HAND) return;

        if (!plugin.getConfig().getBoolean("interaction.enabled", true)) return;

        var clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;

        RegeneratorBlock rb = plugin.getBlockDataManager().getBlock(clickedBlock.getLocation());
        if (rb == null) return;

        // Cancelar el evento para que no abra inventario ni haga nada vanilla
        event.setCancelled(true);

        Player player = event.getPlayer();

        // ── Cooldown ─────────────────────────────────────────
        int cooldownSecs = plugin.getConfig().getInt("interaction.cooldown", 3);
        if (cooldownSecs > 0) {
            long now  = System.currentTimeMillis();
            long last = cooldowns.getOrDefault(player.getUniqueId(), 0L);
            long diff = (now - last) / 1000L;

            if (diff < cooldownSecs) {
                long remaining = cooldownSecs - diff;
                String cdMsg = plugin.getConfig()
                        .getString("interaction.cooldown-message", "<red>Espera {seconds}s.")
                        .replace("{seconds}", String.valueOf(remaining));
                player.sendMessage(MM.deserialize(cdMsg));
                return;
            }
            cooldowns.put(player.getUniqueId(), now);
        }

        // ── Ejecutar comandos como el jugador ────────────────
        List<String> commands = plugin.getConfig().getStringList("interaction.commands");
        for (String cmd : commands) {
            String parsed = cmd.replace("{player}", player.getName());
            player.performCommand(parsed);
        }

        // ── Mensaje de feedback ───────────────────────────────
        String msg = plugin.getConfig().getString("interaction.message", "");
        if (!msg.isEmpty()) {
            player.sendMessage(MM.deserialize(msg));
        }
    }
}
