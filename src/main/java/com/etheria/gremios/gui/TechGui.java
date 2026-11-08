package com.etheria.gremios.gui;

import com.etheria.gremios.GremiosPlugin;
import com.etheria.gremios.model.Guild;
import com.etheria.gremios.tech.Tech;
import com.etheria.gremios.tech.TechTree;
import com.etheria.gremios.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Árbol de tecnologías como grafo de nodos conectados por líneas (estilo "constelación"):
 * fila superior = pestañas de ramas; zona central = ventana desplazable sobre el grafo;
 * columna derecha = info y botones de desplazamiento.
 */
public class TechGui implements Listener {
    private static final int VIEW_W = 8, VIEW_H = 5; // celdas visibles (filas 1..5, columnas 0..7)

    /** Posición de los nodos de una rama en una cuadrícula de celdas (nodo = celdas pares, línea = impares). */
    private static final class Layout {
        final Map<String, int[]> pos = new HashMap<>(); // id -> {cx, cy}
        final List<String[]> edges = new ArrayList<>();  // {desde, hasta}
        int width = 1, height = 1;
    }

    private final Map<Tech.Branch, Layout> layouts = new HashMap<>();

    private static final class Holder implements InventoryHolder {
        Inventory inv;
        Tech.Branch branch;
        int ox, oy;         // esquina superior izquierda de la ventana (en celdas)
        String pending;     // confirmación en curso ("go:id" / "no:id")
        boolean pack;       // true = fondo del resourcepack con casillas fijas (sin desplazamiento)
        final Map<Integer, String> techAt = new HashMap<>();
        final Map<Integer, Tech.Branch> tabAt = new HashMap<>();

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final GremiosPlugin plugin;
    /** rama (minúsculas) -> id de tecnología -> {columna, fila}; sale de tech-layout.yml (tools/gen_tech.py). */
    private Map<String, Map<String, int[]>> packSlots;

