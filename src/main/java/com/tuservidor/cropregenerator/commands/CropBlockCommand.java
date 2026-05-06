package com.tuservidor.cropregenerator.commands;

import com.tuservidor.cropregenerator.CropRegeneratorPlugin;
import com.tuservidor.cropregenerator.model.RegeneratorBlock;
import com.tuservidor.cropregenerator.util.ItemUtil;
import com.tuservidor.cropregenerator.util.MessageUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.IntStream;

/**
 * /cropblock <help|give|upgradeblock|info>
 *
 * upgradeblock — solo consola o cropregenerator.admin
 *   Uso: /cropblock upgradeblock <mundo> <x> <y> <z> <nivel>
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

            // ── /cropblock give <jugador> <nivel> ────────────
            case "give" -> {
                if (!sender.hasPermission("cropregenerator.give")) {
                    MessageUtil.send(sender, "no-permission");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(MM.deserialize("<red>Uso: /cropblock give <jugador> <nivel>"));
                    return true;
                }

                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { MessageUtil.send(sender, "no-player"); return true; }

                int level = parseLevel(sender, args[2]);
                if (level == -1) return true;

                ItemStack item = ItemUtil.createRegeneratorItem(level);
                target.getInventory().addItem(item);
                MessageUtil.send(sender, "item-given",
                        "{level}", String.valueOf(level),
                        "{player}", target.getName());
            }

            // ── /cropblock upgradeblock <mundo> <x> <y> <z> <nivel> ──
            case "upgradeblock" -> {
                // Solo consola o admins
                if (!(sender instanceof ConsoleCommandSender)
                        && !sender.hasPermission("cropregenerator.admin")) {
                    MessageUtil.send(sender, "no-permission");
                    return true;
                }
                if (args.length < 6) {
                    sender.sendMessage(MM.deserialize(
                            "<red>Uso: /cropblock upgradeblock <mundo> <x> <y> <z> <nivel>"));
                    return true;
                }

                World world = Bukkit.getWorld(args[1]);
                if (world == null) {
                    sender.sendMessage(MM.deserialize("<red>Mundo '<white>" + args[1] + "<red>' no encontrado."));
                    return true;
                }

                int x, y, z;
                try {
                    x = Integer.parseInt(args[2]);
                    y = Integer.parseInt(args[3]);
                    z = Integer.parseInt(args[4]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(MM.deserialize("<red>Las coordenadas deben ser números enteros."));
                    return true;
                }

                int level = parseLevel(sender, args[5]);
                if (level == -1) return true;

                Location loc = new Location(world, x, y, z);
                RegeneratorBlock rb = plugin.getBlockDataManager().getBlock(loc);
                if (rb == null) {
                    sender.sendMessage(MM.deserialize(
                            "<red>No hay bloque regenerador en <white>" +
                            args[1] + " " + x + " " + y + " " + z + "<red>."));
                    return true;
                }

                if (!plugin.getUpgradeManager().levelExists(level)) {
                    MessageUtil.send(sender, "invalid-level",
                            "{max}", String.valueOf(plugin.getUpgradeManager().getMaxLevel()));
                    return true;
                }

                rb.setLevel(level);
                plugin.getHologramManager().spawnOrUpdate(rb);
                sender.sendMessage(MM.deserialize(
                        "<green>Bloque en <white>" + args[1] + " " + x + " " + y + " " + z +
                        "<green> actualizado al nivel <yellow>" + level + "<green>."));
            }

            // ── /cropblock info ──────────────────────────────
            case "info" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(MM.deserialize("<red>Solo jugadores pueden usar este comando."));
                    return true;
                }

                RegeneratorBlock rb = plugin.getBlockDataManager()
                        .getBlock(player.getLocation().subtract(0, 1, 0));
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

    // ── Helpers ──────────────────────────────────────────────

    /** Parsea y valida el nivel. Devuelve -1 si es inválido. */
    private int parseLevel(CommandSender sender, String raw) {
        int level;
        try {
            level = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            MessageUtil.send(sender, "invalid-level",
                    "{max}", String.valueOf(plugin.getUpgradeManager().getMaxLevel()));
            return -1;
        }
        if (!plugin.getUpgradeManager().levelExists(level)) {
            MessageUtil.send(sender, "invalid-level",
                    "{max}", String.valueOf(plugin.getUpgradeManager().getMaxLevel()));
            return -1;
        }
        return level;
    }

    private void sendHelp(CommandSender sender) {
        boolean isAdmin = !(sender instanceof Player p)
                || p.hasPermission("cropregenerator.admin");

        StringBuilder sb = new StringBuilder();
        sb.append("<dark_green>══ <green>CropRegenerator <dark_green>══\n");
        sb.append("<yellow>/cropblock give <jugador> <nivel> <gray>- Da un bloque\n");
        sb.append("<yellow>/cropblock info <gray>- Info del bloque bajo tus pies");
        if (isAdmin) {
            sb.append("\n<yellow>/cropblock upgradeblock <mundo> <x> <y> <z> <nivel> " +
                      "<gray>- Mejora un bloque por coordenadas");
        }
        sender.sendMessage(MM.deserialize(sb.toString()));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command command,
                                      @NotNull String alias,
                                      String[] args) {
        boolean isAdmin = !(sender instanceof Player p)
                || p.hasPermission("cropregenerator.admin");

        if (args.length == 1) {
            List<String> base = new java.util.ArrayList<>(List.of("give", "info", "help"));
            if (isAdmin) base.add("upgradeblock");
            return base;
        }

        // give
        if (args[0].equalsIgnoreCase("give")) {
            if (args.length == 2)
                return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            if (args.length == 3)
                return levelList();
        }

        // upgradeblock
        if (args[0].equalsIgnoreCase("upgradeblock") && isAdmin) {
            if (args.length == 2)
                return Bukkit.getWorlds().stream().map(World::getName).toList();
            if (args.length == 3) return List.of("<x>");
            if (args.length == 4) return List.of("<y>");
            if (args.length == 5) return List.of("<z>");
            if (args.length == 6) return levelList();
        }

        return List.of();
    }

    private List<String> levelList() {
        return IntStream.rangeClosed(1, plugin.getUpgradeManager().getMaxLevel())
                .mapToObj(String::valueOf).toList();
    }
}
