package com.tuservidor.cropregenerator.util;

import com.tuservidor.cropregenerator.CropRegeneratorPlugin;
import com.tuservidor.cropregenerator.managers.UpgradeManager;
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
 * Usa LegacyComponentSerializer para nombre y lore — soporta & codes y &#RRGGBB.
 *
 * La cursiva se desactiva explícitamente para evitar la cursiva predeterminada
 * que aplica Minecraft/Paper a las descripciones/nombres de ítems.
 */
public class ItemUtil {

    // LegacyComponentSerializer con soporte de & y hex &#RRGGBB
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder()
                    .character('&')
                    .hexColors()
                    .useUnusualXRepeatedCharacterHexFormat()
                    .build();

    private static NamespacedKey REGEN_KEY;
    private static NamespacedKey LEVEL_KEY;

    public static void init(CropRegeneratorPlugin plugin) {
        REGEN_KEY = new NamespacedKey(plugin, "regenerator_block");
        LEVEL_KEY = new NamespacedKey(plugin, "regenerator_level");
    }

    /**
     * Desactiva explícitamente la cursiva predeterminada de los ítems.
     */
    private static Component noItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static ItemStack createRegeneratorItem(int level) {
        CropRegeneratorPlugin plugin = CropRegeneratorPlugin.getInstance();
        if (REGEN_KEY == null) init(plugin);
        Material mat;
        try { mat = Material.valueOf(plugin.getConfig().getString("block-material", "EMERALD_BLOCK")); }
        catch (IllegalArgumentException ex) { mat = Material.EMERALD_BLOCK; }
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(noItalic(LEGACY.deserialize(plugin.getConfig().getString("item.name", "&aRegenerador de Cultivos"))));
        List<Component> lore = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList("item.lore")) {
            String parsed = line.replace("{level}", String.valueOf(level))
                    .replace("{time_level}", String.valueOf(level))
                    .replace("{radius_level}", String.valueOf(level))
                    .replace("{crops_level}", String.valueOf(level))
                    .replace("{radius}", String.valueOf(plugin.getUpgradeManager().getRadius(level)))
                    .replace("{interval}", String.valueOf(plugin.getUpgradeManager().getInterval(level)));
            lore.add(noItalic(parsed.isEmpty() ? Component.empty() : LEGACY.deserialize(parsed)));
        }
        meta.lore(lore);
        if (plugin.getConfig().getBoolean("item.enchanted", true)) {
            Enchantment unbreaking = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("unbreaking"));
            if (unbreaking != null) meta.addEnchant(unbreaking, 1, true);
            if (plugin.getConfig().getBoolean("item.hide-enchantments", true)) meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        meta.getPersistentDataContainer().set(REGEN_KEY, PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(LEVEL_KEY, PersistentDataType.INTEGER, Math.max(1, level));
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isRegeneratorItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        if (REGEN_KEY == null) {
            init(CropRegeneratorPlugin.getInstance());
        }

        return item.getItemMeta()
                .getPersistentDataContainer()
                .has(
                        REGEN_KEY,
                        PersistentDataType.BOOLEAN
                );
    }

    public static int getLevelFromItem(ItemStack item) {
        if (!isRegeneratorItem(item)) {
            return 1;
        }

        if (LEVEL_KEY == null) {
            init(CropRegeneratorPlugin.getInstance());
        }

        Integer lvl = item.getItemMeta()
                .getPersistentDataContainer()
                .get(
                        LEVEL_KEY,
                        PersistentDataType.INTEGER
                );

        return lvl != null ? lvl : 1;
    }
}