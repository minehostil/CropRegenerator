package com.tuservidor.cropregenerator.hooks;

import com.tuservidor.cropregenerator.CropRegeneratorPlugin;
import com.tuservidor.cropregenerator.model.RegeneratorBlock;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Comparator;

/**
 * Expansión de PlaceholderAPI para CropRegenerator.
 *
 * Placeholders disponibles (basados en la isla del jugador con SSB2,
 * o en el bloque más cercano si SSB2 no está presente):
 *
 *   %cropregenerator_level%       → Nivel más alto de bloque activo en la isla del jugador
 *   %cropregenerator_next_regen%  → Segundos hasta la próxima regeneración (bloque con menos tiempo restante)
 */
public class PlaceholderHook extends PlaceholderExpansion {

    private final CropRegeneratorPlugin plugin;

    public PlaceholderHook(CropRegeneratorPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Metadata de la expansión ─────────────────────────────

    @Override
    public @NotNull String getIdentifier() {
        return "cropregenerator";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    /**
     * Devuelve true para que PAPI no desregistre la expansión
     * si el plugin se recarga.
     */
    @Override
    public boolean persist() {
        return true;
    }

    // ── Lógica de placeholders ───────────────────────────────

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";

        Collection<RegeneratorBlock> blocks = getBlocksForPlayer(player);

        return switch (params.toLowerCase()) {

            // Nivel más alto de bloque activo en la isla del jugador
            case "level" -> {
                if (blocks.isEmpty()) yield "0";
                yield String.valueOf(
                        blocks.stream()
                                .mapToInt(RegeneratorBlock::getLevel)
                                .max()
                                .orElse(0)
                );
            }

            // Segundos hasta la próxima regeneración (el bloque con menos tiempo restante)
            case "next_regen" -> {
                if (blocks.isEmpty()) yield "-";
                long min = blocks.stream()
                        .mapToLong(RegeneratorBlock::getSecondsUntilRegen)
                        .min()
                        .orElse(0);
                yield min + "s";
            }

            default -> null; // placeholder desconocido
        };
    }

    // ── Helper ───────────────────────────────────────────────

    /**
     * Devuelve los bloques del jugador.
     * - Con SSB2: filtra por isla del jugador.
     * - Sin SSB2: devuelve todos los bloques cuyo owner sea el jugador.
     */
    private Collection<RegeneratorBlock> getBlocksForPlayer(Player player) {
        Collection<RegeneratorBlock> all = plugin.getBlockDataManager().getAllBlocks();

        if (plugin.hasSuperior()) {
            // Buscar por ubicación del jugador — funciona para dueños y miembros
            String islandId = plugin.getSuperiorHook().getIslandIdAt(player.getLocation());
            // Fallback: isla propia si no está parado en ninguna isla
            if (islandId == null) {
                islandId = plugin.getSuperiorHook().getPlayerIslandId(player);
            }
            if (islandId == null) return java.util.List.of();

            final String finalIslandId = islandId;
            return all.stream()
                    .filter(rb -> finalIslandId.equals(
                            plugin.getBlockDataManager().getIslandIdForBlock(rb)))
                    .toList();
        }

        // Sin SSB2: bloques del propio jugador
        return all.stream()
                .filter(rb -> rb.getOwnerUUID().equals(player.getUniqueId()))
                .toList();
    }
}