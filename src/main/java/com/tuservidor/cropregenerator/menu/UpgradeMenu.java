package com.tuservidor.cropregenerator.menu;

import com.tuservidor.cropregenerator.CropRegeneratorPlugin;
import com.tuservidor.cropregenerator.managers.UpgradeManager;
import com.tuservidor.cropregenerator.model.RegeneratorBlock;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.ArrayList;
import java.util.List;

public class UpgradeMenu {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder().character('&').hexColors().build();
    private final CropRegeneratorPlugin plugin;
    public UpgradeMenu(CropRegeneratorPlugin plugin) { this.plugin = plugin; }
    public void open(org.bukkit.entity.Player player, RegeneratorBlock rb) {
        Inventory inv = plugin.getServer().createInventory(new Holder(rb.getKey()), 9, text(plugin.getConfig().getString("menu.title", "&8Mejoras del Regenerador")));
        inv.setItem(1, upgradeItem(UpgradeManager.Tree.TIME, rb));
        inv.setItem(3, upgradeItem(UpgradeManager.Tree.RADIUS, rb));
        inv.setItem(5, upgradeItem(UpgradeManager.Tree.CROPS, rb));
        inv.setItem(7, particleItem(rb));
        player.openInventory(inv);
    }
    private ItemStack upgradeItem(UpgradeManager.Tree tree, RegeneratorBlock rb) {
        int current = switch (tree) { case TIME -> rb.getTimeLevel(); case RADIUS -> rb.getRadiusLevel(); case CROPS -> rb.getCropsLevel(); };
        UpgradeManager.UpgradeLevel cur = plugin.getUpgradeManager().get(tree, current), next = plugin.getUpgradeManager().getNext(tree, current);
        Material mat = switch (tree) { case TIME -> Material.CLOCK; case RADIUS -> Material.COMPASS; case CROPS -> Material.WHEAT; };
        String name = switch (tree) { case TIME -> "&bMejora de Tiempo"; case RADIUS -> "&aMejora de Radio"; case CROPS -> "&eMejora de Cultivos"; };
        List<String> lore = new ArrayList<>(List.of("&7Nivel actual: &f" + current));
        if (next == null) lore.add("&6Nivel máximo");
        else {
            if (tree == UpgradeManager.Tree.TIME) lore.add("&7Intervalo: &f" + cur.interval() + "s &7→ &a" + next.interval() + "s");
            if (tree == UpgradeManager.Tree.RADIUS) lore.add("&7Radio: &f" + cur.radius() + " &7→ &a" + next.radius());
            if (tree == UpgradeManager.Tree.CROPS) lore.add("&7Añade cultivos del tier &a" + next.level());
            lore.add(""); lore.add("&7Costo: &f" + costText(next.cost())); lore.add("&eClic para mejorar");
        }
        return item(mat, name, lore);
    }
    private ItemStack particleItem(RegeneratorBlock rb) {
        return item(Material.END_ROD, rb.isParticlesEnabled() ? "&aPartículas: Activadas" : "&cPartículas: Desactivadas", List.of("&7Anillo horizontal", "", "&eClic para alternar"));
    }
    public String costText(UpgradeManager.UpgradeCost cost) {
        return switch (cost.type()) {
            case MONEY -> plugin.getVaultHook() != null ? plugin.getVaultHook().format(cost.amount()) : "Vault no disponible";
            case XP -> ((int) cost.amount()) + " XP";
            case ITEMS -> (int) cost.amount() + "x " + (cost.itemDisplay().isBlank() ? String.valueOf(cost.item()) : cost.itemDisplay());
            case NONE -> "Gratis";
        };
    }
    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material); ItemMeta meta = item.getItemMeta();
        meta.displayName(text(name).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(s -> text(s).decoration(TextDecoration.ITALIC, false)).toList()); item.setItemMeta(meta); return item;
    }
    private Component text(String value) { return LEGACY.deserialize(value == null ? "" : value); }
    public static final class Holder implements InventoryHolder {
        private final String blockKey; public Holder(String blockKey) { this.blockKey = blockKey; }
        public String blockKey() { return blockKey; }
        @Override public Inventory getInventory() { return null; }
    }
}