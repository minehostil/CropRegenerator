package com.tuservidor.cropregenerator.listeners;

import com.tuservidor.cropregenerator.CropRegeneratorPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Pausa el task global de regeneración cuando no hay jugadores
 * conectados y lo reanuda cuando alguien se une.
 * Elimina el consumo de CPU en servidores vacíos.
 */
public class PlayerConnectionListener implements Listener {

    private final CropRegeneratorPlugin plugin;

    public PlayerConnectionListener(CropRegeneratorPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Si era el único jugador ausente, reanudar el task
        if (plugin.getServer().getOnlinePlayers().size() == 1) {
            plugin.getRegeneratorManager().resume();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        // -1 porque el jugador aún está en la lista al dispararse el evento
        if (plugin.getServer().getOnlinePlayers().size() - 1 == 0) {
            plugin.getRegeneratorManager().pause();
        }
    }
}