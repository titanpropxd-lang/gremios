package com.bossforge.gui;

import com.bossforge.BossForgeContext;
import com.bossforge.model.BossTemplate;
import com.bossforge.model.Obelisco;
import com.bossforge.model.OpcionJefe;
import com.bossforge.util.MenuItemBuilder;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;

/** Configura UNA opcion de jefe de un obelisco: sus requisitos, su loot, o quitarla. */
public class OpcionJefeEditorGuiHolder implements BossForgeGuiHolder {
    private final BossForgeContext ctx;
    private final Player jugador;
    private final Obelisco obelisco;
    private final OpcionJefe opcion;
    private final Inventory inventario;

    public OpcionJefeEditorGuiHolder(BossForgeContext ctx, Player jugador, Obelisco obelisco, OpcionJefe opcion) {
        this.ctx = ctx;
        this.jugador = jugador;
        this.obelisco = obelisco;
        this.opcion = opcion;
        this.inventario = Bukkit.createInventory(this, 27, ChatColor.DARK_RED + "Jefe: " + ChatColor.RESET + nombreDe(ctx, opcion));
        construir();
    }

    /** Nombre legible del jefe de una opcion (con colores '&' traducidos). */
    public static String nombreDe(BossForgeContext ctx, OpcionJefe opcion) {
        return ctx.bossTemplateManager.obtener(opcion.getBossTemplateId())
            .map(t -> ChatColor.translateAlternateColorCodes('&', t.getNombre()))
            .orElse("(jefe eliminado)");
    }

    @Override
    public Inventory getInventory() { return inventario; }

    private void construir() {
        BossTemplate boss = ctx.bossTemplateManager.obtener(opcion.getBossTemplateId()).orElse(null);
        List<String> lore = new ArrayList<>();
        if (boss == null) lore.add(ChatColor.RED + "Este jefe ya no existe.");
        else if (boss.esMythic()) lore.add(ChatColor.GRAY + "MythicMobs: " + ChatColor.WHITE + boss.getMythicMobId());
        else lore.add(ChatColor.GRAY + "BossForge: " + ChatColor.WHITE + boss.getTipoBase());
        lore.add(opcion.estaCompleta() ? ChatColor.GREEN + "Lista para invocarse" : ChatColor.RED + "Incompleta: pon al menos un requisito");
        inventario.setItem(4, MenuItemBuilder.de(boss != null && boss.esMythic() ? Material.DRAGON_HEAD : Material.SPAWNER)
            .nombre(ChatColor.LIGHT_PURPLE + nombreDe(ctx, opcion)).lore(lore).build());

        inventario.setItem(11, MenuItemBuilder.de(Material.CHEST_MINECART)
            .nombre(ChatColor.GOLD + "Requisitos de este jefe (" + opcion.getRequisitos().size() + ")")
            .lore(ChatColor.GRAY + "Los items que hay que depositar",
                  ChatColor.GRAY + "para invocarlo. Si les pones nombre o",
                  ChatColor.GRAY + "encantamiento, se exige ESE item exacto.").build());
        inventario.setItem(15, MenuItemBuilder.de(Material.CHEST)
            .nombre(ChatColor.GOLD + "Loot de este jefe (" + opcion.getLoot().size() + ")")
            .lore(ChatColor.GRAY + "Lo que suelta al derrotarlo,",
                  ChatColor.GRAY + "cada item con su % de probabilidad.").build());
        inventario.setItem(22, MenuItemBuilder.de(Material.ARROW).nombre(ChatColor.YELLOW + "« Volver a los jefes").build());
        inventario.setItem(26, MenuItemBuilder.de(Material.LAVA_BUCKET)
            .nombre(ChatColor.RED + "Quitar este jefe del obelisco")
            .lore(ChatColor.GRAY + "Shift + clic para confirmar.").build());
    }

    @Override
    public void manejarClic(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() != inventario) return;
        switch (event.getSlot()) {
            case 11 -> new RequisitosEditorGuiHolder(ctx, jugador, obelisco, opcion).abrir();
            case 15 -> new LootEditorGuiHolder(ctx, jugador, obelisco, opcion).abrir();
            case 22 -> new ObeliscoJefesGuiHolder(ctx, jugador, obelisco).abrir();
            case 26 -> {
                if (!event.isShiftClick()) return;
                obelisco.getOpciones().remove(opcion);
                ctx.obeliskManager.actualizar(obelisco);
                jugador.sendMessage(ChatColor.YELLOW + "Jefe quitado del obelisco.");
                new ObeliscoJefesGuiHolder(ctx, jugador, obelisco).abrir();
            }
            default -> { }
        }
    }

    @Override
    public void manejarArrastre(InventoryDragEvent event) { event.setCancelled(true); }

    public void abrir() {
        jugador.openInventory(inventario);
    }
}
