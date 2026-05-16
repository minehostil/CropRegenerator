package com.tuservidor.cropregenerator.managers;

import com.tuservidor.cropregenerator.CropRegeneratorPlugin;
import com.tuservidor.cropregenerator.hologram.GHoloProvider;
import com.tuservidor.cropregenerator.hologram.IHologramProvider;
import com.tuservidor.cropregenerator.hologram.NativeHologramProvider;
import com.tuservidor.cropregenerator.model.RegeneratorBlock;
import org.bukkit.Chunk;

/**
 * Fachada que delega al proveedor configurado en config.yml → hologram.provider.
 *
 * Valores válidos:
 *   NATIVE  → TextDisplay nativo de Paper (default, sin dependencias)
 *   GHOLO   → GHolo plugin (requiere GHolo instalado)
 */
public class HologramManager {

    public enum Provider { NATIVE, GHOLO }

    private final CropRegeneratorPlugin plugin;
    private final IHologramProvider provider;
    private final Provider providerType;

    public HologramManager(CropRegeneratorPlugin plugin) {
        this.plugin = plugin;

        String raw = plugin.getConfig().getString("hologram.provider", "NATIVE").toUpperCase();
        Provider selected;
        try {
            selected = Provider.valueOf(raw);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[HologramManager] Proveedor inválido '" + raw + "', usando NATIVE.");
            selected = Provider.NATIVE;
        }

        // Verificar que GHolo esté disponible si se seleccionó
        if (selected == Provider.GHOLO
                && plugin.getServer().getPluginManager().getPlugin("GHolo") == null) {
            plugin.getLogger().warning("[HologramManager] GHolo no está instalado, usando NATIVE.");
            selected = Provider.NATIVE;
        }

        this.providerType = selected;

        if (selected == Provider.GHOLO) {
            this.provider = new GHoloProvider(plugin);
            plugin.getLogger().info("[HologramManager] Proveedor: GHolo");
        } else {
            this.provider = new NativeHologramProvider(plugin);
            plugin.getLogger().info("[HologramManager] Proveedor: Native (TextDisplay)");
        }
    }

    // ── Limpieza inicial (solo NATIVE) ───────────────────────

    /**
     * Elimina TextDisplays huérfanas. Solo aplica al proveedor NATIVE.
     * Debe llamarse en onEnable() antes de loadAll().
     */
    public void purgeAll() {
        if (provider instanceof NativeHologramProvider native_) {
            native_.purgeAll();
        }
    }

    /**
     * Limpia huérfanas en un chunk cargado. Solo aplica al proveedor NATIVE.
     */
    public void purgeChunk(Chunk chunk) {
        if (provider instanceof NativeHologramProvider native_) {
            native_.purgeChunk(chunk);
        }
    }

    // ── Delegación al proveedor ──────────────────────────────

    public void spawnOrUpdate(RegeneratorBlock rb) { provider.spawnOrUpdate(rb); }
    public void updateText(RegeneratorBlock rb)    { provider.updateText(rb); }
    public void remove(RegeneratorBlock rb)        { provider.remove(rb); }
    public void removeAll()                        { provider.removeAll(); }

    public Provider getProviderType() { return providerType; }
}