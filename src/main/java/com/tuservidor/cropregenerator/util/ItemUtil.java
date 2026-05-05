package com.tuservidor.cropregenerator.util;

import com.tuservidor.cropregenerator.CropRegeneratorPlugin;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Utilidades para crear e identificar el Ã­tem del bloque regenerador.
 * Usa PersistentDataContainer para marcar el Ã­tem con su nivel.
 */
public class ItemUtil {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static NamespacedKey REGEN_KEY;
    private static NamespacedKey LEVEL_KEY;

    public static void init(CropRegeneratorPlugin plugin) {
        REGEN_KEY = new NamespacedKey(plugin, "regenerator_block");
        LEVEL_KEY = new NamespacedKey(plugin, "regenerator_level");
    }

    public static ItemStack createRegeneratorItem(int level) {
        CropRegeneratorPlugin plugin = CropRegeneratorPlugin.getInstance();

        if (REGEN_KEY == null) init(plugin);

        Material mat  = Material.valueOf(
                plugin.getConfig().getString("block-material", "EMERALD_BLOCK"));
        String name   = plugin.getUpgradeManager().getLevel(level).displayName();
        var upgLevel  = plugin.getUpgradeManager().getLevel(level);

        ItemStack item = new ItemStack(mat);
        ItemMeta meta  = item.getItemMeta();

        meta.displayName(MM.deserialize(name));
        meta.lore(List.of(
                MM.deserialize("<gray>Radio: <green>" + upgLevel.radius() + " bloques"),
                MM.deserialize("<gray>Intervalo: <yellow>" + upgLevel.regenInterval() + "s"),
                MM.deserialize("<gray>MÃ¡x/isla: <aqua>" + upgLevel.maxBlocksPerIsland()),
                MM.deserialize(""),
                MM.deserialize("<dark_gray>Coloca este bloque en tu isla")
        ));

        // Marcar con PDC
        meta.getPersistentDataContainer().set(REGEN_KEY, PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(LEVEL_KEY, PersistentDataType.INTEGER, level);

        item.setItemMeta(meta);
        return item;
    }

    public static boolean isRegeneratorItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        if (REGEN_KEY == null) init(CropRegeneratorPlugin.getInstance());
        return item.getItemMeta().getPersistentDataContainer()
                .has(REGEN_KEY, PersistentDataType.BOOLEAN);
    }

    public static int getLevelFromItem(ItemStack item) {
        if (!isRegeneratorItem(item)) return 1;
        if (LEVEL_KEY == null) init(CropRegeneratorPlugin.getInstance());
        Integer lvl = item.getItemMeta().getPersistentDataContainer()
                .get(LEVEL_KEY, PersistentDataType.INTEGER);
        return lvl != null ? lvl : 1;
    }
}
