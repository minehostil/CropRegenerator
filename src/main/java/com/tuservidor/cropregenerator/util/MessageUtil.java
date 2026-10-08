package com.tuservidor.cropregenerator.util;

import com.tuservidor.cropregenerator.CropRegeneratorPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

/** Mensajes configurables con & codes y &#RRGGBB. */
public final class MessageUtil {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder().character('&').hexColors().build();
    private MessageUtil() {}

    public static Component component(String key, String... replacements) {
        CropRegeneratorPlugin plugin = CropRegeneratorPlugin.getInstance();
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        String msg = plugin.getConfig().getString("messages." + key, key);
        for (int i = 0; i + 1 < replacements.length; i += 2) msg = msg.replace(replacements[i], replacements[i + 1]);
        return LEGACY.deserialize(prefix + msg);
    }
    public static void send(CommandSender sender, String key, String... replacements) { sender.sendMessage(component(key, replacements)); }
}