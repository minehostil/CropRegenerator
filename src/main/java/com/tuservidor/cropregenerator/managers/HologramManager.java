package com.tuservidor.cropregenerator.managers;

import com.tuservidor.cropregenerator.CropRegeneratorPlugin;
import com.tuservidor.cropregenerator.model.RegeneratorBlock;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;

/**
 * Gestiona hologramas con TextDisplay entities.
 *
 * Solución al duplicado por cambio de mundo:
 *  - Cada TextDisplay se marca con PDC al crearse (clave "cropgen_hologram")
 *  - Al iniciar el plugin se escanean todos los mundos y se eliminan
 *    todas las TextDisplay con esa marca (limpiar entidades persistentes
 *    que Paper guardó en el NBT del chunk)
 *  - setPersistent(false) evita que Paper guarde la entidad en el chunk,
 *    así no reaparece al recargar el chunk
 */
public class HologramManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();

    // PDC key que marca a las TextDisplay de este plugin
    private static final String PDC_KEY = "cropgen_hologram";

    private final CropRegeneratorPlugin plugin;
    private final NamespacedKey holoKey;

    // key del bloque → UUID de la TextDisplay activa
    private final Map<String, UUID> hologramUUIDs = new HashMap<>();

    public HologramManager(CropRegeneratorPlugin plugin) {
        this.plugin  = plugin;
        this.holoKey = new NamespacedKey(plugin, PDC_KEY);
    }

    // ── Limpieza inicial ─────────────────────────────────────

    /**
     * Debe llamarse en onEnable() ANTES de spawnear hologramas nuevos.
     * Recorre todos los chunks cargados de todos los mundos y elimina
     * cualquier TextDisplay marcada con el PDC de este plugin.
     */
    public void purgeAll() {
        int count = 0;
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof TextDisplay td
                        && td.getPersistentDataContainer().has(holoKey, PersistentDataType.BYTE)) {
                    td.remove();
                    count++;
                }
            }
        }
        if (count > 0) {
            plugin.getLogger().info("[HologramManager] Eliminadas " + count
                    + " TextDisplay huérfanas de sesiones anteriores.");
        }
    }

    // ── API pública ──────────────────────────────────────────

    /** Elimina el holograma anterior (si existe) y crea uno nuevo. */
    public void spawnOrUpdate(RegeneratorBlock rb) {
        // 1. Eliminar entidad anterior registrada
        removeEntity(rb.getKey());

        // 2. Spawnear nueva entidad
        Location loc = rb.getLocation().clone().add(0.5,
                plugin.getConfig().getDouble("hologram.offset-y", 1.5), 0.5);
        if (loc.getWorld() == null) return;

        TextDisplay display = loc.getWorld().spawn(loc, TextDisplay.class, td -> {
            td.text(buildComponent(rb));
            td.setBillboard(Display.Billboard.VERTICAL);
            td.setShadowed(true);
            td.setDefaultBackground(true);
            td.setBackgroundColor(org.bukkit.Color.fromARGB(100, 0, 0, 0));
            td.setTextOpacity((byte) 255);
            td.setSeeThrough(false);

            // NO persistir — Paper no guardará esta entidad en el NBT del chunk
            td.setPersistent(false);

            // Marcar con PDC para poder limpiarla si de alguna forma persiste
            td.getPersistentDataContainer().set(holoKey, PersistentDataType.BYTE, (byte) 1);

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
     * NO spawna entidades nuevas por sí solo — si la entidad no existe,
     * llama a spawnOrUpdate() una sola vez.
     */
    public void updateText(RegeneratorBlock rb) {
        UUID uid = hologramUUIDs.get(rb.getKey());
        if (uid == null) {
            spawnOrUpdate(rb);
            return;
        }

        Entity entity = plugin.getServer().getEntity(uid);
        if (!(entity instanceof TextDisplay td) || !td.isValid()) {
            hologramUUIDs.remove(rb.getKey());
            spawnOrUpdate(rb);
            return;
        }

        td.text(buildComponent(rb));
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
     * Construye el texto del holograma.
     * Soporta MiniMessage (<red>, <gradient:...>, etc.)
     * y colores legacy con & (&a, &b, &l, etc.).
     * Ambos formatos pueden mezclarse en la misma línea.
     */
    private net.kyori.adventure.text.Component buildComponent(RegeneratorBlock rb) {
        var upgradeLevel = plugin.getUpgradeManager().getLevel(rb.getLevel());
        List<String> lines = plugin.getConfig().getStringList("hologram.lines");

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            sb.append(lines.get(i));
            if (i < lines.size() - 1) sb.append("\n");
        }

        String raw = sb.toString()
                .replace("{level}",      String.valueOf(rb.getLevel()))
                .replace("{radius}",     String.valueOf(upgradeLevel.radius()))
                .replace("{interval}",   String.valueOf(upgradeLevel.regenInterval()))
                .replace("{next_regen}", String.valueOf(rb.getSecondsUntilRegen()));

        // Convertir & codes a tags de MiniMessage antes de parsear
        // Ejemplo: &aHola &lMundo → <green>Hola <bold>Mundo
        // Hacemos esto convirtiendo primero el legacy a Component y luego
        // serializando a MiniMessage para un parse unificado
        String converted = convertLegacyToMiniMessage(raw);
        return MM.deserialize(converted);
    }

    /**
     * Convierte códigos & a sus equivalentes MiniMessage.
     * Solo actúa sobre los & seguidos de un código válido (0-9, a-f, k-o, r).
     */
    private String convertLegacyToMiniMessage(String input) {
        return input
                .replace("&0", "<black>")        .replace("&1", "<dark_blue>")
                .replace("&2", "<dark_green>")   .replace("&3", "<dark_aqua>")
                .replace("&4", "<dark_red>")     .replace("&5", "<dark_purple>")
                .replace("&6", "<gold>")         .replace("&7", "<gray>")
                .replace("&8", "<dark_gray>")    .replace("&9", "<blue>")
                .replace("&a", "<green>")        .replace("&b", "<aqua>")
                .replace("&c", "<red>")          .replace("&d", "<light_purple>")
                .replace("&e", "<yellow>")       .replace("&f", "<white>")
                .replace("&k", "<obfuscated>")   .replace("&l", "<bold>")
                .replace("&m", "<strikethrough>").replace("&n", "<underlined>")
                .replace("&o", "<italic>")       .replace("&r", "<reset>")
                // Mayúsculas también
                .replace("&A", "<green>")        .replace("&B", "<aqua>")
                .replace("&C", "<red>")          .replace("&D", "<light_purple>")
                .replace("&E", "<yellow>")       .replace("&F", "<white>")
                .replace("&K", "<obfuscated>")   .replace("&L", "<bold>")
                .replace("&M", "<strikethrough>").replace("&N", "<underlined>")
                .replace("&O", "<italic>")       .replace("&R", "<reset>");
    }
}
