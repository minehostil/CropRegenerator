package com.tuservidor.cropregenerator.model;

import org.bukkit.Location;

import java.util.UUID;

/** Representa un bloque regenerador activo en el mundo. */
public class RegeneratorBlock {
    private final Location location;
    private final UUID ownerUUID;
    private int timeLevel;
    private int radiusLevel;
    private int cropsLevel;
    private boolean particlesEnabled;
    private long nextRegenTimestamp;

    public RegeneratorBlock(Location location, UUID ownerUUID, int level) {
        this(location, ownerUUID, level, level, level, false, System.currentTimeMillis());
    }

    public RegeneratorBlock(Location location, UUID ownerUUID, int timeLevel, int radiusLevel,
                            int cropsLevel, boolean particlesEnabled, long nextRegenTimestamp) {
        this.location = location.clone();
        this.ownerUUID = ownerUUID;
        this.timeLevel = timeLevel;
        this.radiusLevel = radiusLevel;
        this.cropsLevel = cropsLevel;
        this.particlesEnabled = particlesEnabled;
        this.nextRegenTimestamp = nextRegenTimestamp;
    }

    public Location getLocation() { return location; }
    public UUID getOwnerUUID() { return ownerUUID; }

    public int getTimeLevel() { return timeLevel; }
    public void setTimeLevel(int level) { this.timeLevel = level; }

    public int getRadiusLevel() { return radiusLevel; }
    public void setRadiusLevel(int level) { this.radiusLevel = level; }

    public int getCropsLevel() { return cropsLevel; }
    public void setCropsLevel(int level) { this.cropsLevel = level; }

    public boolean isParticlesEnabled() { return particlesEnabled; }
    public void setParticlesEnabled(boolean enabled) { this.particlesEnabled = enabled; }

    public long getNextRegenTimestamp() { return nextRegenTimestamp; }
    public void setNextRegenTimestamp(long timestamp) { this.nextRegenTimestamp = timestamp; }

    public long getSecondsUntilRegen() {
        long diff = (nextRegenTimestamp - System.currentTimeMillis()) / 1000L;
        return Math.max(0L, diff);
    }

    public String getKey() {
        return location.getWorld().getName() + ","
                + location.getBlockX() + ","
                + location.getBlockY() + ","
                + location.getBlockZ();
    }

    /** Compatibilidad con configuraciones/comandos antiguos: nivel base del bloque. */
    public int getLevel() {
        return Math.max(timeLevel, Math.max(radiusLevel, cropsLevel));
    }

    /** Aplica un nivel antiguo a los tres árboles. */
    public void setLevel(int level) {
        this.timeLevel = level;
        this.radiusLevel = level;
        this.cropsLevel = level;
    }
}