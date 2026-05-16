package com.tuservidor.cropregenerator.hologram;

import com.tuservidor.cropregenerator.CropRegeneratorPlugin;
import com.tuservidor.cropregenerator.model.RegeneratorBlock;
import dev.geco.gholo.api.GHoloAPI;
import dev.geco.gholo.object.holo.GHolo;
import dev.geco.gholo.object.simple.SimpleLocation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Proveedor de hologramas usando GHolo API.
 *
 * Cada RegeneratorBlock tiene un GHolo con ID "cropgen_<key>".
 * El contenido se actualiza con setAllHoloRowContent() solo cuando
 * el countdown cambia (mismo cache que NativeHologramProvider).
 */
public class GHoloProvider implements IHologramProvider {

    private final CropRegeneratorPlugin plugin;

    // key del bloque → ID del GHolo
    private final Map<String, String> holoIds = new HashMap<>();

    // key → último countdown renderizado
    private final Map<String, Long> lastRenderedRegen = new HashMap<>();

    public GHoloProvider(CropRegeneratorPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void spawnOrUpdate(RegeneratorBlock rb) {
        remove(rb);

        String holoId = buildHoloId(rb);
        org.bukkit.Location loc = rb.getLocation().clone().add(0.5,
                plugin.getConfig().getDouble("hologram.offset-y", 1.5), 0.5);

        if (loc.getWorld() == null) return;

        SimpleLocation simpleLoc = new SimpleLocation(
                loc.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(),
                0f, 0f);

        GHolo holo = GHoloAPI.createHolo(holoId, simpleLoc);
        if (holo == null) {
            plugin.getLogger().warning("[GHolo] No se pudo crear el holograma: " + holoId);
            return;
        }

        for (String line : buildLines(rb)) {
            GHoloAPI.addHoloRow(holo, line);
        }

        holoIds.put(rb.getKey(), holoId);
        lastRenderedRegen.remove(rb.getKey());
    }

    @Override
    public void updateText(RegeneratorBlock rb) {
        String holoId = holoIds.get(rb.getKey());
        if (holoId == null) {
            spawnOrUpdate(rb);
            return;
        }

        GHolo holo = GHoloAPI.getHolo(holoId);
        if (holo == null) {
            holoIds.remove(rb.getKey());
            spawnOrUpdate(rb);
            return;
        }

        long currentRegen = rb.getSecondsUntilRegen();
        Long lastRegen    = lastRenderedRegen.get(rb.getKey());
        if (lastRegen != null && lastRegen == currentRegen) return;

        GHoloAPI.setAllHoloRowContent(holo, buildLines(rb));
        lastRenderedRegen.put(rb.getKey(), currentRegen);
    }

    @Override
    public void remove(RegeneratorBlock rb) {
        String holoId = holoIds.remove(rb.getKey());
        lastRenderedRegen.remove(rb.getKey());
        if (holoId == null) return;

        GHolo holo = GHoloAPI.getHolo(holoId);
        if (holo != null) GHoloAPI.removeHolo(holo);
    }

    @Override
    public void removeAll() {
        for (String holoId : holoIds.values()) {
            GHolo holo = GHoloAPI.getHolo(holoId);
            if (holo != null) GHoloAPI.removeHolo(holo);
        }
        holoIds.clear();
        lastRenderedRegen.clear();
    }

    // ── Helpers ──────────────────────────────────────────────

    private String buildHoloId(RegeneratorBlock rb) {
        return "cropgen_" + rb.getKey().replace(",", "_");
    }

    private List<String> buildLines(RegeneratorBlock rb) {
        var upgradeLevel = plugin.getUpgradeManager().getLevel(rb.getLevel());
        return plugin.getConfig().getStringList("hologram.lines").stream()
                .map(line -> line
                        .replace("{level}",      String.valueOf(rb.getLevel()))
                        .replace("{radius}",     String.valueOf(upgradeLevel.radius()))
                        .replace("{interval}",   String.valueOf(upgradeLevel.regenInterval()))
                        .replace("{next_regen}", String.valueOf(rb.getSecondsUntilRegen())))
                .toList();
    }
}