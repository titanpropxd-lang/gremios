package com.bossforge.gui;

import com.bossforge.BossForgeContext;
import com.bossforge.model.LootEntry;
import com.bossforge.model.Obelisco;
import com.bossforge.model.OpcionJefe;
import com.bossforge.util.LootLoreCodec;
import com.bossforge.util.MenuItemBuilder;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class LootEditorGuiHolder implements BossForgeGuiHolder {
    private final BossForgeContext ctx;
    private final Player jugador;
    private final Obelisco obelisco;
    private final OpcionJefe opcion;
    private final Inventory inventario;
    private boolean guardado = false;

    public LootEditorGuiHolder(BossForgeContext ctx, Player jugador, Obelisco obelisco, OpcionJefe opcion) {
        this.ctx = ctx;
        this.jugador = jugador;
        this.obelisco = obelisco;
        this.opcion = opcion;
        this.inventario = Bukkit.createInventory(this, 54, ChatColor.DARK_GREEN + "Loot: " + ChatColor.RESET + OpcionJefeEditorGuiHolder.nombreDe(ctx, opcion));
        for (int i = 0; i < opcion.getLoot().size() && i < 45; i++) {
            LootEntry entrada = opcion.getLoot().get(i);
            inventario.setItem(i, LootLoreCodec.conProbabilidadEnLore(entrada.getItem(), entrada.getProbabilidad()));
        }
        construirControles();
    }

    @Override
    public Inventory getInventory() { return inventario; }

    private void construirControles() {
        inventario.setItem(45, MenuItemBuilder.de(Material.BOOK)
            .nombre(ChatColor.AQUA + "Instrucciones")
            .lore(
                ChatColor.GRAY + "Arrastra items desde tu inventario.",
                ChatColor.GRAY + "Clic izq/der sobre un item ya puesto",
                ChatColor.GRAY + "(con la mano vacia) sube/baja su %.",
                ChatColor.GRAY + "Shift+clic para quitarlo del todo."
            ).build());
        inventario.setItem(49, MenuItemBuilder.de(Material.LIME_DYE).nombre(ChatColor.GREEN + "Guardar").build());
        inventario.setItem(53, MenuItemBuilder.de(Material.ARROW).nombre(ChatColor.YELLOW + "« Volver (sin guardar)").build());
    }

    @Override
    public void manejarClic(InventoryClickEvent event) {
        boolean enElEditor = event.getClickedInventory() == inventario;
        int slot = event.getSlot();

        if (enElEditor && slot >= 45) {
            event.setCancelled(true);
            if (slot == 49) guardar();
            else if (slot == 53) { guardado = true; new OpcionJefeEditorGuiHolder(ctx, jugador, obelisco, opcion).abrir(); }
            return;
        }

        if (!enElEditor || slot >= 45) return;

        ItemStack clickeado = inventario.getItem(slot);
        boolean cursorVacio = event.getCursor() == null || event.getCursor().getType() == Material.AIR;
        boolean esClicSimple = event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT;

        if (clickeado != null && clickeado.getType() != Material.AIR && cursorVacio && esClicSimple) {
            event.setCancelled(true);
            double actual = LootLoreCodec.extraerProbabilidad(clickeado, 100);
            double delta = event.getClick() == ClickType.LEFT ? 5 : -5;
            double nueva = Math.max(0, Math.min(100, actual + delta));
            inventario.setItem(slot, LootLoreCodec.conProbabilidadEnLore(clickeado, nueva));
        }
    }

    private void guardar() {
        List<LootEntry> loot = new ArrayList<>();
        for (int i = 0; i < 45; i++) {
            ItemStack item = inventario.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;
            double probabilidad = LootLoreCodec.extraerProbabilidad(item, 100);
            ItemStack limpio = LootLoreCodec.sinLoreDeProbabilidad(item);
            loot.add(new LootEntry(limpio, probabilidad));
        }
        opcion.setLoot(loot);
        ctx.obeliskManager.actualizar(obelisco);
        guardado = true;
        jugador.sendMessage(ChatColor.GREEN + "Loot guardado (" + loot.size() + " item(s)).");
        new OpcionJefeEditorGuiHolder(ctx, jugador, obelisco, opcion).abrir();
    }

    @Override
    public void alCerrar(InventoryCloseEvent event) {
        if (guardado) return;
        for (int i = 0; i < 45; i++) {
            ItemStack item = inventario.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                devolver(LootLoreCodec.sinLoreDeProbabilidad(item));
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
