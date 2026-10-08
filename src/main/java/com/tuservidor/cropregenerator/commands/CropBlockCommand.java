package com.tuservidor.cropregenerator.commands;

import com.tuservidor.cropregenerator.CropRegeneratorPlugin;
import com.tuservidor.cropregenerator.model.RegeneratorBlock;
import com.tuservidor.cropregenerator.util.ItemUtil;
import com.tuservidor.cropregenerator.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
 * /cropblock <help|give|upgradeblock|info|reload>
 *
 * upgradeblock — solo consola o cropregenerator.admin
 *   Uso: /cropblock upgradeblock <mundo> <x> <y> <z> <nivel>
 * reload — solo cropregenerator.admin
 */
public class CropBlockCommand implements CommandExecutor, TabCompleter {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder().character('&').hexColors().build();
    private static Component text(String s) { return LEGACY.deserialize(s); }
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
                    sender.sendMessage(text("&cUso: /cropblock give <jugador> <nivel>"));
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
                    sender.sendMessage(text(
                            "&cUso: /cropblock upgradeblock <mundo> <x> <y> <z> <nivel>"));
                    return true;
                }

                World world = Bukkit.getWorld(args[1]);
                if (world == null) {
                    sender.sendMessage(text("&cMundo '&f" + args[1] + "&c' no encontrado."));
                    return true;
                }

                int x, y, z;
                try {
                    x = Integer.parseInt(args[2]);
                    y = Integer.parseInt(args[3]);
                    z = Integer.parseInt(args[4]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(text("&cLas coordenadas deben ser números enteros."));
                    return true;
                }

                int level = parseLevel(sender, args[5]);
                if (level == -1) return true;

                Location loc = new Location(world, x, y, z);
                RegeneratorBlock rb = plugin.getBlockDataManager().getBlock(loc);
                if (rb == null) {
                    sender.sendMessage(text(
                            "&cNo hay bloque regenerador en &f" +
                            args[1] + " " + x + " " + y + " " + z + "&c."));
                    return true;
                }

                if (!plugin.getUpgradeManager().levelExists(level)) {
                    MessageUtil.send(sender, "invalid-level",
                            "{max}", String.valueOf(Math.max(plugin.getUpgradeManager().getMaxTime(), Math.max(plugin.getUpgradeManager().getMaxRadius(), plugin.getUpgradeManager().getMaxCrops()))));
                    return true;
                }

                rb.setLevel(level);
                plugin.getHologramManager().spawnOrUpdate(rb);
                sender.sendMessage(text(
                        "&aBloque en &f" + args[1] + " " + x + " " + y + " " + z +
                        "&a actualizado al nivel &e" + level + "&a."));
            }

            // ── /cropblock info ──────────────────────────────
            case "info" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(text("&cSolo jugadores pueden usar este comando."));
                    return true;
                }

                RegeneratorBlock rb = plugin.getBlockDataManager()
                        .getBlock(player.getLocation().subtract(0, 1, 0));
                if (rb == null) {
                    player.sendMessage(text("&cNo estás sobre un bloque regenerador."));
                    return true;
                }
                player.sendMessage(text(
                        "&8══ &aInfo del Bloque &8══\n" +
                        "&7Dueño: &f" + Bukkit.getOfflinePlayer(rb.getOwnerUUID()).getName() + "\n" +
                        "&7Tiempo: &b" + rb.getTimeLevel() + " &8| &7Radio: &a" + rb.getRadiusLevel() + " &8| &7Cultivos: &e" + rb.getCropsLevel() + "\n" +
                        "&7Radio efectivo: &a" + plugin.getUpgradeManager().getRadius(rb.getRadiusLevel()) + " bloques\n" +
                        "&7Intervalo: &b" + plugin.getUpgradeManager().getInterval(rb.getTimeLevel()) + "s\n" +
                        "&7Próx. regen: &d" + rb.getSecondsUntilRegen() + "s"
                ));
            }

            // ── /cropblock reload ────────────────────────────
            case "reload" -> {
                if (!sender.hasPermission("cropregenerator.admin")) {
                    MessageUtil.send(sender, "no-permission");
                    return true;
                }

                plugin.reloadConfig();
                plugin.getUpgradeManager().reload();
                plugin.getRegeneratorManager().reload();
                plugin.getParticleManager().start();

                // Reconstruir cache estático de todos los hologramas activos
                for (com.tuservidor.cropregenerator.model.RegeneratorBlock rb
                        : plugin.getBlockDataManager().getAllBlocks()) {
                    plugin.getHologramManager().spawnOrUpdate(rb);
                }

                sender.sendMessage(text(
                        "&8[&aCropRegen&8] &aConfiguración recargada correctamente."));
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
                    "{max}", String.valueOf(Math.max(plugin.getUpgradeManager().getMaxTime(), Math.max(plugin.getUpgradeManager().getMaxRadius(), plugin.getUpgradeManager().getMaxCrops()))));
            return -1;
        }
        if (!plugin.getUpgradeManager().levelExists(level)) {
            MessageUtil.send(sender, "invalid-level",
                    "{max}", String.valueOf(Math.max(plugin.getUpgradeManager().getMaxTime(), Math.max(plugin.getUpgradeManager().getMaxRadius(), plugin.getUpgradeManager().getMaxCrops()))));
            return -1;
        }
        return level;
    }

    private void sendHelp(CommandSender sender) {
        boolean isAdmin = !(sender instanceof Player p)
                || p.hasPermission("cropregenerator.admin");

        StringBuilder sb = new StringBuilder();
        sb.append("&8══ &aCropRegenerator &8══\n");
        sb.append("&e/cropblock give <jugador> <nivel> &7- Da un bloque\n");
        sb.append("&e/cropblock info &7- Info del bloque bajo tus pies");
        if (isAdmin) {
            sb.append("\n&e/cropblock upgradeblock <mundo> <x> <y> <z> <nivel> " +
                      "&7- Mejora un bloque por coordenadas");
            sb.append("\n&e/cropblock reload &7- Recarga la configuración");
        }
        sender.sendMessage(text(sb.toString()));
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
            if (isAdmin) { base.add("upgradeblock"); base.add("reload"); }
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
        return IntStream.rangeClosed(1, Math.max(plugin.getUpgradeManager().getMaxTime(), Math.max(plugin.getUpgradeManager().getMaxRadius(), plugin.getUpgradeManager().getMaxCrops())))
                .mapToObj(String::valueOf).toList();
    }
}