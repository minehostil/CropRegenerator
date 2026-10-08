package com.tuservidor.cropregenerator.hooks;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Integración opcional con Vault para costes MONEY. */
public class VaultHook {
    private final Economy economy;

    public VaultHook(org.bukkit.plugin.Plugin plugin) {
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        this.economy = rsp == null ? null : rsp.getProvider();
    }

    public boolean isAvailable() { return economy != null; }
    public double balance(Player player) { return economy == null ? 0 : economy.getBalance(player); }
    public boolean has(Player player, double amount) { return economy != null && economy.has(player, amount); }
    public boolean withdraw(Player player, double amount) {
        return economy != null && economy.withdrawPlayer(player, amount).transactionSuccess();
    }
    public String format(double amount) { return economy == null ? String.valueOf(amount) : economy.format(amount); }
}
