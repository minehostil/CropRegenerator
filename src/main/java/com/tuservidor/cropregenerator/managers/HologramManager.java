package com.tuservidor.cropregenerator.managers;

import com.tuservidor.cropregenerator.CropRegeneratorPlugin;
import com.tuservidor.cropregenerator.model.RegeneratorBlock;
import net.kyori.adventure.text.Component;
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
 * Optimizaciones:
 *  - Cache de Component por bloque: solo se llama MM.deserialize() cuando
 *    el texto realmente cambia (el countdown bajó un segundo).
 *  - La plantilla estática (nivel, radio, intervalo) se cachea por separado
 *    y solo se recalcula al mejorar el bloque (spawnOrUpdate).
 *  - setPersistent(false) + purgeAll() evitan duplicados por recarga de chunk.
 */
public class HologramManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static final String PDC_KEY = "cropgen_hologram";

    private final CropRegeneratorPlugin plugin;
    private final NamespacedKey holoKey;

    // key → UUID de la TextDisplay activa
    private final Map<String, UUID> hologramUUIDs = new HashMap<>();

    // key → último valor de next_regen renderizado (evita re-parsear si no cambió)
    private final Map<String, Long> lastRenderedRegen = new HashMap<>();

    // key → Component cacheado listo para aplicar a la TextDisplay
    private final Map<String, Component> componentCache = new HashMap<>();

    public HologramManager(CropRegeneratorPlugin plugin) {
        this.plugin  = plugin;
        this.holoKey = new NamespacedKey(plugin, PDC_KEY);
    }

    // ── Limpieza inicial ─────────────────────────────────────

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

    /** Elimina el holograma anterior y crea uno nuevo. Limpia el cache del bloque. */
    public void spawnOrUpdate(RegeneratorBlock rb) {
        removeEntity(rb.getKey());

        // Limpiar cache para forzar re-render completo
        lastRenderedRegen.remove(rb.getKey());
        componentCache.remove(rb.getKey());

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
            td.setPersistent(false);
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
     * Actualiza el texto solo si el countdown cambió desde el último render.
     * Si next_regen es el mismo que el tick anterior, no hace nada.
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

        long currentRegen = rb.getSecondsUntilRegen();
        Long lastRegen    = lastRenderedRegen.get(rb.getKey());

        // Si el countdown no cambió, reutilizar el Component cacheado sin re-parsear
        if (lastRegen != null && lastRegen == currentRegen) return;

        Component component = buildComponent(rb);
        td.text(component);
        lastRenderedRegen.put(rb.getKey(), currentRegen);
    }

    /** Elimina el holograma de un bloque. */
    public void remove(RegeneratorBlock rb) {
        removeEntity(rb.getKey());
        lastRenderedRegen.remove(rb.getKey());
        componentCache.remove(rb.getKey());
    }

    /** Elimina todos los hologramas (onDisable). */
    public void removeAll() {
        for (UUID uid : hologramUUIDs.values()) {
            Entity e = plugin.getServer().getEntity(uid);
            if (e != null && e.isValid()) e.remove();
        }
        hologramUUIDs.clear();
        lastRenderedRegen.clear();
        componentCache.clear();
    }

    // ── Helpers ──────────────────────────────────────────────

    private void removeEntity(String key) {
        UUID uid = hologramUUIDs.remove(key);
        if (uid == null) return;
        Entity e = plugin.getServer().getEntity(uid);
        if (e != null && e.isValid()) e.remove();
    }

    private Component buildComponent(RegeneratorBlock rb) {
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

        return MM.deserialize(convertLegacyToMiniMessage(raw));
    }

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
                .replace("&A", "<green>")        .replace("&B", "<aqua>")
                .replace("&C", "<red>")          .replace("&D", "<light_purple>")
                .replace("&E", "<yellow>")       .replace("&F", "<white>")
                .replace("&K", "<obfuscated>")   .replace("&L", "<bold>")
                .replace("&M", "<strikethrough>").replace("&N", "<underlined>")
                .replace("&O", "<italic>")       .replace("&R", "<reset>");
    }
}
