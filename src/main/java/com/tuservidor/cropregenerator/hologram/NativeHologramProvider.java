package com.tuservidor.cropregenerator.hologram;

import com.tuservidor.cropregenerator.CropRegeneratorPlugin;
import com.tuservidor.cropregenerator.model.RegeneratorBlock;
import net.kyori.adventure.text.Component;
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
 * No requiere plugins externos.
 */
public class NativeHologramProvider implements IHologramProvider {

    // LegacyComponentSerializer soporta & y &#RRGGBB (hex)
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder()
                    .character('&')
                    .hexColors()
                    .useUnusualXRepeatedCharacterHexFormat()
                    .build();

    private static final String PDC_KEY = "cropgen_hologram";

    private final CropRegeneratorPlugin plugin;
    private final NamespacedKey holoKey;

    private final Map<String, UUID> hologramUUIDs    = new HashMap<>();
    private final Map<String, Long> lastRenderedRegen = new HashMap<>();

    public NativeHologramProvider(CropRegeneratorPlugin plugin) {
        this.plugin  = plugin;
        this.holoKey = new NamespacedKey(plugin, PDC_KEY);
    }

    /** Limpia TextDisplays huérfanas de sesiones anteriores. Llamar en onEnable. */
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

    /** Verifica si un chunk tiene TextDisplays huérfanas y las elimina. */
    public void purgeChunk(org.bukkit.Chunk chunk) {
        if (chunk.getEntities().length == 0) return;
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof TextDisplay td
                    && td.getPersistentDataContainer().has(holoKey, PersistentDataType.BYTE)
                    && !hologramUUIDs.containsValue(td.getUniqueId())) {
                td.remove();
            }
        }
    }

    @Override
    public void spawnOrUpdate(RegeneratorBlock rb) {
        removeEntity(rb.getKey());
        lastRenderedRegen.remove(rb.getKey());

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

    @Override
    public void updateText(RegeneratorBlock rb) {
        UUID uid = hologramUUIDs.get(rb.getKey());
        if (uid == null) { spawnOrUpdate(rb); return; }

        Entity entity = plugin.getServer().getEntity(uid);
        if (!(entity instanceof TextDisplay td) || !td.isValid()) {
            hologramUUIDs.remove(rb.getKey());
            spawnOrUpdate(rb);
            return;
        }

        long currentRegen = rb.getSecondsUntilRegen();
        Long lastRegen    = lastRenderedRegen.get(rb.getKey());
        if (lastRegen != null && lastRegen == currentRegen) return;

        td.text(buildComponent(rb));
        lastRenderedRegen.put(rb.getKey(), currentRegen);
    }

    @Override
    public void remove(RegeneratorBlock rb) {
        removeEntity(rb.getKey());
        lastRenderedRegen.remove(rb.getKey());
    }

    @Override
    public void removeAll() {
        for (UUID uid : hologramUUIDs.values()) {
            Entity e = plugin.getServer().getEntity(uid);
            if (e != null && e.isValid()) e.remove();
        }
        hologramUUIDs.clear();
        lastRenderedRegen.clear();
    }

    // ── Helpers ──────────────────────────────────────────────

    private void removeEntity(String key) {
        UUID uid = hologramUUIDs.remove(key);
        if (uid == null) return;
        Entity e = plugin.getServer().getEntity(uid);
        if (e != null && e.isValid()) e.remove();
    }

    /**
     * Construye el Component del holograma.
     * Soporta:
     *   & codes  → &a, &l, &r, etc.
     *   Hex      → &#RRGGBB  (formato &#ffffff)
     * MiniMessage fue removido por impacto en rendimiento.
     */
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

        return LEGACY.deserialize(raw);
    }
}