package com.tuservidor.cropregenerator.hologram;

import com.tuservidor.cropregenerator.CropRegeneratorPlugin;
import com.tuservidor.cropregenerator.model.RegeneratorBlock;
import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.HologramManager;
import de.oliver.fancyholograms.api.hologram.Hologram;
import de.oliver.fancyholograms.api.data.TextHologramData;
import org.bukkit.Location;
import org.bukkit.entity.Display;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Proveedor de hologramas usando FancyHolograms API.
 *
 * Flujo:
 *  - spawnOrUpdate: crea TextHologramData, instancia Hologram via manager.create(),
 *    lo registra con manager.addHologram() y setPersistent(false) para no guardarlo.
 *  - updateText: modifica el texto del hologram y llama queueUpdate().
 *  - remove: manager.removeHologram() por nombre.
 */
public class FancyHologramsProvider implements IHologramProvider {

    private final CropRegeneratorPlugin plugin;

    // key del bloque → nombre del Hologram en FancyHolograms
    private final Map<String, String> holoNames        = new HashMap<>();
    private final Map<String, Long>   lastRenderedRegen = new HashMap<>();

    public FancyHologramsProvider(CropRegeneratorPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void spawnOrUpdate(RegeneratorBlock rb) {
        remove(rb);

        String holoName = buildHoloName(rb);
        Location loc    = rb.getLocation().clone().add(0.5,
                plugin.getConfig().getDouble("hologram.offset-y", 1.5), 0.5);
        if (loc.getWorld() == null) return;

        // Construir el texto multilinea (FancyHolograms usa List<String> como líneas)
        TextHologramData data = new TextHologramData(holoName, loc);
        data.setText(buildLines(rb));
        data.setBillboard(Display.Billboard.VERTICAL);

        HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
        Hologram hologram = manager.create(data);

        // No persistir — no queremos que FancyHolograms lo guarde en disco
        hologram.setPersistent(false);

        manager.addHologram(hologram);

        holoNames.put(rb.getKey(), holoName);
        lastRenderedRegen.remove(rb.getKey());
    }

    @Override
    public void updateText(RegeneratorBlock rb) {
        String holoName = holoNames.get(rb.getKey());
        if (holoName == null) {
            spawnOrUpdate(rb);
            return;
        }

        HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
        Hologram hologram = manager.getHologram(holoName).orElse(null);
        if (hologram == null) {
            holoNames.remove(rb.getKey());
            spawnOrUpdate(rb);
            return;
        }

        long currentRegen = rb.getSecondsUntilRegen();
        Long lastRegen    = lastRenderedRegen.get(rb.getKey());
        if (lastRegen != null && lastRegen == currentRegen) return;

        // Modificar texto y notificar a FancyHolograms
        if (hologram.getData() instanceof TextHologramData textData) {
            textData.setText(buildLines(rb));
            hologram.queueUpdate();
        }

        lastRenderedRegen.put(rb.getKey(), currentRegen);
    }

    @Override
    public void remove(RegeneratorBlock rb) {
        String holoName = holoNames.remove(rb.getKey());
        lastRenderedRegen.remove(rb.getKey());
        if (holoName == null) return;

        FancyHologramsPlugin.get().getHologramManager().removeHologram(holoName);
    }

    @Override
    public void removeAll() {
        HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
        for (String name : holoNames.values()) {
            manager.removeHologram(name);
        }
        holoNames.clear();
        lastRenderedRegen.clear();
    }

    // ── Helpers ──────────────────────────────────────────────

    /** Nombre único para el Hologram — FancyHolograms usa string como ID. */
    private String buildHoloName(RegeneratorBlock rb) {
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