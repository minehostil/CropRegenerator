package com.tuservidor.cropregenerator.managers;

import com.tuservidor.cropregenerator.CropRegeneratorPlugin;
import com.tuservidor.cropregenerator.hologram.IHologramProvider;
import com.tuservidor.cropregenerator.hologram.NativeHologramProvider;
import com.tuservidor.cropregenerator.model.RegeneratorBlock;
import org.bukkit.Chunk;

/**
 * Fachada que delega al NativeHologramProvider (TextDisplay de Paper).
 */
public class HologramManager {

    private final NativeHologramProvider provider;

    public HologramManager(CropRegeneratorPlugin plugin) {
        this.provider = new NativeHologramProvider(plugin);
        plugin.getLogger().info("[HologramManager] Proveedor: Native (TextDisplay)");
    }

    public void purgeAll()              { provider.purgeAll(); }
    public void purgeChunk(Chunk chunk) { provider.purgeChunk(chunk); }

    public void spawnOrUpdate(RegeneratorBlock rb) { provider.spawnOrUpdate(rb); }
    public void updateText(RegeneratorBlock rb)    { provider.updateText(rb); }
    public void remove(RegeneratorBlock rb)        { provider.remove(rb); }
    public void removeAll()                        { provider.removeAll(); }
}