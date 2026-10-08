package com.tuservidor.cropregenerator.managers;

import com.tuservidor.cropregenerator.CropRegeneratorPlugin;
import com.tuservidor.cropregenerator.hologram.NativeHologramProvider;
import com.tuservidor.cropregenerator.model.RegeneratorBlock;

/**
 * Fachada que delega al NativeHologramProvider (TextDisplay de Paper).
 */
public class HologramManager {

    private final NativeHologramProvider provider;

    public HologramManager(CropRegeneratorPlugin plugin) {
    NativeHologramProvider p;
    try {
        p = new NativeHologramProvider(plugin);
    } catch (NoClassDefFoundError | NoSuchMethodError e) {
        throw new IllegalStateException(
            "CropRegenerator requiere Paper 1.19.4+ (TextDisplay no disponible en esta versión)", e);
    }
    this.provider = p;
    plugin.getLogger().info("[HologramManager] Proveedor: Native (TextDisplay)");
}

    public void spawnOrUpdate(RegeneratorBlock rb) { provider.spawnOrUpdate(rb); }
    public void updateText(RegeneratorBlock rb)    { provider.updateText(rb); }
    public void remove(RegeneratorBlock rb)        { provider.remove(rb); }
    public void removeAll()                        { provider.removeAll(); }
}