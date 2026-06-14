package com.tuservidor.cropregenerator.hologram;

import com.tuservidor.cropregenerator.CropRegeneratorPlugin;
import com.tuservidor.cropregenerator.model.RegeneratorBlock;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
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
 * Proveedor nativo usando TextDisplay de Paper (1.19.4+).
 *
 * Optimización de texto:
 *  - Las líneas del config se dividen en "estáticas" (sin {next_regen})
 *    y "dinámicas" (contienen {next_regen}).
 *  - La parte estática se pre-parsea UNA SOLA VEZ al crear/mejorar el bloque
 *    y se cachea en staticCache.
 *  - Cada segundo solo se parsea la línea dinámica (una sola línea corta)
 *    y se combina con el Component cacheado.
 */
public class NativeHologramProvider {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder()
                    .character('&')
                    .hexColors()
                    .useUnusualXRepeatedCharacterHexFormat()
                    .build();

    private static final String PDC_KEY      = "cropgen_hologram";
    private static final String DYNAMIC_TAG  = "{next_regen}";

    private final CropRegeneratorPlugin plugin;
    private final NamespacedKey holoKey;

    private final Map<String, UUID>      hologramUUIDs = new HashMap<>();
    private final Set<UUID>               registeredUUIDs = new HashSet<>();
    private final Map<String, Long>      lastRenderedRegen = new HashMap<>();

    // key → Component pre-parseado de las líneas estáticas
    private final Map<String, Component> staticCache = new HashMap<>();

    // key → plantilla raw de la línea dinámica (solo el string con {next_regen})
    private final Map<String, String>    dynamicTemplate = new HashMap<>();

    // true si hay alguna línea dinámica en el config
    private boolean hasDynamicLine = false;

    public NativeHologramProvider(CropRegeneratorPlugin plugin) {
        this.plugin  = plugin;
        this.holoKey = new NamespacedKey(plugin, PDC_KEY);
        checkDynamicLines();
    }

    /** Detecta si hay líneas dinámicas en el config para evitar trabajo innecesario. */
    private void checkDynamicLines() {
        hasDynamicLine = plugin.getConfig().getStringList("hologram.lines")
                .stream().anyMatch(l -> l.contains(DYNAMIC_TAG));
    }

