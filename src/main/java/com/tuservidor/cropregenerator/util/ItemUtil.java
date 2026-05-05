package com.tuservidor.cropregenerator.util;

import com.tuservidor.cropregenerator.CropRegeneratorPlugin;
import com.tuservidor.cropregenerator.managers.UpgradeManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
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
 * Opciones en config.yml → item:
 *   lore              → Líneas ilimitadas con placeholders
 *   enchanted         → true/false — aplica brillo de encantamiento
 *   hide-enchantments → true/false — oculta el texto de encantamientos
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

        UpgradeManager.UpgradeLevel upgLevel = plugin.getUpgradeManager().getLevel(level);

        Material mat = Material.valueOf(
                plugin.getConfig().getString("block-material", "EMERALD_BLOCK"));

        ItemStack item = new ItemStack(mat);
        ItemMeta meta  = item.getItemMeta();

        // ── Nombre ───────────────────────────────────────────
        meta.displayName(MM.deserialize(upgLevel.displayName()));

        // ── Lore desde config ────────────────────────────────
        List<String> rawLines = plugin.getConfig().getStringList("item.lore");
        List<Component> lore  = new ArrayList<>();

        for (String line : rawLines) {
            String parsed = line
                    .replace("{level}",      String.valueOf(level))
                    .replace("{radius}",     String.valueOf(upgLevel.radius()))
                    .replace("{interval}",   String.valueOf(upgLevel.regenInterval()))
                    .replace("{max_blocks}", String.valueOf(upgLevel.maxBlocksPerIsland()));

            lore.add(parsed.isEmpty() ? Component.empty() : MM.deserialize(parsed));
        }

        meta.lore(lore);

        // ── Encantamiento (brillo) ────────────────────────────
        boolean enchanted        = plugin.getConfig().getBoolean("item.enchanted", true);
        boolean hideEnchantments = plugin.getConfig().getBoolean("item.hide-enchantments", true);

        if (enchanted) {
            // Usar UNBREAKING via Registry para compatibilidad con 1.21.x
            Enchantment unbreaking = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("unbreaking"));
            if (unbreaking != null) {
                meta.addEnchant(unbreaking, 1, true);
            }
            if (hideEnchantments) {
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
        }

        // ── PDC ──────────────────────────────────────────────
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
