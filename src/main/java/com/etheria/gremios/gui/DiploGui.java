package com.etheria.gremios.gui;

import com.etheria.gremios.GremiosPlugin;
import com.etheria.gremios.manager.DiplomacyManager;
import com.etheria.gremios.model.Guild;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Menú de diplomacia: guerras, treguas, vasallaje y propuestas. */
public class DiploGui implements Listener {
    private enum Mode {MAIN, WAR, VASSAL, PROTECT}

    private static final class Holder implements InventoryHolder {
        Inventory inv;
        Mode mode = Mode.MAIN;
        int page;
        String pending;
        final Map<Integer, String> action = new HashMap<>();

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final GremiosPlugin plugin;

    public DiploGui(GremiosPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player p) {
        Guild g = plugin.guilds.of(p);
        if (g == null) {
            Msg.to(p, "§cNecesitas un gremio para usar la diplomacia.");
            return;
        }
        if (!plugin.diplo.enabled()) {
            Msg.to(p, "§cLa diplomacia está desactivada.");
            return;
        }
        Holder h = new Holder();
        h.inv = Bukkit.createInventory(h, 54, "§8⚔ §cDiplomacia §8· §7" + g.name);
        draw(h, g);
        p.openInventory(h.inv);
    }

    private ItemStack item(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        it.setItemMeta(meta);
        return it;
    }

    private void draw(Holder h, Guild g) {
        h.inv.clear();
        h.action.clear();
        if (h.mode == Mode.MAIN) drawMain(h, g);
        else drawPick(h, g);
        ItemStack f = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < 54; i++) if (h.inv.getItem(i) == null) h.inv.setItem(i, f);
    }

    private String confirm(Holder h, String key, String normal) {
        return key.equals(h.pending) ? "§c§lClic de nuevo para CONFIRMAR" : normal;
    }

