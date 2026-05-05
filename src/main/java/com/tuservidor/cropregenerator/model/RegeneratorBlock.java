package com.tuservidor.cropregenerator.model;

import org.bukkit.Location;

import java.util.UUID;

/**
 * Representa un bloque regenerador activo en el mundo.
 */
public class RegeneratorBlock {

    private final Location location;
    private final UUID ownerUUID;
    private int level;
    private long nextRegenTimestamp; // epoch ms de la próxima regeneración

    public RegeneratorBlock(Location location, UUID ownerUUID, int level) {
        this.location  = location;
        this.ownerUUID = ownerUUID;
        this.level     = level;
        this.nextRegenTimestamp = System.currentTimeMillis();
    }

    // ── Getters / Setters ───────────────────────────────────
    public Location getLocation()  { return location; }
    public UUID     getOwnerUUID() { return ownerUUID; }
    public int      getLevel()     { return level; }
    public void     setLevel(int l){ this.level = l; }

    public long getNextRegenTimestamp()            { return nextRegenTimestamp; }
    public void setNextRegenTimestamp(long ts)     { this.nextRegenTimestamp = ts; }

    /** Segundos restantes para la próxima regeneración (≥ 0). */
    public long getSecondsUntilRegen() {
        long diff = (nextRegenTimestamp - System.currentTimeMillis()) / 1000L;
        return Math.max(0, diff);
    }

    /** Clave única para mapas: "world,x,y,z" */
    public String getKey() {
        return location.getWorld().getName() + ","
                + location.getBlockX() + ","
                + location.getBlockY() + ","
                + location.getBlockZ();
    }
}
