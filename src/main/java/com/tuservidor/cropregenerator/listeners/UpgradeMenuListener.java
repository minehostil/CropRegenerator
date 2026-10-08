package com.tuservidor.cropregenerator.listeners;

import com.tuservidor.cropregenerator.CropRegeneratorPlugin;
import com.tuservidor.cropregenerator.managers.UpgradeManager;
import com.tuservidor.cropregenerator.menu.UpgradeMenu;
import com.tuservidor.cropregenerator.model.RegeneratorBlock;
import com.tuservidor.cropregenerator.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

public class UpgradeMenuListener implements Listener {
    private final CropRegeneratorPlugin plugin; private final UpgradeMenu menu;
    public UpgradeMenuListener(CropRegeneratorPlugin plugin, UpgradeMenu menu) { this.plugin = plugin; this.menu = menu; }
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof UpgradeMenu.Holder holder)) return;
        event.setCancelled(true); if (!(event.getWhoClicked() instanceof Player player)) return;
        RegeneratorBlock rb = find(holder.blockKey());
        if (rb == null || !rb.getOwnerUUID().equals(player.getUniqueId())) { player.closeInventory(); return; }
        switch (event.getRawSlot()) {
            case 1 -> upgrade(player, rb, UpgradeManager.Tree.TIME);
            case 3 -> upgrade(player, rb, UpgradeManager.Tree.RADIUS);
            case 5 -> upgrade(player, rb, UpgradeManager.Tree.CROPS);
            case 7 -> { rb.setParticlesEnabled(!rb.isParticlesEnabled()); MessageUtil.send(player, "particles-toggled", "{state}", rb.isParticlesEnabled() ? "activadas" : "desactivadas"); menu.open(player, rb); }
            default -> { }
        }
    }
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof UpgradeMenu.Holder) event.setCancelled(true);
    }

    private void upgrade(Player player, RegeneratorBlock rb, UpgradeManager.Tree tree) {
        int current = switch (tree) { case TIME -> rb.getTimeLevel(); case RADIUS -> rb.getRadiusLevel(); case CROPS -> rb.getCropsLevel(); };
        UpgradeManager.UpgradeLevel next = plugin.getUpgradeManager().getNext(tree, current);
        if (next == null) { MessageUtil.send(player, "upgrade-max"); return; }
        if (!pay(player, next.cost())) return;
        switch (tree) { case TIME -> rb.setTimeLevel(next.level()); case RADIUS -> rb.setRadiusLevel(next.level()); case CROPS -> rb.setCropsLevel(next.level()); }
        if (tree == UpgradeManager.Tree.TIME) rb.setNextRegenTimestamp(System.currentTimeMillis() + next.interval() * 1000L);
        plugin.getHologramManager().spawnOrUpdate(rb);
        MessageUtil.send(player, "upgrade-success", "{tree}", treeName(tree), "{level}", String.valueOf(next.level()));
        menu.open(player, rb);
    }
    private boolean pay(Player player, UpgradeManager.UpgradeCost cost) {
        switch (cost.type()) {
            case NONE -> { return true; }
            case MONEY -> { if (plugin.getVaultHook() == null || !plugin.getVaultHook().isAvailable()) { MessageUtil.send(player, "vault-unavailable"); return false; } if (!plugin.getVaultHook().has(player, cost.amount())) { MessageUtil.send(player, "not-enough-money", "{cost}", plugin.getVaultHook().format(cost.amount())); return false; } if (!plugin.getVaultHook().withdraw(player, cost.amount())) { MessageUtil.send(player, "payment-error"); return false; } return true; }
            case XP -> { int amount = Math.max(0, (int) Math.ceil(cost.amount())); if (player.getTotalExperience() < amount) { MessageUtil.send(player, "not-enough-xp", "{cost}", String.valueOf(amount)); return false; } player.giveExp(-amount); return true; }
            case ITEMS -> { int amount = Math.max(1, (int) Math.ceil(cost.amount())); if (cost.item() == null || count(player, cost.item()) < amount) { MessageUtil.send(player, "not-enough-items", "{cost}", menu.costText(cost)); return false; } remove(player, cost.item(), amount); return true; }
        }
        return false;
    }
    private int count(Player p, Material m) { int n=0; for(ItemStack i:p.getInventory().getStorageContents()) if(i!=null&&i.getType()==m)n+=i.getAmount(); return n; }
    private void remove(Player p, Material m, int amount) { int rem=amount; ItemStack[] a=p.getInventory().getStorageContents(); for(int i=0;i<a.length&&rem>0;i++){ItemStack x=a[i]; if(x==null||x.getType()!=m)continue; int take=Math.min(rem,x.getAmount()); x.setAmount(x.getAmount()-take); rem-=take; a[i]=x.getAmount()==0?null:x;} p.getInventory().setStorageContents(a); }
    private RegeneratorBlock find(String key) { for(RegeneratorBlock rb:plugin.getBlockDataManager().getAllBlocks()) if(rb.getKey().equals(key)) return rb; return null; }
    private String treeName(UpgradeManager.Tree t) { return switch(t){case TIME->"Tiempo";case RADIUS->"Radio";case CROPS->"Cultivos";}; }
}
