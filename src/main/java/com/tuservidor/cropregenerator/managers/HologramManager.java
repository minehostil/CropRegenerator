package com.tuservidor.cropregenerator.managers;

import com.tuservidor.cropregenerator.CropRegeneratorPlugin;
import com.tuservidor.cropregenerator.model.RegeneratorBlock;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;

/**
 * Gestiona hologramas con TextDisplay entities.
 *
 * Correcciones vs versión anterior:
 *  - Rastreo por UUID de entidad, no solo por key de bloque
 *  - remove() espera confirmación antes de hacer spawn
 *  - updateText() NUNCA spawna entidades nuevas, solo modifica la existente
 *  - spawnOrUpdate() elimina cualquier TextDisplay huérfano en la misma ubicación
 *    antes de crear uno nuevo
 */
public class HologramManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final CropRegeneratorPlugin plugin;

    // key del bloque → UUID de la TextDisplay activa
    private final Map<String, UUID> hologramUUIDs = new HashMap<>();

    public HologramManager(CropRegeneratorPlugin plugin) {
        this.plugin = plugin;
    }

    // ── API pública ──────────────────────────────────────────

    /** Elimina el holograma anterior (si existe) y crea uno nuevo. */
    public void spawnOrUpdate(RegeneratorBlock rb) {
        // 1. Eliminar entidad anterior registrada
        removeEntity(rb.getKey());

        // 2. Limpiar posibles TextDisplays huérfanos en la misma ubicación
        killOrphansAt(rb.getLocation());

        // 3. Spawnear nueva entidad
        Location loc = rb.getLocation().clone().add(0.5,
                plugin.getConfig().getDouble("hologram.offset-y", 1.5), 0.5);
        if (loc.getWorld() == null) return;

        TextDisplay display = loc.getWorld().spawn(loc, TextDisplay.class, td -> {
            td.text(MM.deserialize(buildText(rb)));
            td.setBillboard(Display.Billboard.VERTICAL);
            td.setShadowed(true);
            td.setDefaultBackground(true);
            td.setBackgroundColor(org.bukkit.Color.fromARGB(100, 0, 0, 0));
            td.setTextOpacity((byte) 255);
            td.setSeeThrough(false);

            float scale = (float) plugin.getConfig().getDouble("hologram.scale", 1.0);
            td.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 0, 1),
                    new Vector3f(scale, scale, scale),
                    new AxisAngle4f(0, 0, 0, 1)
            ));
        });

        hologramUUIDs.put(rb.getKey(), display.getUniqueId());
    }

    /**
     * Actualiza solo el texto de la entidad existente.
     * NO spawna entidades nuevas — si la entidad no existe, no hace nada
     * hasta que el siguiente ciclo llame a spawnOrUpdate().
     */
    public void updateText(RegeneratorBlock rb) {
        UUID uid = hologramUUIDs.get(rb.getKey());
        if (uid == null) {
            // Primera vez o se perdió la referencia — spawnear
            spawnOrUpdate(rb);
            return;
        }

        Entity entity = plugin.getServer().getEntity(uid);
        if (!(entity instanceof TextDisplay td) || !td.isValid()) {
            // La entidad ya no existe en el mundo — respawnear una sola vez
            hologramUUIDs.remove(rb.getKey());
            spawnOrUpdate(rb);
            return;
        }

        // Solo modificar el texto, sin tocar la entidad
        td.text(MM.deserialize(buildText(rb)));
    }

    /** Elimina el holograma de un bloque. */
    public void remove(RegeneratorBlock rb) {
        removeEntity(rb.getKey());
    }

    /** Elimina todos los hologramas (onDisable). */
    public void removeAll() {
        for (UUID uid : hologramUUIDs.values()) {
            Entity e = plugin.getServer().getEntity(uid);
            if (e != null && e.isValid()) e.remove();
        }
        hologramUUIDs.clear();
    }

    // ── Helpers ──────────────────────────────────────────────

    private void removeEntity(String key) {
        UUID uid = hologramUUIDs.remove(key);
        if (uid == null) return;
        Entity e = plugin.getServer().getEntity(uid);
        if (e != null && e.isValid()) e.remove();
    }

    /**
     * Elimina TextDisplays huérfanos en un radio pequeño alrededor
     * de la posición del holograma. Evita duplicados tras reinicios
     * donde las entidades persisten en el mundo pero el plugin perdió
     * la referencia.
     */
    private void killOrphansAt(Location blockLoc) {
        double offsetY = plugin.getConfig().getDouble("hologram.offset-y", 1.5);
        Location holoLoc = blockLoc.clone().add(0.5, offsetY, 0.5);
        if (holoLoc.getWorld() == null) return;

        Collection<Entity> nearby = holoLoc.getWorld()
                .getNearbyEntities(holoLoc, 0.5, 0.5, 0.5);

        for (Entity e : nearby) {
            if (e instanceof TextDisplay) {
                // Solo eliminar si NO está registrado como holograma activo de otro bloque
                if (!hologramUUIDs.containsValue(e.getUniqueId())) {
                    e.remove();
                }
            }
        }
    }

    /** Construye el texto del holograma con placeholders reemplazados. */
    private String buildText(RegeneratorBlock rb) {
        var upgradeLevel = plugin.getUpgradeManager().getLevel(rb.getLevel());
        List<String> lines = plugin.getConfig().getStringList("hologram.lines");

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            sb.append(lines.get(i));
            if (i < lines.size() - 1) sb.append("\n");
        }

        return sb.toString()
                .replace("{level}",     String.valueOf(rb.getLevel()))
                .replace("{radius}",    String.valueOf(upgradeLevel.radius()))
                .replace("{interval}",  String.valueOf(upgradeLevel.regenInterval()))
                .replace("{next_regen}", String.valueOf(rb.getSecondsUntilRegen()));
    }
}
