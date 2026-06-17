package com.tuservidor.cropregenerator;

import com.tuservidor.cropregenerator.commands.CropBlockCommand;
import com.tuservidor.cropregenerator.data.BlockDataManager;
import com.tuservidor.cropregenerator.hooks.FAWEHook;
import com.tuservidor.cropregenerator.hooks.SuperiorSkyblockHook;
import com.tuservidor.cropregenerator.listeners.BlockListener;
import com.tuservidor.cropregenerator.listeners.ChunkLoadListener;
import com.tuservidor.cropregenerator.listeners.PlayerConnectionListener;
import com.tuservidor.cropregenerator.listeners.InteractListener;
import com.tuservidor.cropregenerator.listeners.IslandListener;
import com.tuservidor.cropregenerator.managers.HologramManager;
import com.tuservidor.cropregenerator.managers.RegeneratorManager;
import com.tuservidor.cropregenerator.managers.UpgradeManager;
import org.bukkit.plugin.java.JavaPlugin;

public class CropRegeneratorPlugin extends JavaPlugin {

    private static CropRegeneratorPlugin instance;

    private BlockDataManager blockDataManager;
    private HologramManager hologramManager;
    private RegeneratorManager regeneratorManager;
    private UpgradeManager upgradeManager;
    private SuperiorSkyblockHook superiorHook;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        // Managers
        this.upgradeManager    = new UpgradeManager(this);
        this.blockDataManager  = new BlockDataManager(this);
        this.hologramManager   = new HologramManager(this);
        this.regeneratorManager = new RegeneratorManager(this);

        // Hook opcional de SuperiorSkyblock2
        if (getServer().getPluginManager().getPlugin("SuperiorSkyblock2") != null) {
            this.superiorHook = new SuperiorSkyblockHook(this);
            getLogger().info("SuperiorSkyblock2 detectado — integración activada.");
            // Listener de isla (delete)
            getServer().getPluginManager().registerEvents(new IslandListener(this), this);
        } else {
            getLogger().warning("SuperiorSkyblock2 no encontrado — límites de isla desactivados.");
        }

        // Listeners y comandos
        getServer().getPluginManager().registerEvents(new BlockListener(this), this);
        getServer().getPluginManager().registerEvents(new InteractListener(this), this);
        getServer().getPluginManager().registerEvents(new ChunkLoadListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this), this);

        CropBlockCommand cmd = new CropBlockCommand(this);
        getCommand("cropblock").setExecutor(cmd);
        getCommand("cropblock").setTabCompleter(cmd);

        // Cargar datos persistentes y restaurar hologramas
        blockDataManager.loadAll();
        // Solo iniciar el task si hay jugadores conectados (evita consumo en servidor vacío)
        if (!getServer().getOnlinePlayers().isEmpty()) {
            regeneratorManager.startAll();
        }

        getLogger().info("CropRegenerator habilitado correctamente.");
    }

    @Override
    public void onDisable() {
        if (regeneratorManager != null) regeneratorManager.stopAll();
        if (hologramManager    != null) hologramManager.removeAll();
        if (blockDataManager   != null) blockDataManager.saveAll();
        getLogger().info("CropRegenerator deshabilitado.");
    }

    // ── Getters ────────────────────────────────────────────
    public static CropRegeneratorPlugin getInstance() { return instance; }
    public BlockDataManager getBlockDataManager()     { return blockDataManager; }
    public HologramManager  getHologramManager()      { return hologramManager; }
    public RegeneratorManager getRegeneratorManager() { return regeneratorManager; }
    public UpgradeManager   getUpgradeManager()       { return upgradeManager; }
    public SuperiorSkyblockHook getSuperiorHook()     { return superiorHook; }
    public boolean hasSuperior()                      { return superiorHook != null; }
}