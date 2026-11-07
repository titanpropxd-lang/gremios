package com.bossforge.gui;

import com.bossforge.BossForgeContext;
import com.bossforge.model.Obelisco;
import com.bossforge.util.MenuItemBuilder;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.List;

/**
 * Paleta de colores para el faro del obelisco, igual que se elige el
 * color del haz de luz de un obelisco en ARK: Survival Evolved.
 */
public class ObeliskColorGuiHolder implements BossForgeGuiHolder {
    private record OpcionColor(String nombre, Color color, Material material) { }

    private static final List<OpcionColor> PALETA = List.of(
        new OpcionColor("Blanco", Color.WHITE, Material.WHITE_DYE),
        new OpcionColor("Rojo", Color.RED, Material.RED_DYE),
        new OpcionColor("Naranja", Color.ORANGE, Material.ORANGE_DYE),
        new OpcionColor("Amarillo", Color.YELLOW, Material.YELLOW_DYE),
        new OpcionColor("Verde (lima)", Color.LIME, Material.LIME_DYE),
        new OpcionColor("Cian", Color.fromRGB(0, 255, 255), Material.CYAN_DYE),
        new OpcionColor("Azul", Color.BLUE, Material.BLUE_DYE),
        new OpcionColor("Morado", Color.PURPLE, Material.PURPLE_DYE),
        new OpcionColor("Magenta", Color.FUCHSIA, Material.MAGENTA_DYE),
        new OpcionColor("Negro", Color.BLACK, Material.BLACK_DYE)
    );

    private final BossForgeContext ctx;
    private final Player jugador;
    private final Obelisco obelisco;
    private final Inventory inventario;

    public ObeliskColorGuiHolder(BossForgeContext ctx, Player jugador, Obelisco obelisco) {
        this.ctx = ctx;
        this.jugador = jugador;
        this.obelisco = obelisco;
        this.inventario = Bukkit.createInventory(this, 27, ChatColor.DARK_PURPLE + "Color del faro: " + ChatColor.RESET + obelisco.getNombre());
        construir();
    }

    @Override
    public Inventory getInventory() { return inventario; }

    private void construir() {
        inventario.clear();
        for (int i = 0; i < PALETA.size(); i++) {
            OpcionColor opcion = PALETA.get(i);
            boolean seleccionado = mismoColor(opcion.color(), obelisco.getColorFaro());
            inventario.setItem(i, MenuItemBuilder.de(opcion.material())
                .nombre((seleccionado ? ChatColor.GREEN + "» " : ChatColor.WHITE) + opcion.nombre())
                .lore(seleccionado ? ChatColor.GRAY + "Color actual" : ChatColor.YELLOW + "Clic para elegir").build());
        }
        inventario.setItem(26, MenuItemBuilder.de(Material.ARROW).nombre(ChatColor.YELLOW + "« Volver").build());
    }

    private boolean mismoColor(Color a, Color b) {
        return a.getRed() == b.getRed() && a.getGreen() == b.getGreen() && a.getBlue() == b.getBlue();
    }

    @Override
    public void manejarClic(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() != inventario) return;
        int slot = event.getSlot();

        if (slot == 26) { new ObeliscoConfigGuiHolder(ctx, jugador, obelisco).abrir(); return; }
        if (slot < 0 || slot >= PALETA.size()) return;

        obelisco.setColorFaro(PALETA.get(slot).color());
        ctx.obeliskManager.actualizar(obelisco);
        construir();
        jugador.openInventory(inventario);
    }

    public void abrir() {
        jugador.openInventory(inventario);
    }
}
