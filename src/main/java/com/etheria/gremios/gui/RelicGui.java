package com.etheria.gremios.gui;

import com.etheria.gremios.GremiosPlugin;
import com.etheria.gremios.manager.RelicManager;
import com.etheria.gremios.manager.RelicManager.Relic;
import com.etheria.gremios.model.Guild;
import com.etheria.gremios.model.Rank;
import com.etheria.gremios.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.enchantments.Enchantment;

import java.util.ArrayList;
import java.util.List;

/** Menú de reliquias (/gremio reliquias). */
public class RelicGui implements Listener {
    private static final int[] GRID = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private static final class Holder implements InventoryHolder {
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final GremiosPlugin plugin;

    public RelicGui(GremiosPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player p) {
        if (!plugin.relics.enabled()) {
            Msg.to(p, "§cLas reliquias están desactivadas.");
            return;
        }
        Holder h = new Holder();
        h.inv = Bukkit.createInventory(h, 54, "§8✦ §dReliquias");
        draw(h, p);
        p.openInventory(h.inv);
    }

    private ItemStack item(Material m, String name, List<String> lore, boolean glow) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        if (glow) meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        it.setItemMeta(meta);
        return it;
    }

    private void draw(Holder h, Player p) {
        h.inv.clear();
        Guild g = plugin.guilds.of(p);
        int i = 0;
        for (Relic r : plugin.relics.all()) {
            if (i >= GRID.length) break;
            List<String> lore = new ArrayList<>();
            if (!r.desc.isEmpty()) lore.add("§7§o" + r.desc);
            lore.add("");
            lore.addAll(RelicManager.effectLines(r));
            lore.add("");
            boolean mine = g != null && g.id.equals(r.holder);
            if (r.holder == null) {
                lore.add("§a● LIBRE §7— reclámala con clic derecho en su pedestal");
                if (r.placed && plugin.getConfig().getBoolean("reliquias.mostrar-coordenadas-libres", true))
                    lore.add("§7Ubicación: §f" + r.world + " " + (int) r.x + ", " + (int) r.y + ", " + (int) r.z);
            } else {
                Guild hg = plugin.guilds.byId(r.holder);
                lore.add((mine ? "§6★ Tu gremio la custodia" : "§cCustodiada por §f" + (hg == null ? "?" : hg.name)));
                if (!mine) lore.add("§8Se le puede robar ganando un asedio.");
            }
            h.inv.setItem(GRID[i++], item(r.icon, "§d✦ §f" + r.name, lore, mine));
        }
        if (i == 0) h.inv.setItem(22, item(Material.BARRIER, "§7No hay reliquias configuradas", List.of(), false));
        List<String> info = new ArrayList<>();
        info.add("§7Objetos únicos con efectos pasivos para el gremio.");
        info.add("§7Se custodian en el §faltar §7de tu territorio.");
        info.add("§7Si pierdes un asedio, el atacante puede robarlas.");
        info.add("");
        if (g != null) {
            info.add("§7Tu gremio: §f" + plugin.relics.countHeld(g) + "§7/§f" + plugin.relics.maxPerGuild());
            info.add("§7Altar: " + (plugin.relics.altarOf(g) == null ? "§8(automático: spawn del territorio)" : "§afijado"));
            if (g.rankOf(p.getUniqueId()) == Rank.LIDER) info.add("§eClic: §ffijar el altar donde estás");
        }
        h.inv.setItem(49, item(Material.NETHER_STAR, "§d§lReliquias", info, true));
        h.inv.setItem(53, item(Material.BARRIER, "§cCerrar", List.of(), false));
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != h.inv) return;
        int s = e.getRawSlot();
        if (s == 53) p.closeInventory();
        else if (s == 49) {
            Guild g = plugin.guilds.of(p);
            if (g != null && g.rankOf(p.getUniqueId()) == Rank.LIDER) {
                String err = plugin.relics.setAltar(p, g);
                Msg.to(p, err == null ? "§dAltar fijado aquí." : "§c" + err);
                draw(h, p);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