    private void drawMain(Holder h, Guild g) {
        var d = plugin.diplo;
        long now = System.currentTimeMillis();
        List<String> info = new ArrayList<>();
        info.add("§7Guerras: §c" + d.warsOf(g).size() + "§7/§f" + d.maxWars(g));
        Guild lord = d.lordOf(g);
        info.add(lord != null ? "§7Señor: §6" + lord.name : "§7Vasallos: §6" + d.vassalsOf(g).size() + "§7/§f" + d.maxVassals(g));
        info.add("");
        info.add("§7Un §6Mariscal §7o superior decide.");
        h.inv.setItem(4, item(Material.WHITE_BANNER, "§6§l" + g.name, info));
        // guerras
        int slot = 9;
        for (var w : d.warsOf(g)) {
            if (slot > 17) break;
            Guild o = plugin.guilds.byId(w.other(g));
            if (o == null) continue;
            List<String> l = new ArrayList<>();
            l.add("§7Puntos: §a" + w.score(g) + " §7vs §c" + w.score(o) + " §8(gana quien llegue a " + plugin.getConfig().getInt("diplomacia.puntos-victoria", 40) + ")");
            l.add("§7Termina en: §f" + Msg.dur(Math.max(0, w.end - now)));
            l.add("§7Motivo: §f" + w.reason);
            l.add("");
            boolean peaceFrom = plugin.diplo.proposalsFor(g).stream().anyMatch(x -> x.type.equals("PAZ") && x.from.equals(o.id));
            l.add(peaceFrom ? "§aClic: §7aceptar la paz que propone" : "§aClic: §7proponer la paz");
            l.add(confirm(h, "sur:" + o.id, "§cMayús+clic: §7rendirse (ellos ganan el botín)"));
            l.add(confirm(h, "surv:" + o.id, "§4Clic derecho: §7rendirse como §6vasallo"));
            h.inv.setItem(slot, item(Material.RED_BANNER, "§c⚔ Guerra con §6" + o.name, l));
            h.action.put(slot, "war:" + o.id);
            slot++;
        }
        // treguas
        slot = 18;
        for (var e : d.truces().entrySet()) {
            if (slot > 26 || !e.getKey().contains(g.id)) continue;
            String[] ids = e.getKey().split("~");
            String other = ids[0].equals(g.id) ? ids[1] : ids[0];
            Guild o = plugin.guilds.byId(other);
            if (o == null) continue;
            h.inv.setItem(slot++, item(Material.CLOCK, "§a✌ Tregua con §6" + o.name, List.of("§7Quedan §f" + Msg.dur(Math.max(0, e.getValue() - now)))));
        }
        // señor y vasallos
        slot = 27;
        if (lord != null) {
            var v = d.vassalData(g);
            h.inv.setItem(slot++, item(Material.GOLDEN_HELMET, "§6👑 Tu señor: §f" + lord.name, List.of(
                    "§7Tributo: §f" + plugin.getConfig().getInt("diplomacia.tributo-pct", 10) + "% del banco cada " + plugin.getConfig().getLong("diplomacia.tributo-horas", 24) + " h",
                    "§7Vasallo desde hace §f" + Msg.dur(now - v.since), "",
                    confirm(h, "ind:" + lord.id, "§cClic: §7declarar independencia (guerra contra tu señor)"))));
            h.action.put(slot - 1, "ind:" + lord.id);
        }
        for (Guild v : d.vassalsOf(g)) {
            if (slot > 35) break;
            h.inv.setItem(slot, item(Material.LEATHER_HELMET, "§6Vasallo: §f" + v.name, List.of("§7Os paga tributo.", "", confirm(h, "rel:" + v.id, "§cClic: §7liberar al vasallo"))));
            h.action.put(slot, "rel:" + v.id);
            slot++;
        }
        // propuestas recibidas
        slot = 36;
        for (var pr : d.proposalsFor(g)) {
            if (slot > 44) break;
            Guild from = plugin.guilds.byId(pr.from);
            if (from == null) continue;
            String what = switch (pr.type) {
                case "PAZ" -> "§aPropone la paz";
                case "VASALLAJE" -> "§6Os ofrece ser su vasallo (protección a cambio de tributo)";
                default -> "§6Pide ser vuestro vasallo";
            };
            h.inv.setItem(slot, item(Material.PAPER, "§e✉ §f" + from.name, List.of(what, "", "§aClic: §7aceptar", "§cClic derecho: §7rechazar")));
            h.action.put(slot, "prop:" + pr.type + ":" + pr.from);
            slot++;
        }
        h.inv.setItem(45, item(Material.NETHERITE_SWORD, "§c§lDeclarar guerra", List.of("§7Elige un gremio.", "§7Coste: §e" + plugin.currency.fmt(plugin.getConfig().getLong("diplomacia.costo-guerra", 24)))));
        h.inv.setItem(46, item(Material.GOLDEN_HELMET, "§6Ofrecer vasallaje", List.of("§7Ofrece a otro gremio ponerse bajo tu protección.")));
        h.inv.setItem(47, item(Material.SHIELD, "§bPedir protección", List.of("§7Pide a un gremio mayor que te acepte como vasallo.")));
        h.inv.setItem(49, item(Material.BARRIER, "§cCerrar", List.of()));
    }