    private Map<String, int[]> packSlotsOf(Tech.Branch b) {
        if (packSlots == null) {
            packSlots = new HashMap<>();
            try (java.io.InputStream in = plugin.getResource("tech-layout.yml")) {
                if (in != null) {
                    var y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                            new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
                    var root = y.getConfigurationSection("branches");
                    if (root != null) for (String br : root.getKeys(false)) {
                        Map<String, int[]> m = new HashMap<>();
                        var ns = root.getConfigurationSection(br + ".nodes");
                        if (ns != null) for (String id : ns.getKeys(false)) {
                            List<Integer> l = ns.getIntegerList(id);
                            if (l.size() >= 2) m.put(id, new int[]{l.get(0), l.get(1)});
                        }
                        packSlots.put(br, m);
                    }
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("No se pudo leer tech-layout.yml: " + ex.getMessage());
            }
        }
        return packSlots.get(b.name().toLowerCase());
    }

    private boolean packMode(Player p, Tech.Branch b) {
        return plugin.usePack(p) && packSlotsOf(b) != null && plugin.layouts.all().containsKey("tech_" + b.name().toLowerCase());
    }

    public TechGui(GremiosPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player p) {
        open(p, null);
    }

    public void open(Player p, Tech.Branch b) {
        if (!plugin.tech.enabled()) {
            Msg.to(p, "§cLas tecnologías están desactivadas.");
            return;
        }
        Guild g = plugin.guilds.of(p);
        if (g == null) {
            Msg.to(p, "§cNecesitas un gremio para ver las tecnologías.");
            return;
        }
        if (b == null || plugin.tech.branch(b).isEmpty()) {
            b = null;
            for (Tech.Branch x : Tech.Branch.values()) {
                if (!plugin.tech.branch(x).isEmpty()) {
                    b = x;
                    break;
                }
            }
            if (b == null) {
                Msg.to(p, "§cNo hay tecnologías disponibles.");
                return;
            }
        }
        Holder h = new Holder();
        h.pack = packMode(p, b);
        if (h.pack) h.inv = Bukkit.createInventory(h, 54, plugin.layouts.title(plugin.layouts.get("tech_" + b.name().toLowerCase()), true));
        else h.inv = Bukkit.createInventory(h, 54, "§8🔬 §bTecnologías §8· §7" + g.name);
        switchBranch(h, g, b);
        p.openInventory(h.inv);
    }

    private void switchBranch(Holder h, Guild g, Tech.Branch b) {
        h.branch = b;
        h.pending = null;
        Layout l = layout(b);
        // centrar la ventana en la primera tecnología investigable (o en la raíz)
        int fx = 0, fy = 0;
        for (Tech t : plugin.tech.branch(b)) {
            int[] ps = l.pos.get(t.id);
            if (ps == null) continue;
            if (!plugin.tech.has(g, t.id) && plugin.tech.blocked(g, t) == null) {
                fx = ps[0];
                fy = ps[1];
                break;
            }
        }
        h.ox = fx - VIEW_W / 2;
        h.oy = fy - VIEW_H / 2;
        clamp(h, l);
        draw(h, g);
    }

    private static void clamp(Holder h, Layout l) {
        h.ox = Math.max(0, Math.min(Math.max(0, l.width - VIEW_W), h.ox));
        h.oy = Math.max(0, Math.min(Math.max(0, l.height - VIEW_H), h.oy));
    }

    // ───────────── cálculo del grafo ─────────────

    private Layout layout(Tech.Branch b) {
        Layout cached = layouts.get(b);
        if (cached != null) return cached;
        List<Tech> list = plugin.tech.branch(b);
        Set<String> ids = new HashSet<>();
        for (Tech t : list) ids.add(t.id);
        Map<String, Integer> depth = new HashMap<>();
        for (Tech t : list) depthOf(t, ids, depth, 0);
        List<Tech> order = new ArrayList<>(list);
        order.sort((a, c) -> Integer.compare(depth.get(a.id), depth.get(c.id)));
        Map<String, Integer> row = new HashMap<>();
        Map<Integer, Set<Integer>> used = new HashMap<>();
        for (Tech t : order) {
            int d = depth.get(t.id);
            double sum = 0;
            int n = 0;
            for (String r : t.reqs) {
                if (row.containsKey(r)) {
                    sum += row.get(r);
                    n++;
                }
            }
            int pref = n == 0 ? 0 : (int) Math.round(sum / n);
            Set<Integer> u = used.computeIfAbsent(d, k -> new HashSet<>());
            int r = pref;
            for (int k = 0; k < 40; k++) {
                int cand = pref + ((k % 2 == 0) ? k / 2 : -(k + 1) / 2);
                if (!u.contains(cand)) {
                    r = cand;
                    break;
                }
            }
            u.add(r);
            row.put(t.id, r);
        }
        int minRow = Integer.MAX_VALUE;
        for (int r : row.values()) minRow = Math.min(minRow, r);
        if (minRow == Integer.MAX_VALUE) minRow = 0;
        Layout l = new Layout();
        for (Tech t : list) {
            int cx = depth.get(t.id) * 2;
            int cy = (row.get(t.id) - minRow) * 2;
            l.pos.put(t.id, new int[]{cx, cy});
            l.width = Math.max(l.width, cx + 1);
            l.height = Math.max(l.height, cy + 1);
            for (String r : t.reqs) if (ids.contains(r)) l.edges.add(new String[]{r, t.id});
        }
        layouts.put(b, l);
        return l;
    }

    private int depthOf(Tech t, Set<String> ids, Map<String, Integer> memo, int guard) {
        Integer m = memo.get(t.id);
        if (m != null) return m;
        int d = 0;
        if (guard < 50) {
            for (String r : t.reqs) {
                if (!ids.contains(r)) continue;
                Tech rt = TechTree.ALL.get(r);
                if (rt != null) d = Math.max(d, 1 + depthOf(rt, ids, memo, guard + 1));
            }
        }
        memo.put(t.id, d);
        return d;
    }

    // ───────────── dibujo ─────────────

    private ItemStack item(Material m, String name, List<String> lore, boolean glow, int amount) {
        ItemStack it = new ItemStack(m, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        if (glow) meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        it.setItemMeta(meta);
        return it;
    }

    private static String bar(double frac) {
        int f = (int) Math.round(Math.max(0, Math.min(1, frac)) * 12);
        return "§a" + "█".repeat(f) + "§8" + "░".repeat(12 - f);
    }

    private static int slotOf(int vx, int vy) {
        return (1 + vy) * 9 + vx;
    }

    /** Estado de un nodo: 2 = investigada, 1 = disponible/en curso, 0 = bloqueada. */
    private int state(Guild g, Tech t) {
        var tm = plugin.tech;
        if (tm.has(g, t.id)) return 2;
        if (tm.doing(g).containsKey(t.id)) return 1;
        for (String r : t.reqs) if (!tm.has(g, r)) return 0;
        if (g.level < tm.minGuildLevel(t)) return 0;
        return 1;
    }

    /** Versión con fondo del resourcepack: cada tecnología en su casilla fija, sin desplazamiento. */
    private void drawPack(Holder h, Guild g) {
        var tm = plugin.tech;
        h.inv.clear();
        h.techAt.clear();
        h.tabAt.clear();
        for (Tech.Branch b : Tech.Branch.values()) {
            List<Tech> list = tm.branch(b);
            if (list.isEmpty()) continue;
            int done = 0;
            for (Tech t : list) if (tm.has(g, t.id)) done++;
            boolean cur = b == h.branch;
            List<String> lore = new ArrayList<>();
            lore.add("§7Investigadas: §a" + done + "§7/§f" + list.size());
            lore.add(bar(done / (double) list.size()));
            lore.add(cur ? "§8Rama abierta" : "§eClic: §7abrir la rama");
            h.inv.setItem(b.ordinal(), item(b.icon, (cur ? "§l▶ " : "") + b.color + b.display, lore, cur, 1));
            h.tabAt.put(b.ordinal(), b);
        }
        Map<String, int[]> slots = packSlotsOf(h.branch);
        long now = System.currentTimeMillis();
        for (Tech t : tm.branch(h.branch)) {
            int[] ps = slots == null ? null : slots.get(t.id);
            if (ps == null) continue;
            int slot = ps[1] * 9 + ps[0];
            h.inv.setItem(slot, nodeItem(h, g, t, now));
            h.techAt.put(slot, t.id);
        }
        List<String> info = new ArrayList<>();
        info.add("§7Banco del gremio: §e" + plugin.currency.fmt(g.bank));
        info.add("§7Investigadas: §a" + tm.count(g) + "§7/§f" + tm.totalVisible());
        info.add("§7Investigaciones a la vez: §f" + tm.doing(g).size() + "§7/§f" + tm.slots(g));
        info.add("§7Nivel del gremio: §f" + g.level);
        h.inv.setItem(17, item(Material.KNOWLEDGE_BOOK, "§b§lPuntos de investigación", info, true, 1));
        List<String> cur = new ArrayList<>();
        long now2 = System.currentTimeMillis();
        for (var e : tm.doing(g).entrySet()) {
            Tech t = TechTree.ALL.get(e.getKey());
            if (t != null) cur.add("§e⏳ §f" + t.name + " §7" + Msg.dur(Math.max(0, e.getValue() - now2)));
        }
        if (cur.isEmpty()) cur.add("§7Nada en curso.");
        h.inv.setItem(26, item(Material.CLOCK, "§6§lEn investigación", cur, !tm.doing(g).isEmpty(), 1));
        h.inv.setItem(53, item(Material.ARROW, "§eVolver al menú del gremio", List.of(), false, 1));
    }

    private void draw(Holder h, Guild g) {
        if (h.pack) {
            drawPack(h, g);
            return;
        }
        var tm = plugin.tech;
        h.inv.clear();
        h.techAt.clear();
        h.tabAt.clear();
        Layout l = layout(h.branch);
        clamp(h, l);
        ItemStack dark = item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of(), false, 1);
        for (int i = 0; i < 54; i++) h.inv.setItem(i, dark);

        // pestañas de ramas (fila 0)
        int ti = 0;
        for (Tech.Branch b : Tech.Branch.values()) {
            List<Tech> list = tm.branch(b);
            if (list.isEmpty() || ti > 8) continue;
            int done = 0;
            for (Tech t : list) if (tm.has(g, t.id)) done++;
            boolean cur = b == h.branch;
            List<String> lore = new ArrayList<>();
            lore.add("§7Investigadas: §a" + done + "§7/§f" + list.size());
            lore.add(bar(done / (double) list.size()));
            lore.add(cur ? "§8Rama abierta" : "§eClic: §7abrir la rama");
            h.inv.setItem(ti, item(b.icon, (cur ? "§l▶ " : "") + b.color + b.display, lore, cur, 1));
            h.tabAt.put(ti, b);
            ti++;
        }

        // líneas: celdas libres entre nodos. Estado: 0 gris, 1 naranja (disponible), 2 verde (investigado)
        Map<Integer, Integer> lines = new HashMap<>();
        for (String[] e : l.edges) {
            int[] a = l.pos.get(e[0]);
            int[] c = l.pos.get(e[1]);
            Tech from = TechTree.ALL.get(e[0]);
            Tech to = TechTree.ALL.get(e[1]);
            if (a == null || c == null || from == null || to == null) continue;
            int st = !tm.has(g, from.id) ? 0 : (tm.has(g, to.id) ? 2 : 1);
            int x = a[0] + 1, y = a[1];
            // tramo vertical junto al origen y luego horizontal hasta el destino
            int dy = Integer.compare(c[1], a[1]);
            while (y != c[1]) {
                mark(lines, x, y, st);
                y += dy;
            }
            for (; x < c[0]; x++) mark(lines, x, c[1], st);
            mark(lines, x - 1, c[1], st);
        }
        Set<Long> nodeCells = new HashSet<>();
        for (int[] ps : l.pos.values()) nodeCells.add(((long) ps[0] << 32) | ps[1]);
        for (var e : lines.entrySet()) {
            int cx = e.getKey() >> 16, cy = e.getKey() & 0xFFFF;
            if (nodeCells.contains(((long) cx << 32) | cy)) continue;
            int vx = cx - h.ox, vy = cy - h.oy;
            if (vx < 0 || vx >= VIEW_W || vy < 0 || vy >= VIEW_H) continue;
            Material m = switch (e.getValue()) {
                case 2 -> Material.LIME_STAINED_GLASS_PANE;
                case 1 -> Material.ORANGE_STAINED_GLASS_PANE;
                default -> Material.GRAY_STAINED_GLASS_PANE;
            };
            h.inv.setItem(slotOf(vx, vy), item(m, " ", List.of(), false, 1));
        }

        // nodos
        long now = System.currentTimeMillis();
        for (Tech t : tm.branch(h.branch)) {
            int[] ps = l.pos.get(t.id);
            if (ps == null) continue;
            int vx = ps[0] - h.ox, vy = ps[1] - h.oy;
            if (vx < 0 || vx >= VIEW_W || vy < 0 || vy >= VIEW_H) continue;
            int slot = slotOf(vx, vy);
            h.inv.setItem(slot, nodeItem(h, g, t, now));
            h.techAt.put(slot, t.id);
        }

        // columna derecha: info y desplazamiento
        int done = tm.count(g);
        List<String> info = new ArrayList<>();
        info.add("§7Banco del gremio: §e" + plugin.currency.fmt(g.bank));
        info.add("§7Investigadas: §a" + done + "§7/§f" + tm.totalVisible());
        info.add("§7Investigaciones a la vez: §f" + tm.doing(g).size() + "§7/§f" + tm.slots(g));
        info.add("§7Nivel del gremio: §f" + g.level);
        info.add("");
        info.add("§a■ §7investigada  §6■ §7disponible  §8■ §7bloqueada");
        h.inv.setItem(17, item(Material.KNOWLEDGE_BOOK, "§b§lPuntos de investigación", info, true, 1));
        h.inv.setItem(26, item(Material.ARROW, "§e▲ Subir", List.of(), false, 1));
        h.inv.setItem(35, item(Material.ARROW, "§e▼ Bajar", List.of(), false, 1));
        h.inv.setItem(44, item(Material.SPECTRAL_ARROW, "§e◀ Izquierda", List.of(), false, 1));
        h.inv.setItem(53, item(Material.SPECTRAL_ARROW, "§e▶ Derecha", List.of(), false, 1));
    }

    private static void mark(Map<Integer, Integer> lines, int x, int y, int st) {
        lines.merge((x << 16) | y, st, Math::max);
    }

    private ItemStack nodeItem(Holder h, Guild g, Tech t, long now) {
        var tm = plugin.tech;
        List<String> lore = new ArrayList<>();
        lore.add("§7" + t.desc);
        lore.add("");
        for (String x : t.effectLines()) lore.add("§a▸ §f" + x);
        lore.add("");
        String name;
        boolean glow = false;
        if (tm.has(g, t.id)) {
            name = "§a✔ " + t.name;
            glow = true;
            lore.add("§aInvestigada");
        } else if (tm.doing(g).containsKey(t.id)) {
            name = "§e⏳ " + t.name;
            glow = true;
            long left = Math.max(0, tm.doing(g).get(t.id) - now);
            lore.add("§eEn investigación: §f" + Msg.dur(left));
            lore.add(("no:" + t.id).equals(h.pending) ? "§c§lClic derecho de nuevo para CANCELAR (50% devuelto)" : "§7Clic derecho: cancelar");
        } else {
            List<String> missing = new ArrayList<>();
            for (String r : t.reqs) if (!tm.has(g, r)) missing.add(TechTree.ALL.get(r).name);
            if (g.level < tm.minGuildLevel(t)) missing.add("gremio nivel " + tm.minGuildLevel(t));
            lore.add("§7Coste: §e" + plugin.currency.fmt(tm.cost(g, t)) + " §8· §7Tiempo: §f" + Msg.dur(tm.millis(g, t)));
            if (!missing.isEmpty()) {
                name = "§8🔒 " + t.name;
                lore.add("§cFalta: §7" + String.join(", ", missing));
            } else {
                String blocked = tm.blocked(g, t);
                name = (blocked == null ? "§6" : "§7") + t.name;
                if (blocked != null) lore.add("§c" + blocked);
                else lore.add(("go:" + t.id).equals(h.pending) ? "§a§lClic de nuevo para CONFIRMAR" : "§eClic: §7investigar");
                glow = blocked == null;
            }
        }
        if (h.pack && name.startsWith("§8🔒")) return item(Material.GRAY_STAINED_GLASS_PANE, name, lore, false, 1);
        return item(t.icon, name, lore, glow, 1);
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
        Tech.Branch tab = h.tabAt.get(slot);
        if (tab != null) {
            if (tab == h.branch) return;
            if (h.pack) Bukkit.getScheduler().runTask(plugin, () -> open(p, tab)); // cada rama tiene su propio fondo: se abre otro inventario
            else switchBranch(h, g, tab);
            return;
        }
        if (h.pack) {
            if (slot == 53) {
                plugin.guis.main(p);
                return;
            }
            if (!h.techAt.containsKey(slot)) return;
        }
        int step = e.isShiftClick() ? 2 : 1;
        switch (slot) {
            case 26 -> h.oy -= 2 * step;
            case 35 -> h.oy += 2 * step;
            case 44 -> h.ox -= 2 * step;
            case 53 -> h.ox += 2 * step;
            default -> step = 0;
        }
        if (step != 0) {
            draw(h, g);
            return;
        }
        String id = h.techAt.get(slot);
        if (id == null) return;
        Tech t = TechTree.ALL.get(id);
        if (t == null) return;
        if (e.isRightClick() && plugin.tech.doing(g).containsKey(id)) {
            if (("no:" + id).equals(h.pending)) {
                String err = plugin.tech.cancel(p, g, t);
                if (err != null) Msg.to(p, "§c" + err);
                h.pending = null;
            } else h.pending = "no:" + id;
        } else if (!plugin.tech.has(g, id) && !plugin.tech.doing(g).containsKey(id)) {
            if (("go:" + id).equals(h.pending)) {
                String err = plugin.tech.start(p, g, t);
                if (err != null) Msg.to(p, "§c" + err);
                h.pending = null;
            } else if (plugin.tech.blocked(g, t) == null) h.pending = "go:" + id;
            else {
                Msg.to(p, "§c" + plugin.tech.blocked(g, t));
                h.pending = null;
            }
        }
        draw(h, g);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
