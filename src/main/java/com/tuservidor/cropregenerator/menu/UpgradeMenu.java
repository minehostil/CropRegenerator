package com.tuservidor.cropregenerator.menu;

import com.tuservidor.cropregenerator.CropRegeneratorPlugin;
import com.tuservidor.cropregenerator.managers.UpgradeManager;
import com.tuservidor.cropregenerator.model.RegeneratorBlock;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/** Menú de mejoras completamente configurable desde config.yml. */
public class UpgradeMenu {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&').hexColors().build();

    private final CropRegeneratorPlugin plugin;

    public UpgradeMenu(CropRegeneratorPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, RegeneratorBlock rb) {
        ConfigurationSection menu = plugin.getConfig().getConfigurationSection("menu");
        if (menu == null) {
            plugin.getLogger().warning("No existe la sección menu en config.yml.");
            return;
        }

        int rows = Math.max(1, Math.min(6, menu.getInt("rows", 1)));
        String title = menu.getString("title", "&8Mejoras del Regenerador");
        Holder holder = new Holder(rb.getKey());
        Inventory inv = plugin.getServer().createInventory(holder, rows * 9, text(title));
        holder.setInventory(inv);

        ConfigurationSection items = menu.getConfigurationSection("items");
        if (items != null) {
            for (String id : items.getKeys(false)) {
                ConfigurationSection section = items.getConfigurationSection(id);
                if (section == null) continue;

                String action = section.getString("action", "NONE").toUpperCase(Locale.ROOT);
                int slot = section.getInt("slot", -1);
                if (slot < 0 || slot >= inv.getSize()) {
                    plugin.getLogger().warning("Slot inválido en menu.items." + id + ": " + slot);
                    continue;
                }

                holder.setAction(slot, action);
                inv.setItem(slot, createMenuItem(section, id, action, rb));
            }
        }

        ConfigurationSection filler = menu.getConfigurationSection("filler");
        if (filler != null && filler.getBoolean("enabled", false)) {
            Material material = material(filler.getString("material", "BLACK_STAINED_GLASS_PANE"), Material.BLACK_STAINED_GLASS_PANE);
            ItemStack item = item(material,
                    filler.getString("name", " "),
                    filler.getStringList("lore"));
            for (int slot = 0; slot < inv.getSize(); slot++) {
                if (inv.getItem(slot) == null) inv.setItem(slot, item);
            }
        }

        player.openInventory(inv);
    }

    private ItemStack createMenuItem(ConfigurationSection section, String id, String action, RegeneratorBlock rb) {
        Material material = material(section.getString("material", "STONE"), Material.STONE);
        String name = replace(section.getString("name", "&f" + id), action, rb);
        List<String> lore = section.getStringList("lore").stream()
                .map(line -> replace(line, action, rb))
                .toList();
        return item(material, name, lore);
    }

    private String replace(String value, String action, RegeneratorBlock rb) {
        if (value == null) return "";
        UpgradeManager.Tree tree = treeFor(action);
        String result = value;

        result = result.replace("{particles_state}", rb.isParticlesEnabled() ? "Activadas" : "Desactivadas");

        if (tree != null) {
            int currentLevel = currentLevel(tree, rb);
            UpgradeManager.UpgradeLevel current = plugin.getUpgradeManager().get(tree, currentLevel);
            UpgradeManager.UpgradeLevel next = plugin.getUpgradeManager().getNext(tree, currentLevel);

            result = result.replace("{level}", String.valueOf(currentLevel));
            result = result.replace("{max_level}", String.valueOf(plugin.getUpgradeManager().maxLevel(tree)));
            result = result.replace("{cost}", next == null ? "Nivel máximo" : costText(next.cost()));
            result = result.replace("{current_interval}", String.valueOf(current.interval()));
            result = result.replace("{next_interval}", next == null ? "MAX" : String.valueOf(next.interval()));
            result = result.replace("{current_radius}", String.valueOf(current.radius()));
            result = result.replace("{next_radius}", next == null ? "MAX" : String.valueOf(next.radius()));
            result = result.replace("{current_crops}", String.valueOf(current.crops().size()));
            result = result.replace("{next_crops}", next == null ? "MAX" : String.valueOf(next.crops().size()));
            result = result.replace("{next_level}", next == null ? "MAX" : String.valueOf(next.level()));
        }

        return result;
    }

    private int currentLevel(UpgradeManager.Tree tree, RegeneratorBlock rb) {
        return switch (tree) {
            case TIME -> rb.getTimeLevel();
            case RADIUS -> rb.getRadiusLevel();
            case CROPS -> rb.getCropsLevel();
        };
    }

    private UpgradeManager.Tree treeFor(String action) {
        return switch (action) {
            case "UPGRADE_TIME" -> UpgradeManager.Tree.TIME;
            case "UPGRADE_RADIUS" -> UpgradeManager.Tree.RADIUS;
            case "UPGRADE_CROPS" -> UpgradeManager.Tree.CROPS;
            default -> null;
        };
    }

    public String costText(UpgradeManager.UpgradeCost cost) {
        return switch (cost.type()) {
            case MONEY -> plugin.getVaultHook() != null
                    ? plugin.getVaultHook().format(cost.amount())
                    : "Vault no disponible";
            case XP -> ((int) cost.amount()) + " XP";
            case ITEMS -> (int) cost.amount() + "x "
                    + (cost.itemDisplay().isBlank() ? String.valueOf(cost.item()) : cost.itemDisplay());
            case NONE -> "Gratis";
        };
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(text(name).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream()
                .map(line -> text(line).decoration(TextDecoration.ITALIC, false))
                .toList());
        item.setItemMeta(meta);
        return item;
    }

    private Material material(String value, Material fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Material.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Material inválido en menú: " + value);
            return fallback;
        }
    }

    private Component text(String value) {
        return LEGACY.deserialize(value == null ? "" : value);
    }

    public static final class Holder implements InventoryHolder {
        private final String blockKey;
        private final Map<Integer, String> actions = new HashMap<>();
        private Inventory inventory;

        public Holder(String blockKey) {
            this.blockKey = blockKey;
        }

        public String blockKey() {
            return blockKey;
        }

        public void setAction(int slot, String action) {
            actions.put(slot, action);
        }

        public String action(int slot) {
            return actions.getOrDefault(slot, "NONE");
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}