package com.tuservidor.cropregenerator.util;

import com.tuservidor.cropregenerator.CropRegeneratorPlugin;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

/**
 * Envía mensajes configurables con soporte MiniMessage y reemplazos.
 */
public class MessageUtil {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    /**
     * Envía el mensaje del config al sender.
     * @param replacements pares: "{placeholder}", "valor"
     */
    public static void send(CommandSender sender, String key, String... replacements) {
        CropRegeneratorPlugin plugin = CropRegeneratorPlugin.getInstance();
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        String msg    = plugin.getConfig().getString("messages." + key, key);

        for (int i = 0; i + 1 < replacements.length; i += 2) {
            msg = msg.replace(replacements[i], replacements[i + 1]);
        }

        sender.sendMessage(MM.deserialize(prefix + msg));
    }

    public static void send(CommandSender sender, String key) {
        send(sender, key, new String[0]);
    }
}