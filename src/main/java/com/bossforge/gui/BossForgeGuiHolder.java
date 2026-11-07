package com.bossforge.gui;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * Cada GUI de BossForge implementa esto. Un solo listener global
 * (ver listeners/GuiClickListener) delega aqui — cada GUI decide por
 * si misma que clics cancelar: los menus "de lista/seleccion"
 * (bloqueados) cancelan todo y actuan por indice de slot; los
 * editores de items (requisitos, loot, deposito) solo cancelan los
 * clics en su fila de control y dejan pasar el resto para que
 * colocar/quitar items funcione con el drag-and-drop normal de
 * Minecraft.
 */
public interface BossForgeGuiHolder extends InventoryHolder {
    void manejarClic(InventoryClickEvent event);

    default void manejarArrastre(InventoryDragEvent event) { }

    /** Se llama al cerrarse la GUI, para devolver items sin guardar si corresponde. */
    default void alCerrar(org.bukkit.event.inventory.InventoryCloseEvent event) { }
}
