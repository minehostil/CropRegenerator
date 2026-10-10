package com.tuservidor.cropregenerator.managers;

import com.tuservidor.cropregenerator.CropRegeneratorPlugin;
import com.tuservidor.cropregenerator.model.RegeneratorBlock;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Locale;

/**
 * Dibuja el contorno del ÁREA de regeneración (no del bloque).
 *
 * Formas (particles.shape):
 *  - CUBE:   contorno completo del cubo que regenera FAWE (12 aristas). Por defecto.
 *  - SQUARE: contorno cuadrado a la altura del bloque.
 *  - CIRCLE: círculo a la altura del bloque.
 *
 * Separación (particles.spacing): distancia en bloques entre partículas.
 * 2.0 = una partícula cada 2 bloques. Cuanto mayor, menos partículas.
 */
public class ParticleManager {
    private final CropRegeneratorPlugin plugin;
    private BukkitTask task;

    public ParticleManager(CropRegeneratorPlugin plugin) { this.plugin = plugin; }

    public void start() {
        stop();
        long interval = Math.max(1L, plugin.getConfig().getLong("particles.interval-ticks", 10L));
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
    }

    private void tick() {
        if (!plugin.getConfig().getBoolean("particles.enabled", true)) return;
        Particle particle = parseParticle();
        String shape = plugin.getConfig().getString("particles.shape", "CUBE").toUpperCase(Locale.ROOT);
        // Separación en bloques entre partículas (mínimo 0.5 para evitar saturación)
        double spacing = Math.max(0.5, plugin.getConfig().getDouble("particles.spacing", 2.0));

        for (RegeneratorBlock rb : plugin.getBlockDataManager().getAllBlocks()) {
            if (!rb.isParticlesEnabled()) continue;
            Location center = rb.getLocation();
            World world = center.getWorld();
            if (world == null || !world.isChunkLoaded(center.getBlockX() >> 4, center.getBlockZ() >> 4)) continue;

            // Radio REAL del área, el mismo que usa RegeneratorManager al regenerar
            int radius = plugin.getUpgradeManager().getRadius(rb.getRadiusLevel());
            Location base = center.clone().add(0.5, 0.5, 0.5);

            switch (shape) {
                case "CIRCLE" -> drawCircle(world, particle, base, radius, spacing);
                case "SQUARE" -> drawSquare(world, particle, base, radius, spacing);
                default -> drawCube(world, particle, base, radius, spacing);
            }
        }
    }

    private Particle parseParticle() {
        try { return Particle.valueOf(plugin.getConfig().getString("particles.type", "END_ROD").toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { return Particle.END_ROD; }
    }

    /**
     * Contorno completo del cubo de regeneración.
     * La región de FAWE va de (centro - radius) a (centro + radius) INCLUSIVO en cada eje,
     * así que el medio-lado en coordenadas continuas es radius + 0.5.
     */
    private void drawCube(World world, Particle p, Location center, int radius, double spacing) {
        double e = radius + 0.5;
        double x = center.getX(), y = center.getY(), z = center.getZ();
        Vector[] c = {
                new Vector(x - e, y - e, z - e), new Vector(x + e, y - e, z - e),
                new Vector(x + e, y - e, z + e), new Vector(x - e, y - e, z + e),
                new Vector(x - e, y + e, z - e), new Vector(x + e, y + e, z - e),
                new Vector(x + e, y + e, z + e), new Vector(x - e, y + e, z + e)
        };
        // Base (4 aristas)
        drawEdge(world, p, c[0], c[1], spacing); drawEdge(world, p, c[1], c[2], spacing);
        drawEdge(world, p, c[2], c[3], spacing); drawEdge(world, p, c[3], c[0], spacing);
        // Techo (4 aristas)
        drawEdge(world, p, c[4], c[5], spacing); drawEdge(world, p, c[5], c[6], spacing);
        drawEdge(world, p, c[6], c[7], spacing); drawEdge(world, p, c[7], c[4], spacing);
        // Verticales (4 aristas)
        for (int i = 0; i < 4; i++) drawEdge(world, p, c[i], c[i + 4], spacing);
    }

    /** Contorno cuadrado a la altura del bloque. */
    private void drawSquare(World world, Particle p, Location center, int radius, double spacing) {
        double e = radius + 0.5;
        double x = center.getX(), y = center.getY(), z = center.getZ();
        Vector a = new Vector(x - e, y, z - e), b = new Vector(x + e, y, z - e),
               c = new Vector(x + e, y, z + e), d = new Vector(x - e, y, z + e);
        drawEdge(world, p, a, b, spacing); drawEdge(world, p, b, c, spacing);
        drawEdge(world, p, c, d, spacing); drawEdge(world, p, d, a, spacing);
    }

    /** Círculo a la altura del bloque — puntos calculados con el mismo spacing. */
    private void drawCircle(World world, Particle p, Location center, int radius, double spacing) {
        double r = radius + 0.5;
        int points = Math.max(8, (int) Math.ceil((2.0 * Math.PI * r) / spacing));
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0 * i) / points;
            world.spawnParticle(p, center.getX() + Math.cos(angle) * r,
                    center.getY(), center.getZ() + Math.sin(angle) * r, 1, 0, 0, 0, 0);
        }
    }

    /** Interpola puntos a lo largo de una arista: 1 punto cada 'spacing' bloques. */
    private void drawEdge(World world, Particle p, Vector from, Vector to, double spacing) {
        double length = from.distance(to);
        int points = Math.max(1, (int) Math.ceil(length / spacing));
        for (int i = 0; i <= points; i++) {
            double t = (double) i / points;
            world.spawnParticle(p,
                    from.getX() + (to.getX() - from.getX()) * t,
                    from.getY() + (to.getY() - from.getY()) * t,
                    from.getZ() + (to.getZ() - from.getZ()) * t, 1, 0, 0, 0, 0);
        }
    }
}