    // ── Limpieza ─────────────────────────────────────────────

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
        if (count > 0)
            plugin.getLogger().info("[HologramManager] Eliminadas " + count + " TextDisplay huérfanas.");
    }

    public void purgeChunk(org.bukkit.Chunk chunk) {
        if (chunk.getEntities().length == 0) return;
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof TextDisplay td
                    && td.getPersistentDataContainer().has(holoKey, PersistentDataType.BYTE)
                    && !registeredUUIDs.contains(td.getUniqueId())) {
                td.remove();
            }
        }
    }

    // ── API pública ──────────────────────────────────────────

    public void spawnOrUpdate(RegeneratorBlock rb) {
        removeEntity(rb.getKey());
        lastRenderedRegen.remove(rb.getKey());

        // Construir y cachear la parte estática
        buildStaticCache(rb);

        Location loc = rb.getLocation().clone().add(0.5,
                plugin.getConfig().getDouble("hologram.offset-y", 1.5), 0.5);
        if (loc.getWorld() == null) return;

        TextDisplay display = loc.getWorld().spawn(loc, TextDisplay.class, td -> {
            td.text(buildFull(rb, rb.getSecondsUntilRegen()));
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

        UUID uid = display.getUniqueId();
        hologramUUIDs.put(rb.getKey(), uid);
        registeredUUIDs.add(uid);
    }

    public void updateText(RegeneratorBlock rb) {
        UUID uid = hologramUUIDs.get(rb.getKey());
        if (uid == null) { spawnOrUpdate(rb); return; }

        Entity entity = plugin.getServer().getEntity(uid);
        if (!(entity instanceof TextDisplay td) || !td.isValid()) {
            hologramUUIDs.remove(rb.getKey());
            spawnOrUpdate(rb);
            return;
        }

        // Si no hay línea dinámica, nunca hay que actualizar el texto
        if (!hasDynamicLine) return;

        long currentRegen = rb.getSecondsUntilRegen();
        Long lastRegen    = lastRenderedRegen.get(rb.getKey());
        // Usar longValue() para evitar el fallo de == con Long > 127 (autoboxing)
        if (lastRegen != null && lastRegen.longValue() == currentRegen) return;

        // Solo parsear la línea dinámica, combinar con el cache estático
        td.text(buildFull(rb, currentRegen));
        lastRenderedRegen.put(rb.getKey(), currentRegen);
    }

    public void remove(RegeneratorBlock rb) {
        removeEntity(rb.getKey());
        lastRenderedRegen.remove(rb.getKey());
        staticCache.remove(rb.getKey());
        dynamicTemplate.remove(rb.getKey());
    }

    public void removeAll() {
        for (UUID uid : hologramUUIDs.values()) {
            Entity e = plugin.getServer().getEntity(uid);
            if (e != null && e.isValid()) e.remove();
        }
        hologramUUIDs.clear();
        registeredUUIDs.clear();
        lastRenderedRegen.clear();
        staticCache.clear();
        dynamicTemplate.clear();
    }

    // ── Helpers ──────────────────────────────────────────────

    private void removeEntity(String key) {
        UUID uid = hologramUUIDs.remove(key);
        if (uid == null) return;
        registeredUUIDs.remove(uid);
        Entity e = plugin.getServer().getEntity(uid);
        if (e != null && e.isValid()) e.remove();
    }

    /**
     * Pre-parsea todas las líneas que NO contienen {next_regen} y las guarda
     * como un único Component cacheado. También guarda la plantilla de la línea
     * dinámica para reemplazar solo el número cada tick.
     */
    private void buildStaticCache(RegeneratorBlock rb) {
        var upgradeLevel = plugin.getUpgradeManager().getLevel(rb.getLevel());
        List<String> lines = plugin.getConfig().getStringList("hologram.lines");

        TextComponent.Builder staticBuilder = Component.text();
        String dynTemplate = null;
        boolean firstStatic = true;

        for (String line : lines) {
            // Reemplazar placeholders estáticos
            String resolved = line
                    .replace("{level}",    String.valueOf(rb.getLevel()))
                    .replace("{radius}",   String.valueOf(upgradeLevel.radius()))
                    .replace("{interval}", String.valueOf(upgradeLevel.regenInterval()));

            if (resolved.contains(DYNAMIC_TAG)) {
                // Guardar plantilla dinámica con {next_regen} intacto
                dynTemplate = resolved;
            } else {
                if (!firstStatic) staticBuilder.appendNewline();
                staticBuilder.append(LEGACY.deserialize(resolved));
                firstStatic = false;
            }
        }

        staticCache.put(rb.getKey(), staticBuilder.build());
        if (dynTemplate != null) dynamicTemplate.put(rb.getKey(), dynTemplate);
    }

    /**
     * Combina el Component estático cacheado con la línea dinámica parseada
     * en tiempo real (solo una línea corta).
     */
    private Component buildFull(RegeneratorBlock rb, long secondsUntilRegen) {
        Component staticPart = staticCache.getOrDefault(rb.getKey(), Component.empty());
        String dynTemplate   = dynamicTemplate.get(rb.getKey());

        if (dynTemplate == null) return staticPart;

        String dynResolved = dynTemplate.replace(DYNAMIC_TAG, String.valueOf(secondsUntilRegen));
        Component dynPart  = LEGACY.deserialize(dynResolved);

        // Si hay parte estática, añadir salto de línea entre ambas
        // Comparar con hasChildren() evita el costoso Component.equals()
        if (staticPart instanceof TextComponent tc && (tc.content().length() > 0 || !tc.children().isEmpty())) {
            return staticPart.append(Component.newline()).append(dynPart);
        }
        return dynPart;
    }
}