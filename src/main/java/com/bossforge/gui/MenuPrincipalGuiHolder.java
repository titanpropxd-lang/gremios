package com.bossforge.gui;

import com.bossforge.BossForgeContext;
import com.bossforge.model.Obelisco;
import com.bossforge.util.MenuItemBuilder;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class MenuPrincipalGuiHolder implements BossForgeGuiHolder {
    private static final int SLOTS_POR_PAGINA = 45;

    private final BossForgeContext ctx;
    private final Player jugador;
    private final int pagina;
    private final Inventory inventario;

    public MenuPrincipalGuiHolder(BossForgeContext ctx, Player jugador, int pagina) {
        this.ctx = ctx;
        this.jugador = jugador;
        this.pagina = pagina;
        this.inventario = Bukkit.createInventory(this, 54, ChatColor.DARK_PURPLE + "Obeliscos (BossForge)");
        construir();
    }

    @Override
    public Inventory getInventory() { return inventario; }

    private void construir() {
        inventario.clear();
        List<Obelisco> todos = ctx.obeliskManager.todos();
        int desde = pagina * SLOTS_POR_PAGINA;
        int hasta = Math.min(todos.size(), desde + SLOTS_POR_PAGINA);

        for (int i = desde; i < hasta; i++) {
            inventario.setItem(i - desde, iconoDe(todos.get(i)));
        }

        inventario.setItem(45, pagina > 0 ? flecha("« Pagina anterior") : relleno());
        inventario.setItem(47, MenuItemBuilder.de(Material.ZOMBIE_HEAD)
            .nombre(ChatColor.GOLD + "Catalogo de Jefes")
            .lore(ChatColor.GRAY + "Crea y edita las plantillas de jefe.").build());
        inventario.setItem(49, MenuItemBuilder.de(Material.NETHER_STAR)
            .nombre(ChatColor.GREEN + "+ Crear nuevo obelisco")
            .lore(ChatColor.GRAY + "Clic para crear uno nuevo.").build());
        inventario.setItem(53, hasta < todos.size() ? flecha("Pagina siguiente »") : relleno());
    }

    private ItemStack flecha(String texto) {
        return MenuItemBuilder.de(Material.ARROW).nombre(ChatColor.YELLOW + texto).build();
    }

    private ItemStack relleno() {
        return MenuItemBuilder.de(Material.GRAY_STAINED_GLASS_PANE).nombre(" ").build();
    }

    private ItemStack iconoDe(Obelisco o) {
        Material material = switch (o.getEstado()) {
            case EN_COMBATE -> Material.REDSTONE_BLOCK;
            case EN_COOLDOWN -> Material.YELLOW_STAINED_GLASS;
            default -> o.isActivo() ? Material.BEACON : Material.GRAY_CONCRETE;
        };
        String estadoTexto = switch (o.getEstado()) {
            case EN_COMBATE -> ChatColor.RED + "En combate";
            case EN_COOLDOWN -> ChatColor.YELLOW + "En cooldown";
            default -> o.isActivo() ? ChatColor.GREEN + "Esperando" : ChatColor.GRAY + "Desactivado";
        };
        return MenuItemBuilder.de(material)
            .nombre(ChatColor.AQUA + o.getNombre())
            .lore(
                ChatColor.GRAY + "Estado: " + estadoTexto,
                ChatColor.GRAY + "Ubicacion: " + (o.getUbicacion() != null ? ChatColor.WHITE + "vinculada" : ChatColor.RED + "sin vincular"),
                ChatColor.GRAY + "Jefes: " + (o.getOpciones().isEmpty() ? ChatColor.RED + "sin asignar" : ChatColor.WHITE + String.valueOf(o.getOpciones().size())),
                "",
                ChatColor.YELLOW + "Clic para configurar"
            ).build();
    }

    @Override
    public void manejarClic(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() != inventario) return;
        int slot = event.getSlot();

        if (slot == 45 && pagina > 0) { new MenuPrincipalGuiHolder(ctx, jugador, pagina - 1).abrir(); return; }
        if (slot == 53) {
            List<Obelisco> todos = ctx.obeliskManager.todos();
            if ((pagina + 1) * SLOTS_POR_PAGINA < todos.size()) new MenuPrincipalGuiHolder(ctx, jugador, pagina + 1).abrir();
            return;
        }
        if (slot == 49) {
            Obelisco nuevo = ctx.obeliskManager.crear("Obelisco " + (ctx.obeliskManager.todos().size() + 1));
            new ObeliscoConfigGuiHolder(ctx, jugador, nuevo).abrir();
            return;
        }
        if (slot == 47) {
            new BossCatalogoGuiHolder(ctx, jugador, null).abrir();
            return;
        }

        int indice = pagina * SLOTS_POR_PAGINA + slot;
        List<Obelisco> todos = ctx.obeliskManager.todos();
        if (event.getClick() == ClickType.LEFT && slot < 45 && indice < todos.size()) {
            new ObeliscoConfigGuiHolder(ctx, jugador, todos.get(indice)).abrir();
        }
    }

    public void abrir() {
        jugador.openInventory(inventario);
    }
}
