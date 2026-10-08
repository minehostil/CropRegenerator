package com.tuservidor.cropregenerator.util;

import com.tuservidor.cropregenerator.CropRegeneratorPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Utilidades para crear e identificar el ítem del bloque regenerador.
 *
 * Guarda los TRES niveles (time/radius/crops) por separado en el PDC para
 * evitar el exploit de romper un bloque mejorado en un solo árbol y recolocarlo
 * con ese nivel aplicado a los tres.
 *
 * Compatibilidad: los ítems antiguos solo tienen "regenerator_level"; al
 * colocarlos se aplica ese nivel único a los tres árboles (comportamiento legacy).
 */
public class ItemUtil {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder()
                    .character('&')
                    .hexColors()
                    .useUnusualXRepeatedCharacterHexFormat()
                    .build();

    private static NamespacedKey REGEN_KEY;
    private static NamespacedKey LEVEL_KEY;    // legacy: nivel único
    private static NamespacedKey TIME_KEY;     // nivel del árbol TIME
    private static NamespacedKey RADIUS_KEY;   // nivel del árbol RADIUS
    private static NamespacedKey CROPS_KEY;    // nivel del árbol CROPS

    public static void init(CropRegeneratorPlugin plugin) {
        REGEN_KEY   = new NamespacedKey(plugin, "regenerator_block");
        LEVEL_KEY   = new NamespacedKey(plugin, "regenerator_level");
        TIME_KEY    = new NamespacedKey(plugin, "regenerator_time");
        RADIUS_KEY  = new NamespacedKey(plugin, "regenerator_radius");
        CROPS_KEY   = new NamespacedKey(plugin, "regenerator_crops");
    }

    private static void ensureInit() {
        if (REGEN_KEY == null) init(CropRegeneratorPlugin.getInstance());
    }

    /** Desactiva explícitamente la cursiva predeterminada de los ítems. */
    private static Component noItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** Crea el ítem con los tres niveles por separado. */
    public static ItemStack createRegeneratorItem(int timeLevel, int radiusLevel, int cropsLevel) {
        CropRegeneratorPlugin plugin = CropRegeneratorPlugin.getInstance();
        ensureInit();

        Material mat;
        try { mat = Material.valueOf(plugin.getConfig().getString("block-material", "EMERALD_BLOCK")); }
        catch (IllegalArgumentException ex) { mat = Material.EMERALD_BLOCK; }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(noItalic(LEGACY.deserialize(plugin.getConfig().getString("item.name", "&aRegenerador de Cultivos"))));

        int time = Math.max(1, timeLevel);
        int radius = Math.max(1, radiusLevel);
        int crops = Math.max(1, cropsLevel);
        int displayLevel = Math.max(time, Math.max(radius, crops)); // compat con {level}

        List<Component> lore = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList("item.lore")) {
            String parsed = line.replace("{level}", String.valueOf(displayLevel))
                    .replace("{time_level}", String.valueOf(time))
                    .replace("{radius_level}", String.valueOf(radius))
                    .replace("{crops_level}", String.valueOf(crops))
                    .replace("{radius}", String.valueOf(plugin.getUpgradeManager().getRadius(radius)))
                    .replace("{interval}", String.valueOf(plugin.getUpgradeManager().getInterval(time)));
            lore.add(noItalic(parsed.isEmpty() ? Component.empty() : LEGACY.deserialize(parsed)));
        }
        meta.lore(lore);

        if (plugin.getConfig().getBoolean("item.enchanted", true)) {
            Enchantment unbreaking = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("unbreaking"));
            if (unbreaking != null) meta.addEnchant(unbreaking, 1, true);
            if (plugin.getConfig().getBoolean("item.hide-enchantments", true)) meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        meta.getPersistentDataContainer().set(REGEN_KEY, PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(TIME_KEY, PersistentDataType.INTEGER, time);
        meta.getPersistentDataContainer().set(RADIUS_KEY, PersistentDataType.INTEGER, radius);
        meta.getPersistentDataContainer().set(CROPS_KEY, PersistentDataType.INTEGER, crops);
        // Legacy: se mantiene para compatibilidad con ítems antiguos (nivel máximo)
        meta.getPersistentDataContainer().set(LEVEL_KEY, PersistentDataType.INTEGER, displayLevel);

        item.setItemMeta(meta);
        return item;
    }

    /** Compatibilidad: ítem con el mismo nivel en los tres árboles. */
    public static ItemStack createRegeneratorItem(int level) {
        return createRegeneratorItem(level, level, level);
    }

    public static boolean isRegeneratorItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ensureInit();
        return item.getItemMeta().getPersistentDataContainer().has(REGEN_KEY, PersistentDataType.BOOLEAN);
    }

    /**
     * Lee los tres niveles del ítem: {time, radius, crops}.
     * Ítems legacy (solo regenerator_level): devuelve ese nivel único en los tres.
     */
    public static int[] getLevelsFromItem(ItemStack item) {
        if (!isRegeneratorItem(item)) return new int[]{1, 1, 1};
        ensureInit();

        var pdc = item.getItemMeta().getPersistentDataContainer();

        Integer time = pdc.get(TIME_KEY, PersistentDataType.INTEGER);
        Integer radius = pdc.get(RADIUS_KEY, PersistentDataType.INTEGER);
        Integer crops = pdc.get(CROPS_KEY, PersistentDataType.INTEGER);

        if (time == null || radius == null || crops == null) {
            Integer legacy = pdc.get(LEVEL_KEY, PersistentDataType.INTEGER);
            int lvl = legacy != null ? Math.max(1, legacy) : 1;
            return new int[]{lvl, lvl, lvl};
        }

        return new int[]{Math.max(1, time), Math.max(1, radius), Math.max(1, crops)};
    }

    /** Compatibilidad: nivel máximo de los tres árboles. */
    public static int getLevelFromItem(ItemStack item) {
        int[] lv = getLevelsFromItem(item);
        return Math.max(lv[0], Math.max(lv[1], lv[2]));
    }
}