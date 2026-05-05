package com.tuservidor.cropregenerator.commands;

import com.tuservidor.cropregenerator.CropRegeneratorPlugin;
import com.tuservidor.cropregenerator.model.RegeneratorBlock;
import com.tuservidor.cropregenerator.util.ItemUtil;
import com.tuservidor.cropregenerator.util.MessageUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * /cropblock <help|give|upgrade|info>
 */
public class CropBlockCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final CropRegeneratorPlugin plugin;

    public CropBlockCommand(CropRegeneratorPlugin plugin) {
        this.plugin = plugin;
        ItemUtil.init(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             String[] args) {

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {

            // /cropblock give <jugador> <nivel>
            case "give" -> {
                if (!sender.hasPermission("cropregenerator.give")) {
                    MessageUtil.send(sender, "no-permission");
                    return true;
                }
                if (args.length < 3) { sender.sendMessage(MM.deserialize("<red>Uso: /cropblock give <jugador> <nivel>")); return true; }

                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { MessageUtil.send(sender, "no-player"); return true; }

                int level;
                try { level = Integer.parseInt(args[2]); } catch (NumberFormatException e) {
                    MessageUtil.send(sender, "invalid-level", "{max}", String.valueOf(plugin.getUpgradeManager().getMaxLevel()));
                    return true;
                }
                if (!plugin.getUpgradeManager().levelExists(level)) {
                    MessageUtil.send(sender, "invalid-level", "{max}", String.valueOf(plugin.getUpgradeManager().getMaxLevel()));
                    return true;
                }

                ItemStack item = ItemUtil.createRegeneratorItem(level);
                target.getInventory().addItem(item);
                MessageUtil.send(sender, "item-given",
                        "{level}", String.valueOf(level),
                        "{player}", target.getName());
            }

            // /cropblock upgrade  (jugador mira el bloque que pisa)
            case "upgrade" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage("Solo jugadores."); return true; }
                if (!player.hasPermission("cropregenerator.upgrade")) {
                    MessageUtil.send(player, "no-permission"); return true;
                }

                RegeneratorBlock rb = plugin.getBlockDataManager().getBlock(player.getLocation().subtract(0,1,0));
                if (rb == null) {
                    player.sendMessage(MM.deserialize("<red>No estás sobre un bloque regenerador."));
                    return true;
                }
                if (!rb.getOwnerUUID().equals(player.getUniqueId()) && !player.hasPermission("cropregenerator.admin")) {
                    MessageUtil.send(player, "no-permission"); return true;
                }

                int nextLevel = rb.getLevel() + 1;
                if (!plugin.getUpgradeManager().levelExists(nextLevel)) {
                    MessageUtil.send(player, "upgrade-max"); return true;
                }

                rb.setLevel(nextLevel);
                plugin.getHologramManager().spawnOrUpdate(rb);
                MessageUtil.send(player, "upgrade-success", "{level}", String.valueOf(nextLevel));
            }

            // /cropblock info  (info del bloque debajo)
            case "info" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage("Solo jugadores."); return true; }

                RegeneratorBlock rb = plugin.getBlockDataManager().getBlock(player.getLocation().subtract(0,1,0));
                if (rb == null) {
                    player.sendMessage(MM.deserialize("<red>No estás sobre un bloque regenerador."));
                    return true;
                }
                var lvl = plugin.getUpgradeManager().getLevel(rb.getLevel());
                player.sendMessage(MM.deserialize(
                        "<dark_green>══ <green>Info del Bloque <dark_green>══\n" +
                        "<gray>Dueño: <white>" + Bukkit.getOfflinePlayer(rb.getOwnerUUID()).getName() + "\n" +
                        "<gray>Nivel: <yellow>" + rb.getLevel() + "\n" +
                        "<gray>Radio: <green>" + lvl.radius() + " bloques\n" +
                        "<gray>Intervalo: <yellow>" + lvl.regenInterval() + "s\n" +
                        "<gray>Próx. regen: <aqua>" + rb.getSecondsUntilRegen() + "s"
                ));
            }

            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(MM.deserialize(
                "<dark_green>══ <green>CropRegenerator <dark_green>══\n" +
                "<yellow>/cropblock give <jugador> <nivel> <gray>- Da un bloque\n" +
                "<yellow>/cropblock upgrade <gray>- Mejora el bloque en el que estás parado\n" +
                "<yellow>/cropblock info <gray>- Info del bloque bajo tus pies"
        ));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command command,
                                      @NotNull String alias,
                                      String[] args) {
        if (args.length == 1)
            return List.of("give", "upgrade", "info", "help");
        if (args.length == 2 && args[0].equalsIgnoreCase("give"))
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return java.util.stream.IntStream.rangeClosed(1, plugin.getUpgradeManager().getMaxLevel())
                    .mapToObj(String::valueOf).toList();
        }
        return List.of();
    }
}
