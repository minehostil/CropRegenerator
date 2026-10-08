package com.tuservidor.cropregenerator.listeners;

import com.tuservidor.cropregenerator.CropRegeneratorPlugin;
import com.tuservidor.cropregenerator.menu.UpgradeMenu;
import com.tuservidor.cropregenerator.model.RegeneratorBlock;
import com.tuservidor.cropregenerator.util.MessageUtil;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Abre el menú únicamente al dueño del regenerador. */
public class InteractListener implements Listener {
    private final CropRegeneratorPlugin plugin;
    private final UpgradeMenu menu;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    public InteractListener(CropRegeneratorPlugin plugin, UpgradeMenu menu) { this.plugin=plugin; this.menu=menu; }

    @EventHandler(priority=EventPriority.NORMAL, ignoreCancelled=true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction()!=Action.RIGHT_CLICK_BLOCK || event.getClickedBlock()==null) return;
        RegeneratorBlock rb=plugin.getBlockDataManager().getBlock(event.getClickedBlock().getLocation());
        if(rb==null)return;
        event.setCancelled(true);
        Player p=event.getPlayer();
        if(!rb.getOwnerUUID().equals(p.getUniqueId())) { MessageUtil.send(p,"no-permission"); return; }
        long cooldown=Math.max(0L, plugin.getConfig().getLong("interaction.cooldown",0L)*1000L);
        if(cooldown>0){ long now=System.currentTimeMillis(), last=cooldowns.getOrDefault(p.getUniqueId(),0L); if(now-last<cooldown){long sec=(long)Math.ceil((cooldown-(now-last))/1000.0); MessageUtil.send(p,"cooldown-message","{seconds}",String.valueOf(sec)); return;} cooldowns.put(p.getUniqueId(),now); }
        menu.open(p,rb);
    }
    @EventHandler public void onQuit(org.bukkit.event.player.PlayerQuitEvent event){cooldowns.remove(event.getPlayer().getUniqueId());}
}
