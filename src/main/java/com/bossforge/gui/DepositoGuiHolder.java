package com.bossforge.gui;

import com.bossforge.BossForgeContext;
import com.bossforge.model.Obelisco;
import com.bossforge.model.OpcionJefe;
import com.bossforge.util.RequisitoLore;
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

public class DepositoGuiHolder implements BossForgeGuiHolder {
    private final BossForgeContext ctx;
    private final Player jugador;
    private final Obelisco obelisco;
    private final OpcionJefe opcion;
    private final Inventory inventario;
    private boolean procesado = false;

    public DepositoGuiHolder(BossForgeContext ctx, Player jugador, Obelisco obelisco, OpcionJefe opcion) {
        this.ctx = ctx;
        this.jugador = jugador;
        this.obelisco = obelisco;
        this.opcion = opcion;
        this.inventario = Bukkit.createInventory(this, 54, ChatColor.DARK_PURPLE + "Invocar: " + ChatColor.RESET + OpcionJefeEditorGuiHolder.nombreDe(ctx, opcion));
        construirControles();
    }

    @Override
    public Inventory getInventory() { return inventario; }

    private void construirControles() {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Deposita EXACTAMENTE estos items:");
        lore.addAll(RequisitoLore.lineas(opcion.getRequisitos()));
        inventario.setItem(45, MenuItemBuilder.de(Material.WRITTEN_BOOK).nombre(ChatColor.AQUA + "Requisitos").lore(lore).build());
        inventario.setItem(49, MenuItemBuilder.de(Material.LIME_CONCRETE).nombre(ChatColor.GREEN + "Confirmar").build());
        inventario.setItem(53, MenuItemBuilder.de(Material.RED_CONCRETE).nombre(ChatColor.RED + "Cancelar (te devuelve tus items)").build());
    }

    @Override
    public void manejarClic(InventoryClickEvent event) {
        boolean esControl = event.getClickedInventory() == inventario && event.getSlot() >= 45;
        if (!esControl) return;

        event.setCancelled(true);
        if (event.getSlot() == 49) {
            confirmar();
        } else if (event.getSlot() == 53) {
            procesado = true;
            jugador.closeInventory();
        }
    }

    private void confirmar() {
        ItemStack[] contenido = new ItemStack[45];
        for (int i = 0; i < 45; i++) contenido[i] = inventario.getItem(i);

        String error = ctx.obeliskManager.intentarActivar(obelisco, opcion, jugador, contenido, inventario);
        if (error != null) {
            jugador.sendMessage(ChatColor.RED + error);
            return;
        }

        jugador.sendMessage(ChatColor.GREEN + "¡Invocacion de " + OpcionJefeEditorGuiHolder.nombreDe(ctx, opcion) + ChatColor.GREEN + " iniciada! Quienes esten dentro de la esfera de luz iran a la arena.");
        devolverSobrantes();
        procesado = true;
        jugador.closeInventory();
    }

    /** Lo que se deposito de mas (o que no era requisito) vuelve al jugador en vez de perderse. */
    private void devolverSobrantes() {
        for (int i = 0; i < 45; i++) {
            ItemStack item = inventario.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                var sobrante = jugador.getInventory().addItem(item);
                sobrante.values().forEach(sobra -> jugador.getWorld().dropItemNaturally(jugador.getLocation(), sobra));
                inventario.setItem(i, null);
            }
        }
    }

    @Override
    public void alCerrar(InventoryCloseEvent event) {
        if (procesado) return;
        for (int i = 0; i < 45; i++) {
            ItemStack item = inventario.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                var sobrante = jugador.getInventory().addItem(item);
                sobrante.values().forEach(sobra -> jugador.getWorld().dropItemNaturally(jugador.getLocation(), sobra));
            }
        }
    }

    public void abrir() {
        jugador.openInventory(inventario);
    }
}
