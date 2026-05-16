package com.tuservidor.cropregenerator.managers;

import com.tuservidor.cropregenerator.CropRegeneratorPlugin;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;

/**
 * Lee y provee la configuración de cada nivel de mejora desde config.yml.
 */
public class UpgradeManager {

    public record UpgradeLevel(int level, int radius, int regenInterval,
                               int maxBlocksPerIsland, String displayName) {}

    private final Map<Integer, UpgradeLevel> levels = new HashMap<>();
    private int maxLevel = 1;

    public UpgradeManager(CropRegeneratorPlugin plugin) {
        reload(plugin);
    }

    public void reload(CropRegeneratorPlugin plugin) {
        levels.clear();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("upgrades");
        if (sec == null) return;

        for (String key : sec.getKeys(false)) {
            try {
                int lvl      = Integer.parseInt(key);
                int radius   = sec.getInt(key + ".radius", 5);
                int interval = sec.getInt(key + ".regen-interval", 60);
                int maxBlocks= sec.getInt(key + ".max-blocks-per-island", 1);
                String name  = sec.getString(key + ".display-name", "Regenerador Nivel " + lvl);

                levels.put(lvl, new UpgradeLevel(lvl, radius, interval, maxBlocks, name));
                if (lvl > maxLevel) maxLevel = lvl;
            } catch (NumberFormatException ignored) {}
        }
    }

    public UpgradeLevel getLevel(int level) {
        return levels.getOrDefault(level, levels.get(1));
    }

    public int getMaxLevel() { return maxLevel; }

    public boolean levelExists(int level) { return levels.containsKey(level); }
}