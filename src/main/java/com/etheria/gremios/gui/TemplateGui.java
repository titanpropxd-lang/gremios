package com.etheria.gremios.gui;

import com.etheria.gremios.GremiosPlugin;
import com.etheria.gremios.model.Territory;
import com.etheria.gremios.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Menú de plantillas de territorio (solo admins): guardar el territorio donde estás y pegar/borrar plantillas. */
public class TemplateGui implements Listener {
    private static final class Holder implements InventoryHolder {
        Inventory inv;
        String pending; // "del:nombre"
        final Map<Integer, String> at = new HashMap<>();

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final GremiosPlugin plugin;

    public TemplateGui(GremiosPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player p) {
        if (!p.hasPermission("gremios.admin")) {
            Msg.to(p, "§cSin permiso.");
            return;
        }
        Holder h = new Holder();
        h.inv = Bukkit.createInventory(h, 54, "§8📐 §6Plantillas de territorio");
        draw(h, p);
        p.openInventory(h.inv);
    }

    private ItemStack item(Material m, String name, List<String> lore, boolean glow) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        if (glow) meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
        it.setItemMeta(meta);
        return it;
    }

    private void draw(Holder h, Player p) {
        h.inv.clear();
        h.at.clear();
        ItemStack f = item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of(), false);
        for (int i = 0; i < 54; i++) h.inv.setItem(i, f);
        List<String> names = plugin.templates.list();
        for (int i = 0; i < names.size() && i < 36; i++) {
            String n = names.get(i);
            YamlConfiguration y = plugin.templates.info(n);
            List<?> tam = y.getList("tam");
            List<String> lore = new ArrayList<>();
            if (tam != null && tam.size() >= 3) lore.add("§7Tamaño: §f" + tam.get(0) + "x" + tam.get(1) + "x" + tam.get(2));
            int npcs = y.getConfigurationSection("npcs") == null ? 0 : y.getConfigurationSection("npcs").getKeys(false).size();
            int gates = y.getConfigurationSection("puertas") == null ? 0 : y.getConfigurationSection("puertas").getKeys(false).size();
            lore.add("§7NPC: §f" + npcs + " §8· §7Puertas: §f" + gates + (y.getList("bandera") != null ? " §8· §7Bandera: §fsí" : ""));
            lore.add("§7Origen: §f" + y.getString("origen", "?") + " §8· §7" + new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date(y.getLong("creada", 0))));
            lore.add("");
            lore.add("§eClic izquierdo: §7pegar aquí (tu posición = el ancla)");
            lore.add(("del:" + n).equals(h.pending) ? "§c§lClic derecho de nuevo para BORRAR" : "§7Clic derecho: borrar");
            h.inv.setItem(9 + i, item(Material.FILLED_MAP, "§6" + n, lore, ("del:" + n).equals(h.pending)));
            h.at.put(9 + i, n);
        }
        Territory here = plugin.territories.at(p.getLocation());
        List<String> lore = new ArrayList<>();
        if (here == null) lore.add("§cNo estás dentro de ningún territorio.");
        else {
            lore.add("§7Territorio: §f" + here.name + " §8(" + here.id + ")");
            lore.add("§7Guarda bloques, NPC, puertas, bandera,");
            lore.add("§7spawn, núcleo y precios. Cofres: vacíos.");
            lore.add("§7Tu posición actual queda como ancla.");
            lore.add("");
            lore.add("§eClic: §7elegir nombre y guardar");
        }
        h.inv.setItem(4, item(Material.WRITABLE_BOOK, "§a§lGuardar este territorio", lore, here != null));
        h.inv.setItem(49, item(Material.BARRIER, "§cCerrar", List.of(), false));
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != h.inv) return;
        if (!p.hasPermission("gremios.admin")) {
            p.closeInventory();
            return;
        }
        int slot = e.getRawSlot();
        if (slot == 49) {
            p.closeInventory();
            return;
        }
        if (slot == 4) {
            Territory here = plugin.territories.at(p.getLocation());
            if (here == null) {
                Msg.to(p, "§cColócate dentro del territorio que quieres guardar.");
                return;
            }
            final Territory t = here;
            plugin.prompts.ask(p, "§6Nombre de la plantilla (a-z, 0-9, _ o -):", name -> {
                Msg.to(p, "§7Guardando plantilla... (progreso en la barra de acción)");
                plugin.templates.save(t, name, p, err -> Msg.to(p, err == null ? "§aPlantilla §f" + name.toLowerCase() + " §aguardada." : "§c" + err));
            });
            return;
        }
        String n = h.at.get(slot);
        if (n == null) return;
        if (e.isRightClick()) {
            if (("del:" + n).equals(h.pending)) {
                plugin.templates.delete(n);
                h.pending = null;
                Msg.to(p, "§7Plantilla §f" + n + " §7borrada.");
            } else h.pending = "del:" + n;
            draw(h, p);
            return;
        }
        final org.bukkit.Location where = p.getLocation().clone();
        plugin.prompts.ask(p, "§6Id del territorio nuevo (a-z, 0-9, _ o -):", id -> {
            Msg.to(p, "§7Pegando plantilla §f" + n + "§7... (progreso en la barra de acción)");
            plugin.templates.paste(n, id.toLowerCase(), null, where, err -> Msg.to(p, err == null
                    ? "§aTerritorio §f" + id.toLowerCase() + " §acreado desde la plantilla. Ya se puede comprar."
                    : "§c" + err));
        });
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
