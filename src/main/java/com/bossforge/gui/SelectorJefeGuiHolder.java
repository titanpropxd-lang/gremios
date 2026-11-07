package com.bossforge.gui;

import com.bossforge.BossForgeContext;
import com.bossforge.model.BossTemplate;
import com.bossforge.model.Obelisco;
import com.bossforge.model.OpcionJefe;
import com.bossforge.util.MenuItemBuilder;
import com.bossforge.util.RequisitoLore;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;

/** Menu de los jugadores al pulsar el obelisco (estilo ARK): eliges que jefe invocar y ves lo que pide. */
public class SelectorJefeGuiHolder implements BossForgeGuiHolder {
    private final BossForgeContext ctx;
    private final Player jugador;
    private final Obelisco obelisco;
    private final List<OpcionJefe> disponibles = new ArrayList<>();
    private final Inventory inventario;

    public SelectorJefeGuiHolder(BossForgeContext ctx, Player jugador, Obelisco obelisco) {
        this.ctx = ctx;
        this.jugador = jugador;
        this.obelisco = obelisco;
        for (OpcionJefe o : obelisco.getOpciones()) if (o.estaCompleta()) disponibles.add(o);
        this.inventario = Bukkit.createInventory(this, 54, ChatColor.DARK_PURPLE + "Elige el jefe a invocar");
        construir();
    }

    @Override
    public Inventory getInventory() { return inventario; }

    private void construir() {
        for (int i = 0; i < disponibles.size() && i < 45; i++) {
            OpcionJefe opcion = disponibles.get(i);
            BossTemplate boss = ctx.bossTemplateManager.obtener(opcion.getBossTemplateId()).orElse(null);
            String problema = ctx.obeliskManager.razonPorLaQueLaOpcionNoSePuede(opcion);
            String nombre = OpcionJefeEditorGuiHolder.nombreDe(ctx, opcion);

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Requisitos para invocarlo:");
            lore.addAll(RequisitoLore.lineas(opcion.getRequisitos()));
            lore.add("");
            if (problema != null) lore.add(ChatColor.RED + problema);
            else lore.add(ChatColor.YELLOW + "Clic para elegir este jefe");

            inventario.setItem(i, MenuItemBuilder.de(problema != null ? Material.BARRIER
                    : boss != null && boss.esMythic() ? Material.DRAGON_HEAD : Material.SPAWNER)
                .nombre(ChatColor.LIGHT_PURPLE + nombre).lore(lore).build());
        }
        inventario.setItem(49, MenuItemBuilder.de(Material.BARRIER).nombre(ChatColor.RED + "Cerrar").build());
    }

    @Override
    public void manejarClic(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() != inventario) return;
        int slot = event.getSlot();
        if (slot == 49) { jugador.closeInventory(); return; }
        if (slot >= 45 || slot >= disponibles.size()) return;

        OpcionJefe opcion = disponibles.get(slot);
        String problema = ctx.obeliskManager.razonPorLaQueLaOpcionNoSePuede(opcion);
        if (problema != null) {
            jugador.sendMessage(ChatColor.RED + problema);
            return;
        }
        new DepositoGuiHolder(ctx, jugador, obelisco, opcion).abrir();
    }

    @Override
    public void manejarArrastre(InventoryDragEvent event) { event.setCancelled(true); }

    public void abrir() {
        jugador.openInventory(inventario);
    }
}
