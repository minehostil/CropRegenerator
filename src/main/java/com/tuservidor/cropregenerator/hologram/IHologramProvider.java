package com.tuservidor.cropregenerator.hologram;

import com.tuservidor.cropregenerator.model.RegeneratorBlock;

/**
 * Interfaz común para proveedores de hologramas.
 * Implementaciones: NativeHologramProvider (TextDisplay) y GHoloProvider (GHolo).
 */
public interface IHologramProvider {

    /** Spawna o recrea el holograma de un bloque. */
    void spawnOrUpdate(RegeneratorBlock rb);

    /**
     * Actualiza solo el texto si el countdown cambió.
     * No spawna entidades nuevas por sí solo.
     */
    void updateText(RegeneratorBlock rb);

    /** Elimina el holograma de un bloque. */
    void remove(RegeneratorBlock rb);

    /** Elimina todos los hologramas (onDisable). */
    void removeAll();
}