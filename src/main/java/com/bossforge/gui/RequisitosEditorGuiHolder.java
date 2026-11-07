package com.bossforge.gui;

import com.bossforge.BossForgeContext;
import com.bossforge.model.Obelisco;
import com.bossforge.model.OpcionJefe;
import com.bossforge.util.MenuItemBuilder;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Zona editable (slots 0-44): coloca aqui, arrastrando desde tu
 * propio inventario, los items EXACTOS que se deben depositar —
 * si le pusiste nombre/encantamiento con un yunque antes de traerlo
 * aqui, ese nombre/encantamiento pasa a formar parte del requisito
 * (ver ItemMatchUtil, que compara con ItemStack#isSimilar).
 * La fila de control (45-53) es lo unico que este GUI cancela; el
 * resto de clics se dejan pasar para que colocar/quitar items
 * funcione con el drag-and-drop normal de Minecraft.
 */
public class RequisitosEditorGuiHolder implements BossForgeGuiHolder {
    private final BossForgeContext ctx;
    private final Player jugador;
    private final Obelisco obelisco;
    private final OpcionJefe opcion;
    private final Inventory inventario;
    private boolean guardado = false;

    public RequisitosEditorGuiHolder(BossForgeContext ctx, Player jugador, Obelisco obelisco, OpcionJefe opcion) {
        this.ctx = ctx;
        this.jugador = jugador;
        this.obelisco = obelisco;
        this.opcion = opcion;
        this.inventario = Bukkit.createInventory(this, 54, ChatColor.DARK_GREEN + "Requisitos: " + ChatColor.RESET + OpcionJefeEditorGuiHolder.nombreDe(ctx, opcion));
        for (int i = 0; i < opcion.getRequisitos().size() && i < 45; i++) {
            inventario.setItem(i, opcion.getRequisitos().get(i).clone());
        }
        construirControles();
    }

    @Override
    public Inventory getInventory() { return inventario; }

    private void construirControles() {
        inventario.setItem(45, MenuItemBuilder.de(Material.BOOK)
            .nombre(ChatColor.AQUA + "Instrucciones")
            .lore(
                ChatColor.GRAY + "Arrastra items desde tu inventario",
                ChatColor.GRAY + "a las casillas de arriba.",
                ChatColor.GRAY + "La cantidad que pongas es la",
                ChatColor.GRAY + "cantidad requerida de ese item.",
                ChatColor.GRAY + "Nombre/encantamientos se exigen exactos."
            ).build());
        inventario.setItem(49, MenuItemBuilder.de(Material.LIME_DYE).nombre(ChatColor.GREEN + "Guardar").build());
        inventario.setItem(53, MenuItemBuilder.de(Material.ARROW).nombre(ChatColor.YELLOW + "« Volver (sin guardar)").build());
    }

    @Override
    public void manejarClic(InventoryClickEvent event) {
        boolean esSlotDeControl = event.getClickedInventory() == inventario && event.getSlot() >= 45;
        if (!esSlotDeControl) return; // deja pasar todo lo demas para poder colocar/quitar items normalmente

        event.setCancelled(true);
        if (event.getSlot() == 49) {
            guardar();
        } else if (event.getSlot() == 53) {
            guardado = true; // evita que alCerrar() devuelva items, ya volvemos sin tocar nada
            new OpcionJefeEditorGuiHolder(ctx, jugador, obelisco, opcion).abrir();
        }
    }

    private void guardar() {
        List<ItemStack> requisitos = new ArrayList<>();
        for (int i = 0; i < 45; i++) {
            ItemStack item = inventario.getItem(i);
            if (item != null && item.getType() != Material.AIR) requisitos.add(item.clone());
        }
        opcion.setRequisitos(requisitos);
        ctx.obeliskManager.actualizar(obelisco);
        guardado = true;
        jugador.sendMessage(ChatColor.GREEN + "Requisitos guardados (" + requisitos.size() + " item(s)).");
        new OpcionJefeEditorGuiHolder(ctx, jugador, obelisco, opcion).abrir();
    }

    @Override
    public void alCerrar(InventoryCloseEvent event) {
        if (guardado) return;
        // Se cerro sin pulsar "Guardar" ni "Volver": devuelve al jugador
        // cualquier item que hubiera quedado en el editor para que no se pierda.
        for (int i = 0; i < 45; i++) {
            ItemStack item = inventario.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                devolver(item);
            }
        }
    }

    private void devolver(ItemStack item) {
        var sobrante = jugador.getInventory().addItem(item);
        sobrante.values().forEach(i -> jugador.getWorld().dropItemNaturally(jugador.getLocation(), i));
    }

    public void abrir() {
        jugador.openInventory(inventario);
    }
}