    private void drawPick(Holder h, Guild g) {
        List<Guild> list = new ArrayList<>();
        for (Guild o : plugin.guilds.all()) if (o != g) list.add(o);
        list.sort((x, y) -> Integer.compare(y.members.size(), x.members.size()));
        int per = 45, pages = Math.max(1, (list.size() + per - 1) / per);
        if (h.page >= pages) h.page = pages - 1;
        for (int i = 0; i < per; i++) {
            int idx = h.page * per + i;
            if (idx >= list.size()) break;
            Guild o = list.get(idx);
            List<String> l = new ArrayList<>();
            l.add("§7Miembros: §f" + o.members.size() + " §8· §7Nivel: §f" + o.level);
            if (plugin.diplo.atWar(g, o)) l.add("§cYa estáis en guerra");
            long tr = plugin.diplo.truceLeft(g, o);
            if (tr > 0) l.add("§aTregua: " + Msg.dur(tr));
            l.add("");
            String key = h.mode.name() + ":" + o.id;
            l.add(confirm(h, key, "§eClic: §7elegir"));
            h.inv.setItem(i, item(Material.PLAYER_HEAD, "§f" + o.name, l));
            h.action.put(i, key);
        }
        h.inv.setItem(45, item(Material.ARROW, "§7◀ Volver", List.of()));
        h.inv.setItem(48, item(Material.ARROW, "§7Anterior", List.of("§8Página " + (h.page + 1) + "/" + pages)));
        h.inv.setItem(50, item(Material.ARROW, "§7Siguiente", List.of("§8Página " + (h.page + 1) + "/" + pages)));
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != h.inv) return;
        Guild g = plugin.guilds.of(p);
        if (g == null) {
            p.closeInventory();
            return;
        }
        int slot = e.getRawSlot();
        var d = plugin.diplo;
        if (h.mode == Mode.MAIN) {
            switch (slot) {
                case 45 -> {
                    h.mode = Mode.WAR;
                    h.page = 0;
                    h.pending = null;
                }
                case 46 -> {
                    h.mode = Mode.VASSAL;
                    h.page = 0;
                    h.pending = null;
                }
                case 47 -> {
                    h.mode = Mode.PROTECT;
                    h.page = 0;
                    h.pending = null;
                }
                case 49 -> {
                    p.closeInventory();
                    return;
                }
                default -> mainAction(h, g, p, slot, e);
            }
        } else {
            if (slot == 45) {
                h.mode = Mode.MAIN;
                h.pending = null;
            } else if (slot == 48) h.page = Math.max(0, h.page - 1);
            else if (slot == 50) h.page++;
            else {
                String act = h.action.get(slot);
                if (act != null) {
                    if (act.equals(h.pending)) {
                        Guild o = plugin.guilds.byId(act.substring(act.indexOf(':') + 1));
                        String err = o == null ? "Ese gremio ya no existe." : switch (h.mode) {
                            case WAR -> d.declareWar(p, g, o);
                            case VASSAL -> d.offerVassal(p, g, o);
                            default -> d.askProtection(p, g, o);
                        };
                        if (err != null) Msg.to(p, "§c" + err);
                        else Msg.to(p, "§aHecho.");
                        h.mode = Mode.MAIN;
                        h.pending = null;
                    } else h.pending = act;
                }
            }
        }
        draw(h, g);
    }

    private void mainAction(Holder h, Guild g, Player p, int slot, InventoryClickEvent e) {
        String act = h.action.get(slot);
        if (act == null) return;
        var d = plugin.diplo;
        String[] parts = act.split(":");
        String err = null;
        switch (parts[0]) {
            case "war" -> {
                Guild o = plugin.guilds.byId(parts[1]);
                if (o == null) return;
                if (e.isShiftClick()) {
                    if (("sur:" + o.id).equals(h.pending)) {
                        err = d.surrender(p, g, o, false);
                        h.pending = null;
                    } else {
                        h.pending = "sur:" + o.id;
                        return;
                    }
                } else if (e.isRightClick()) {
                    if (("surv:" + o.id).equals(h.pending)) {
                        err = d.surrender(p, g, o, true);
                        h.pending = null;
                    } else {
                        h.pending = "surv:" + o.id;
                        return;
                    }
                } else {
                    err = d.peace(p, g, o);
                    if (err == null) Msg.to(p, "§aPaz gestionada.");
                }
            }
            case "ind" -> {
                if (act.equals(h.pending)) {
                    err = d.independence(p, g);
                    h.pending = null;
                } else {
                    h.pending = act;
                    return;
                }
            }
            case "rel" -> {
                if (act.equals(h.pending)) {
                    Guild v = plugin.guilds.byId(parts[1]);
                    err = v == null ? "Ese gremio ya no existe." : d.release(p, g, v);
                    h.pending = null;
                } else {
                    h.pending = act;
                    return;
                }
            }
            case "prop" -> {
                DiplomacyManager.Proposal pr = null;
                for (var x : d.proposalsFor(g)) if (x.type.equals(parts[1]) && x.from.equals(parts[2])) pr = x;
                if (pr == null) return;
                err = e.isRightClick() ? d.reject(p, g, pr) : d.accept(p, g, pr);
            }
            default -> {
                return;
            }
        }
        if (err != null) Msg.to(p, "§c" + err);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
