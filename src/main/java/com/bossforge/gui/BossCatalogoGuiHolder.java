package com.bossforge.gui;

import com.bossforge.BossForgeContext;
import com.bossforge.model.BossTemplate;
import com.bossforge.model.Obelisco;
import com.bossforge.util.MenuItemBuilder;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.List;

public class BossCatalogoGuiHolder implements BossForgeGuiHolder {
    private final BossForgeContext ctx;
    private final Player jugador;
    private final Obelisco obeliscoParaAsignar;
    private final Inventory inventario;

    public BossCatalogoGuiHolder(BossForgeContext ctx, Player jugador, Obelisco obeliscoParaAsignar) {
        this.ctx = ctx;
        this.jugador = jugador;
        this.obeliscoParaAsignar = obeliscoParaAsignar;
        String titulo = obeliscoParaAsignar != null ? "Anadir jefe a: " + obeliscoParaAsignar.getNombre() : "Catalogo de Jefes";
        this.inventario = Bukkit.createInventory(this, 54, ChatColor.DARK_RED + titulo);
        construir();
    }

    @Override
    public Inventory getInventory() { return inventario; }

    private void construir() {
        inventario.clear();
        List<BossTemplate> plantillas = ctx.bossTemplateManager.todas();
        for (int i = 0; i < plantillas.size() && i < 45; i++) {
            inventario.setItem(i, icono(plantillas.get(i)));
        }
        inventario.setItem(49, MenuItemBuilder.de(Material.EMERALD)
            .nombre(ChatColor.GREEN + "+ Crear nuevo jefe").build());
        inventario.setItem(53, MenuItemBuilder.de(Material.ARROW).nombre(ChatColor.YELLOW + "« Volver").build());
    }

    private org.bukkit.inventory.ItemStack icono(BossTemplate boss) {
        if (boss.esMythic()) {
            return MenuItemBuilder.de(Material.DRAGON_HEAD)
                .nombre(ChatColor.LIGHT_PURPLE + ChatColor.translateAlternateColorCodes('&', boss.getNombre()))
                .lore(
                    ChatColor.GRAY + "MythicMobs: " + ChatColor.WHITE + boss.getMythicMobId(),
                    "",
                    obeliscoParaAsignar != null ? ChatColor.YELLOW + "Clic para anadirlo y configurar sus requisitos" : ChatColor.YELLOW + "Shift+clic para eliminar"
                ).build();
        }
        return MenuItemBuilder.de(Material.SPAWNER)
            .nombre(ChatColor.RED + boss.getNombre())
            .lore(
                ChatColor.GRAY + "Base: " + ChatColor.WHITE + boss.getTipoBase(),
                ChatColor.GRAY + "Vida: " + ChatColor.WHITE + (int) boss.getVidaMaxima(),
                ChatColor.GRAY + "Dano: " + ChatColor.WHITE + (int) boss.getDanoAtaque(),
                ChatColor.GRAY + "Habilidades: " + ChatColor.WHITE + boss.getHabilidades().size(),
                "",
                obeliscoParaAsignar != null ? ChatColor.YELLOW + "Clic para anadirlo y configurar sus requisitos" : ChatColor.YELLOW + "Clic para editar / Shift+clic para eliminar"
            ).build();
    }

    @Override
    public void manejarClic(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() != inventario) return;
        int slot = event.getSlot();

        if (slot == 53) { volver(); return; }
        if (slot == 49 && obeliscoParaAsignar == null) {
            BossTemplate nuevo = ctx.bossTemplateManager.crear();
            new BossEditorGuiHolder(ctx, jugador, nuevo).abrir();
            return;
        }

        List<BossTemplate> plantillas = ctx.bossTemplateManager.todas();
        if (slot >= 45 || slot >= plantillas.size()) return;
        BossTemplate boss = plantillas.get(slot);

        if (obeliscoParaAsignar != null) {
            com.bossforge.model.OpcionJefe opcion = new com.bossforge.model.OpcionJefe(boss.getId());
            obeliscoParaAsignar.getOpciones().add(opcion);
            ctx.obeliskManager.actualizar(obeliscoParaAsignar);
            jugador.sendMessage(ChatColor.GREEN + "Jefe anadido: " + boss.getNombre()
                + ChatColor.GRAY + ". Ahora configura sus requisitos y su loot.");
            new OpcionJefeEditorGuiHolder(ctx, jugador, obeliscoParaAsignar, opcion).abrir();
            return;
        }

        if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
            ctx.bossTemplateManager.eliminar(boss.getId());
            jugador.sendMessage(ChatColor.RED + "Jefe eliminado: " + boss.getNombre());
            construir();
            jugador.openInventory(inventario);
        } else if (!boss.esMythic()) {
            new BossEditorGuiHolder(ctx, jugador, boss).abrir();
        }
    }

    private void volver() {
        if (obeliscoParaAsignar != null) new ObeliscoJefesGuiHolder(ctx, jugador, obeliscoParaAsignar).abrir();
        else new MenuPrincipalGuiHolder(ctx, jugador, 0).abrir();
    }

    public void abrir() {
        jugador.openInventory(inventario);
    }
}
