package com.tuservidor.cropregenerator.managers;

import com.tuservidor.cropregenerator.CropRegeneratorPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;

import java.util.*;

/** Gestiona los tres árboles de mejoras y sus costes. */
public class UpgradeManager {
    public enum Tree { TIME, RADIUS, CROPS }
    public enum CostType { MONEY, XP, ITEMS, NONE }

    public record UpgradeCost(CostType type, double amount, Material item, String itemDisplay) {}

    public record UpgradeLevel(int level, int interval, int radius, List<String> crops, UpgradeCost cost) {}

    private final CropRegeneratorPlugin plugin;
    private final EnumMap<Tree, NavigableMap<Integer, UpgradeLevel>> levels = new EnumMap<>(Tree.class);
    private int maxTime = 1, maxRadius = 1, maxCrops = 1;

    public UpgradeManager(CropRegeneratorPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "upgrades.yml");
        if (!file.exists()) {
            plugin.saveResource("upgrades.yml", false);
        }
        reloadFrom(YamlConfiguration.loadConfiguration(file));
    }

    public void reloadFrom(ConfigurationSection root) {
        levels.clear();
        for (Tree tree : Tree.values()) levels.put(tree, new TreeMap<>());
        loadTreeFrom(root, Tree.TIME, "time");
        loadTreeFrom(root, Tree.RADIUS, "radius");
        loadTreeFrom(root, Tree.CROPS, "crops");
        maxTime = maxLevel(Tree.TIME);
        maxRadius = maxLevel(Tree.RADIUS);
        maxCrops = maxLevel(Tree.CROPS);
    }

    private void loadTreeFrom(ConfigurationSection root, Tree tree, String path) {
        ConfigurationSection section = root.getConfigurationSection(path);
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                int level = Integer.parseInt(key);
                ConfigurationSection s = section.getConfigurationSection(key);
                if (s == null) continue;
                int interval = s.getInt("interval", 60);
                int radius = s.getInt("radius", 5);
                List<String> crops = s.getStringList("crops").stream()
                        .map(String::toLowerCase).distinct().toList();
                UpgradeCost cost = parseCost(s.getConfigurationSection("cost"));
                levels.get(tree).put(level, new UpgradeLevel(level, interval, radius, crops, cost));
            } catch (NumberFormatException ignored) {
                plugin.getLogger().warning("Nivel inválido en upgrades.yml: " + path + "." + key);
            }
        }
    }

    private UpgradeCost parseCost(ConfigurationSection section) {
        if (section == null) return new UpgradeCost(CostType.NONE, 0, null, "");
        CostType type;
        try { type = CostType.valueOf(section.getString("type", "NONE").toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { type = CostType.NONE; }
        double amount = section.getDouble("amount", 0);
        Material item = null;
        String itemName = section.getString("item", "");
        if (type == CostType.ITEMS && !itemName.isBlank()) {
            try { item = Material.valueOf(itemName.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Material inválido en coste ITEMS: " + itemName);
            }
        }
        return new UpgradeCost(type, amount, item, section.getString("item-display", itemName));
    }

    public UpgradeLevel get(Tree tree, int level) {
        NavigableMap<Integer, UpgradeLevel> map = levels.get(tree);
        if (map == null || map.isEmpty()) return new UpgradeLevel(1, 60, 5, List.of(), new UpgradeCost(CostType.NONE, 0, null, ""));
        return map.getOrDefault(level, map.lastEntry().getValue());
    }

    public UpgradeLevel getNext(Tree tree, int currentLevel) {
        NavigableMap<Integer, UpgradeLevel> map = levels.get(tree);
        if (map == null) return null;
        Map.Entry<Integer, UpgradeLevel> next = map.higherEntry(currentLevel);
        return next == null ? null : next.getValue();
    }

    public void reload(CropRegeneratorPlugin ignored) { reload(); }
    public boolean levelExists(int level) { return levelExists(Tree.TIME, level) || levelExists(Tree.RADIUS, level) || levelExists(Tree.CROPS, level); }
    public int getMaxLevel() { return Math.max(maxTime, Math.max(maxRadius, maxCrops)); }

    public boolean levelExists(Tree tree, int level) {
        return levels.getOrDefault(tree, new TreeMap<>()).containsKey(level);
    }

    public int maxLevel(Tree tree) {
        NavigableMap<Integer, UpgradeLevel> map = levels.get(tree);
        return map == null || map.isEmpty() ? 1 : map.lastKey();
    }

    public int getMaxTime() { return maxTime; }
    public int getMaxRadius() { return maxRadius; }
    public int getMaxCrops() { return maxCrops; }

    public int getInterval(int level) { return get(Tree.TIME, level).interval(); }
    public int getRadius(int level) { return get(Tree.RADIUS, level).radius(); }

    public Set<String> getUnlockedCrops(int level, Map<String, Boolean> enabled) {
        Set<String> result = new LinkedHashSet<>();
        for (int i = 1; i <= level; i++) {
            for (String crop : get(Tree.CROPS, i).crops()) {
                if (enabled.getOrDefault(crop, true)) result.add(crop);
            }
        }
        return result;
    }
}
