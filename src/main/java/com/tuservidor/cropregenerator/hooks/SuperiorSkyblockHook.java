package com.tuservidor.cropregenerator.hooks;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import com.tuservidor.cropregenerator.CropRegeneratorPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Abstrae todas las llamadas a la API de SuperiorSkyblock2.
 */
public class SuperiorSkyblockHook {

    private final CropRegeneratorPlugin plugin;

    public SuperiorSkyblockHook(CropRegeneratorPlugin plugin) {
        this.plugin = plugin;
    }

    /** Devuelve el ID de la isla en la que se encuentra la ubicación, o null. */
    public String getIslandIdAt(Location location) {
        Island island = SuperiorSkyblockAPI.getIslandAt(location);
        return island != null ? island.getUniqueId().toString() : null;
    }

    /** Devuelve la isla del jugador (la que es owner), o null. */
    public Island getIslandOf(Player player) {
        SuperiorPlayer sp = SuperiorSkyblockAPI.getPlayer(player);
        return sp == null ? null : sp.getIsland();
    }

    /** ¿Es el jugador miembro o dueño de la isla en esa ubicación? */
    public boolean isOnOwnIsland(Player player, Location location) {
        Island islandAt = SuperiorSkyblockAPI.getIslandAt(location);
        if (islandAt == null) return false;

        SuperiorPlayer sp = SuperiorSkyblockAPI.getPlayer(player);
        if (sp == null) return false;

        return islandAt.isMember(sp) || islandAt.getOwner().equals(sp);
    }

    /** ID único de la isla del jugador como String, o null. */
    public String getPlayerIslandId(Player player) {
        Island island = getIslandOf(player);
        return island != null ? island.getUniqueId().toString() : null;
    }


    /** Cuántos bloques regeneradores puede tener la isla según el nivel del upgrade. */
    public int getMaxBlocks(Player player, int upgradeLevel) {
        return plugin.getUpgradeManager().getLevel(upgradeLevel).maxBlocksPerIsland();
    }
}