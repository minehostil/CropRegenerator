package com.tuservidor.cropregenerator.managers;

import com.tuservidor.cropregenerator.CropRegeneratorPlugin;
import com.tuservidor.cropregenerator.model.RegeneratorBlock;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Crea y gestiona hologramas con TextDisplay entities (1.19.4+).
 * Cada RegeneratorBlock tiene UNA TextDisplay multi-línea.
 */
public class HologramManager {

    private final CropRegeneratorPlugin plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    // key → TextDisplay entity
    private final Map<String, TextDisplay> holograms = new HashMap<>();

    public HologramManager(CropRegeneratorPlugin plugin) {
        this.plugin = plugin;
    }

    /** Spawna o actualiza el holograma de un bloque. */
    public void spawnOrUpdate(RegeneratorBlock rb) {
        remove(rb); // quitar el anterior si existe

        Location loc = rb.getLocation().clone().add(0.5,
                plugin.getConfig().getDouble("hologram.offset-y", 1.5), 0.5);

        if (loc.getWorld() == null) return;

        var upgradeLevel = plugin.getUpgradeManager().getLevel(rb.getLevel());
        List<String> lines = plugin.getConfig().getStringList("hologram.lines");

        // Construir texto completo con placeholders
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            sb.append(lines.get(i));
            if (i < lines.size() - 1) sb.append("\n");
        }

        String rawText = sb.toString()
                .replace("{level}",    String.valueOf(rb.getLevel()))
                .replace("{radius}",   String.valueOf(upgradeLevel.radius()))
                .replace("{interval}", String.valueOf(upgradeLevel.regenInterval()))
                .replace("{next_regen}", String.valueOf(rb.getSecondsUntilRegen()));

        TextDisplay display = loc.getWorld().spawn(loc, TextDisplay.class, td -> {
            td.text(mm.deserialize(rawText));
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

        holograms.put(rb.getKey(), display);
    }

    /** Actualiza solo el texto (para el countdown sin re-spawnear). */
    public void updateText(RegeneratorBlock rb) {
        TextDisplay display = holograms.get(rb.getKey());
        if (display == null || !display.isValid()) {
            spawnOrUpdate(rb);
            return;
        }

        var upgradeLevel = plugin.getUpgradeManager().getLevel(rb.getLevel());
        List<String> lines = plugin.getConfig().getStringList("hologram.lines");

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            sb.append(lines.get(i));
            if (i < lines.size() - 1) sb.append("\n");
        }

        String rawText = sb.toString()
                .replace("{level}",    String.valueOf(rb.getLevel()))
                .replace("{radius}",   String.valueOf(upgradeLevel.radius()))
                .replace("{interval}", String.valueOf(upgradeLevel.regenInterval()))
                .replace("{next_regen}", String.valueOf(rb.getSecondsUntilRegen()));

        display.text(mm.deserialize(rawText));
    }

    /** Elimina el holograma de un bloque. */
    public void remove(RegeneratorBlock rb) {
        TextDisplay td = holograms.remove(rb.getKey());
        if (td != null && td.isValid()) td.remove();
    }

    /** Elimina todos los hologramas (onDisable). */
    public void removeAll() {
        holograms.values().forEach(td -> { if (td.isValid()) td.remove(); });
        holograms.clear();
    }
}
