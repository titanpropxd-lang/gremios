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

import java.util.List;

/** Los jefes que ofrece UN obelisco. Clic en uno = configurar sus requisitos y su loot. */
public class ObeliscoJefesGuiHolder implements BossForgeGuiHolder {
    private final BossForgeContext ctx;
    private final Player jugador;
    private final Obelisco obelisco;
    private final Inventory inventario;

    public ObeliscoJefesGuiHolder(BossForgeContext ctx, Player jugador, Obelisco obelisco) {
        this.ctx = ctx;
        this.jugador = jugador;
        this.obelisco = obelisco;
        this.inventario = Bukkit.createInventory(this, 54, ChatColor.DARK_RED + "Jefes: " + ChatColor.RESET + obelisco.getNombre());
        construir();
    }

    @Override
    public Inventory getInventory() { return inventario; }

    private void construir() {
        inventario.clear();
        List<OpcionJefe> opciones = obelisco.getOpciones();
        for (int i = 0; i < opciones.size() && i < 45; i++) {
            OpcionJefe opcion = opciones.get(i);
            BossTemplate boss = ctx.bossTemplateManager.obtener(opcion.getBossTemplateId()).orElse(null);
            inventario.setItem(i, MenuItemBuilder.de(boss == null ? Material.BARRIER : boss.esMythic() ? Material.DRAGON_HEAD : Material.SPAWNER)
                .nombre((boss == null ? ChatColor.RED : ChatColor.LIGHT_PURPLE) + OpcionJefeEditorGuiHolder.nombreDe(ctx, opcion))
                .lore(
                    boss != null && boss.esMythic() ? ChatColor.GRAY + "MythicMobs: " + ChatColor.WHITE + boss.getMythicMobId()
                        : boss != null ? ChatColor.GRAY + "BossForge: " + ChatColor.WHITE + boss.getTipoBase() : ChatColor.RED + "Jefe eliminado",
                    ChatColor.GRAY + "Requisitos: " + ChatColor.WHITE + opcion.getRequisitos().size() + " item(s)",
                    ChatColor.GRAY + "Loot: " + ChatColor.WHITE + opcion.getLoot().size() + " item(s)",
                    opcion.estaCompleta() ? ChatColor.GREEN + "Lista para invocarse" : ChatColor.RED + "Incompleta (faltan requisitos)",
                    "",
                    ChatColor.YELLOW + "Clic para configurar requisitos y loot").build());
        }

        inventario.setItem(45, MenuItemBuilder.de(Material.DRAGON_HEAD)
            .nombre(ChatColor.LIGHT_PURPLE + "+ Anadir jefe de MythicMobs")
            .lore(ctx.mythic.disponible()
                ? new String[]{ChatColor.GRAY + "Elige uno de tus mobs de MythicMobs", ChatColor.GRAY + "(con sus habilidades y config)."}
                : new String[]{ChatColor.RED + "MythicMobs no esta instalado/activo."}).build());
        inventario.setItem(46, MenuItemBuilder.de(Material.SPAWNER)
            .nombre(ChatColor.RED + "+ Anadir jefe del catalogo BossForge")
            .lore(ChatColor.GRAY + "Jefes creados con el editor del plugin.").build());
        inventario.setItem(49, MenuItemBuilder.de(Material.ARROW).nombre(ChatColor.YELLOW + "« Volver").build());
        inventario.setItem(53, MenuItemBuilder.de(Material.BOOK)
            .nombre(ChatColor.AQUA + "Como funciona")
            .lore(ChatColor.GRAY + "El jugador elige que jefe invocar",
                  ChatColor.GRAY + "y cada uno pide SUS requisitos",
                  ChatColor.GRAY + "y da SU loot (como en ARK).").build());
    }

    @Override
    public void manejarClic(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() != inventario) return;
        int slot = event.getSlot();

        if (slot < 45) {
            if (slot < obelisco.getOpciones().size()) {
                new OpcionJefeEditorGuiHolder(ctx, jugador, obelisco, obelisco.getOpciones().get(slot)).abrir();
            }
            return;
        }
        switch (slot) {
            case 45 -> {
                if (!ctx.mythic.disponible()) {
                    jugador.sendMessage(ChatColor.RED + "MythicMobs no esta instalado o activo en el servidor.");
                    return;
                }
                new MythicMobSeleccionGuiHolder(ctx, jugador, obelisco, 0).abrir();
            }
            case 46 -> new BossCatalogoGuiHolder(ctx, jugador, obelisco).abrir();
            case 49 -> new ObeliscoConfigGuiHolder(ctx, jugador, obelisco).abrir();
            default -> { }
        }
    }

    @Override
    public void manejarArrastre(InventoryDragEvent event) { event.setCancelled(true); }

    public void abrir() {
        jugador.openInventory(inventario);
    }
}
