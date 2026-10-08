package com.tuservidor.cropregenerator.model;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

/** Representa un bloque regenerador activo en el mundo. */
public class RegeneratorBlock {
    private final Location location;
    private final UUID ownerUUID;
    private String worldName; // fallback: nombre del mundo aunque no esté cargado
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
        World w = this.location.getWorld();
        this.worldName = (w != null) ? w.getName() : "";
        this.timeLevel = timeLevel;
        this.radiusLevel = radiusLevel;
        this.cropsLevel = cropsLevel;
        this.particlesEnabled = particlesEnabled;
        this.nextRegenTimestamp = nextRegenTimestamp;
    }

    /** Copia defensiva: los callers no pueden mutar el estado interno. */
    public Location getLocation() { return location.clone(); }
    public UUID getOwnerUUID() { return ownerUUID; }

    /** Nombre del mundo: el real si está cargado, el guardado si no. Nunca null. */
    public String getWorldName() {
        World w = location.getWorld();
        return (w != null) ? w.getName() : worldName;
    }

    /** Solo para loadAll(): conserva el nombre del mundo aún no cargado. */
    public void setWorldName(String worldName) { this.worldName = worldName; }

    /**
     * Re-vincula la Location a un mundo recién cargado (p. ej. Multiverse
     * carga mundos después de habilitar los plugins). No-op si ya tiene mundo.
     */
    public void relinkWorld(World world) {
        if (location.getWorld() == null && world != null) location.setWorld(world);
    }

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
        return getWorldName() + ","
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