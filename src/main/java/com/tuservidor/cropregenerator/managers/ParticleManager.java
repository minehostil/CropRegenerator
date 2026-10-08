package com.tuservidor.cropregenerator.managers;

import com.tuservidor.cropregenerator.CropRegeneratorPlugin;
import com.tuservidor.cropregenerator.model.RegeneratorBlock;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

/** Dibuja un único anillo horizontal alrededor del regenerador. */
public class ParticleManager {
    private final CropRegeneratorPlugin plugin;
    private long taskId = -1L;

    public ParticleManager(CropRegeneratorPlugin plugin) { this.plugin = plugin; }

    public void start() {
        stop();
        long interval = Math.max(1L, plugin.getConfig().getLong("particles.interval-ticks", 10L));
        taskId = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, interval, interval).getTaskId();
    }

    public void stop() {
        if (taskId != -1L) { plugin.getServer().getScheduler().cancelTask((int) taskId); taskId = -1L; }
    }

    private void tick() {
        if (!plugin.getConfig().getBoolean("particles.enabled", true)) return;
        Particle particle;
        try { particle = Particle.valueOf(plugin.getConfig().getString("particles.type", "END_ROD").toUpperCase()); }
        catch (IllegalArgumentException ex) { particle = Particle.END_ROD; }
        double radius = Math.max(0.1, plugin.getConfig().getDouble("particles.radius", 0.8));
        int points = Math.max(8, plugin.getConfig().getInt("particles.points", 24));
        for (RegeneratorBlock rb : plugin.getBlockDataManager().getAllBlocks()) {
            if (!rb.isParticlesEnabled()) continue;
            Location center = rb.getLocation();
            World world = center.getWorld();
            if (world == null || !world.isChunkLoaded(center.getBlockX() >> 4, center.getBlockZ() >> 4)) continue;
            Location base = center.clone().add(0.5, 0.5, 0.5);
            for (int i = 0; i < points; i++) {
                double angle = (Math.PI * 2.0 * i) / points;
                world.spawnParticle(particle, base.getX() + Math.cos(angle) * radius,
                        base.getY(), base.getZ() + Math.sin(angle) * radius, 1, 0, 0, 0, 0);
            }
        }
    }
}
