package com.bossforge.gui;

import com.bossforge.BossForgeContext;
import com.bossforge.habilidades.HabilidadCatalogoEntry;
import com.bossforge.model.BossTemplate;
import com.bossforge.model.HabilidadBossInstancia;
import com.bossforge.util.MenuItemBuilder;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Optional;

public class HabilidadSeleccionGuiHolder implements BossForgeGuiHolder {
    private final BossForgeContext ctx;
    private final Player jugador;
    private final BossTemplate boss;
    private final Inventory inventario;
    private final List<HabilidadCatalogoEntry> catalogo;

    public HabilidadSeleccionGuiHolder(BossForgeContext ctx, Player jugador, BossTemplate boss) {
        this.ctx = ctx;
        this.jugador = jugador;
        this.boss = boss;
        this.catalogo = ctx.habilidadRegistry.todas();
        this.inventario = Bukkit.createInventory(this, 54, ChatColor.DARK_RED + "Habilidades: " + ChatColor.RESET + boss.getNombre());
        construir();
    }

    @Override
    public Inventory getInventory() { return inventario; }

    private void construir() {
        inventario.clear();
        for (int i = 0; i < catalogo.size() && i < 45; i++) {
            inventario.setItem(i, icono(catalogo.get(i)));
        }
        inventario.setItem(49, MenuItemBuilder.de(Material.ARROW).nombre(ChatColor.YELLOW + "« Volver").build());
    }

    private Optional<HabilidadBossInstancia> instanciaDe(String habilidadId) {
        return boss.getHabilidades().stream().filter(h -> h.getHabilidadId().equals(habilidadId)).findFirst();
    }

    private ItemStack icono(HabilidadCatalogoEntry entrada) {
        boolean activa = instanciaDe(entrada.id()).isPresent();
        ItemStack item = MenuItemBuilder.de(activa ? Material.GLOWSTONE_DUST : Material.GUNPOWDER)
            .nombre((activa ? ChatColor.GREEN : ChatColor.GRAY) + entrada.nombre())
            .lore(
                ChatColor.GRAY + entrada.descripcion(),
                "",
                activa ? ChatColor.YELLOW + "Clic: quitar / Shift+clic: configurar" : ChatColor.YELLOW + "Clic para activar"
            ).build();
        if (activa) {
            ItemMeta meta = item.getItemMeta();
            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public void manejarClic(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() != inventario) return;
        int slot = event.getSlot();

        if (slot == 49) { new BossEditorGuiHolder(ctx, jugador, boss).abrir(); return; }
        if (slot >= catalogo.size() || slot >= 45) return;

        HabilidadCatalogoEntry entrada = catalogo.get(slot);
        Optional<HabilidadBossInstancia> existente = instanciaDe(entrada.id());
        boolean quiereConfigurar = event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT;

        if (existente.isPresent() && quiereConfigurar) {
            new HabilidadConfigGuiHolder(ctx, jugador, boss, entrada, existente.get()).abrir();
            return;
        }

        if (existente.isPresent()) {
            boss.getHabilidades().remove(existente.get());
        } else {
            boss.getHabilidades().add(new HabilidadBossInstancia(entrada.id()));
        }
        ctx.bossTemplateManager.actualizar(boss);
        construir();
        jugador.openInventory(inventario);
    }

    public void abrir() {
        jugador.openInventory(inventario);
    }
}
