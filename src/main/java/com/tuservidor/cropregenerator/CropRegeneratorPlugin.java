package com.tuservidor.cropregenerator;

import com.tuservidor.cropregenerator.commands.CropBlockCommand;
import com.tuservidor.cropregenerator.data.BlockDataManager;
import com.tuservidor.cropregenerator.hooks.SuperiorSkyblockHook;
import com.tuservidor.cropregenerator.hooks.VaultHook;
import com.tuservidor.cropregenerator.managers.ParticleManager;
import com.tuservidor.cropregenerator.menu.UpgradeMenu;
import com.tuservidor.cropregenerator.listeners.UpgradeMenuListener;
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
    private VaultHook vaultHook;
    private ParticleManager particleManager;
    private UpgradeMenu upgradeMenu;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        if (!new java.io.File(getDataFolder(), "upgrades.yml").exists()) saveResource("upgrades.yml", false);

        // Managers
        this.upgradeManager    = new UpgradeManager(this);
        this.blockDataManager  = new BlockDataManager(this);
        this.hologramManager   = new HologramManager(this);
        this.regeneratorManager = new RegeneratorManager(this);
        this.vaultHook = new VaultHook(this);
        if (vaultHook.isAvailable()) getLogger().info("Vault detectado — costes MONEY activados.");
        else getLogger().warning("Vault/Economy no encontrado — costes MONEY no disponibles.");
        this.particleManager = new ParticleManager(this);
        this.upgradeMenu = new UpgradeMenu(this);

        // Hook opcional de SuperiorSkyblock2
        if (getServer().getPluginManager().getPlugin("SuperiorSkyblock2") != null) {
            this.superiorHook = new SuperiorSkyblockHook(this);
            getLogger().info("SuperiorSkyblock2 detectado — integración activada.");
            // Listener de isla (disband + transferencia)
            getServer().getPluginManager().registerEvents(new IslandListener(this), this);
        } else {
            getLogger().warning("SuperiorSkyblock2 no encontrado — límites de isla desactivados.");
        }

        // Listeners y comandos
        getServer().getPluginManager().registerEvents(new BlockListener(this), this);
        getServer().getPluginManager().registerEvents(new InteractListener(this, upgradeMenu), this);
        getServer().getPluginManager().registerEvents(new UpgradeMenuListener(this, upgradeMenu), this);
        getServer().getPluginManager().registerEvents(new ChunkLoadListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this), this);

        CropBlockCommand cmd = new CropBlockCommand(this);
        getCommand("cropblock").setExecutor(cmd);
        getCommand("cropblock").setTabCompleter(cmd);

        // Cargar datos persistentes y restaurar hologramas
        blockDataManager.loadAll();
        blockDataManager.spawnHologramsInLoadedChunks();

        // Autosave cada 5 minutos (se cancela solo al deshabilitar el plugin)
        getServer().getScheduler().runTaskTimer(this, () -> blockDataManager.saveAll(),
                20L * 300L, 20L * 300L);

        // Solo iniciar los tasks si hay jugadores conectados (evita consumo en servidor vacío)
        if (!getServer().getOnlinePlayers().isEmpty()) {
            regeneratorManager.startAll();
            particleManager.start();
        }

        getLogger().info("CropRegenerator habilitado correctamente.");
    }

    @Override
    public void onDisable() {
        if (regeneratorManager != null) regeneratorManager.stopAll();
        if (particleManager != null) particleManager.stop();
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
    public VaultHook getVaultHook() { return vaultHook; }
    public ParticleManager getParticleManager() { return particleManager; }
    public UpgradeMenu getUpgradeMenu() { return upgradeMenu; }
}