package com.etheria.gremios.gui;

import com.etheria.gremios.GremiosPlugin;
import com.etheria.gremios.manager.TurretManager;
import com.etheria.gremios.model.EventDef;
import com.etheria.gremios.model.Guild;
import com.etheria.gremios.model.Rank;
import com.etheria.gremios.model.Territory;
import com.etheria.gremios.util.ItemBuilder;
import com.etheria.gremios.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Menús de bandera, torres de ballesta y eventos (admin). */
public class ExtraGuis {
    private final GremiosPlugin plugin;

    public ExtraGuis(GremiosPlugin plugin) {
        this.plugin = plugin;
    }

    private ItemStack it(Material m, String name, List<String> lore) {
        return new ItemBuilder(m).name(name).lore(lore).build();
    }

    private ItemStack it(Material m, String name, String... lore) {
        return new ItemBuilder(m).name(name).lore(lore).build();
    }

    private Menu menu(Player p, String layout, java.util.function.Consumer<Menu> r) {
        return new Menu(plugin, p, layout, r);
    }

    // ------------------------------------------------------------------ bandera

    public void banner(Player p) {
        menu(p, "bandera", m -> {
            Guild g = plugin.guilds.of(p);
            if (g == null) {
                p.closeInventory();
                return;
            }
            ItemStack cur = plugin.banners.of(g);
            if (cur != null) {
                var meta = cur.getItemMeta();
                meta.setDisplayName("§6Bandera de " + g.name);
                meta.setLore(List.of("§7Así se ve en tu territorio."));
                cur.setItemMeta(meta);
                m.cell(4, 0, cur, null);
            } else {
                m.cell(4, 0, it(Material.WHITE_BANNER, "§7Sin bandera", "§7Se usa una bandera blanca por defecto."), null);
            }
            m.button("usar", "§aUsar la bandera de mi mano", List.of("§7Sostén un estandarte (con tus diseños)", "§7y haz clic. Solo Mariscal o superior.", "",
                    "§8Créalo en un telar y se verá en el territorio."), e -> {
                Guild gg = plugin.guilds.of(p);
                Rank r = gg == null ? null : gg.rankOf(p.getUniqueId());
                if (r == null || !r.atLeast(Rank.MARISCAL)) {
                    Msg.to(p, "§cSolo Mariscal o superior puede cambiar la bandera.");
                    return;
                }
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (!plugin.banners.isBanner(hand)) {
                    Msg.to(p, "§cSostén un estandarte en la mano principal.");
                    return;
                }
                plugin.banners.set(gg, hand);
                Msg.to(p, "§aBandera del gremio actualizada.");
                m.refresh();
            });
            m.button("quitar", "§cQuitar bandera", List.of("§7Vuelve a la bandera blanca."), e -> {
                Guild gg = plugin.guilds.of(p);
                Rank r = gg == null ? null : gg.rankOf(p.getUniqueId());
                if (r == null || !r.atLeast(Rank.MARISCAL)) {
                    Msg.to(p, "§cSolo Mariscal o superior.");
                    return;
                }
                plugin.banners.remove(gg);
                m.refresh();
            });
            m.button("volver", "§7Volver", e -> plugin.guis.upgrades(p));
        }).open();
    }

    // ------------------------------------------------------------------ torres

    public void turrets(Player p, int page) {
        menu(p, "torres", m -> {
            Guild g = plugin.guilds.of(p);
            if (g == null) {
                p.closeInventory();
                return;
            }
            Territory t = plugin.territories.ownedBy(g);
            int cap = plugin.turrets.cap(g.territoryLevel);
            int have = t == null ? 0 : plugin.turrets.countIn(g, t);
            m.cell(4, 0, it(Material.CROSSBOW, "§6Torres de Ballesta", "§7Banco: §e" + plugin.currency.fmt(g.bank),
                    "§7Torres: §f" + have + "§7/§f" + cap, "§7Precio: §e" + plugin.currency.fmt(plugin.turrets.buyPrice()),
                    "", "§8Disparan solas a los atacantes", "§8durante un asedio."), null);
            List<TurretManager.Turret> list = plugin.turrets.of(g);
            m.paged(list, tu -> {
                Entity e = Bukkit.getEntity(tu.slime);
                List<String> l = new ArrayList<>();
                l.add("§7Nivel: §f" + tu.level + "§7/3");
                l.add("§7Vida: §c" + (int) tu.hp + "§7/" + (int) plugin.turrets.maxHp(tu.level));
                if (e != null) l.add("§7Posición: §f" + e.getLocation().getBlockX() + ", " + e.getLocation().getBlockY() + ", " + e.getLocation().getBlockZ());
                l.add("");
                long cost = plugin.turrets.upgradeCost(tu.level);
                if (tu.level < 3 && cost >= 0) l.add("§aClic: mejorar §7(§e" + plugin.currency.fmt(cost) + "§7)");
                else l.add("§6Nivel máximo");
                l.add("§cMayús+clic: desmontar y recoger");
                return it(Material.CROSSBOW, "§6Torre nivel " + tu.level, l);
            }, (tu, e) -> {
                if (e.getClick().isShiftClick()) {
                    plugin.turrets.pickup(p, tu);
                } else {
                    String err = plugin.turrets.upgrade(p, tu);
                    Msg.to(p, err == null ? "§aTorre mejorada." : "§c" + err);
                }
                m.refresh();
            });
            m.button("comprar", "§aComprar torre", List.of("§7Precio: §e" + plugin.currency.fmt(plugin.turrets.buyPrice()) + " §7del banco", "§7Mariscal o superior."), e -> {
                String err = plugin.turrets.buy(p);
                Msg.to(p, err == null ? "§aComprada. Colócala dentro de tu territorio." : "§c" + err);
                m.refresh();
            });
            m.button("volver", "§7Volver", e -> plugin.guis.upgrades(p));
        }).at(page).open();
    }

    // ------------------------------------------------------------------ eventos (admin)

    public void adminEvents(Player p, int page) {
        if (!p.hasPermission("gremios.admin")) {
            Msg.to(p, "§cSin permiso.");
            return;
        }
        menu(p, "admin_eventos", m -> {
            List<EventDef> list = new ArrayList<>(plugin.events.all());
            m.paged(list, d -> {
                var a = plugin.events.running(d);
                List<String> l = new ArrayList<>();
                l.add("§7Tipo: §f" + d.type + (a != null ? " §a[EN MARCHA]" : ""));
                l.add("§7Zona: " + (d.hasZone ? "§a✔" : "§c✘ sin definir"));
                l.add("§7Duración: §f" + d.durationMin + " min");
                l.add("§7Recompensa: §e" + d.rewardXp + " XP" + (d.rewardCrowns > 0 ? " + " + d.rewardCrowns + " coronas" : ""));
                l.add("§7Cada: §f" + (d.intervalMin == 0 ? "manual" : d.intervalMin + " min"));
                l.add("");
                l.add("§aClic: editar");
                return it(d.type == EventDef.Type.KOTH ? Material.GOLDEN_HELMET : Material.WITHER_SKELETON_SKULL, "§6" + d.name + " §8(" + d.id + ")", l);
            }, (d, e) -> editEvent(p, d));
            m.button("nuevo", "§aNuevo evento", List.of("§7Te pedirá un nombre.", "§7Después elige tipo, zona y reglas."), e ->
                    plugin.prompts.ask(p, "§6Escribe el nombre del nuevo evento (ej: Colina del Rey):", name -> {
                        String id = name.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
                        if (id.length() > 24) id = id.substring(0, 24);
                        String err = plugin.events.create(id, EventDef.Type.KOTH, name);
                        if (err != null) {
                            Msg.to(p, "§c" + err);
                            adminEvents(p, 0);
                            return;
                        }
                        Msg.to(p, "§aEvento creado. Edítalo en el menú.");
                        editEvent(p, plugin.events.get(id));
                    }));
            m.button("volver", "§7Volver", e -> plugin.guis.admin(p));
        }).at(page).open();
    }

    private void ask(Player p, EventDef d, String field, String question) {
        plugin.prompts.ask(p, question, v -> {
            String err = plugin.events.setField(d, field, v);
            Msg.to(p, err == null ? "§aActualizado." : "§c" + err);
            editEvent(p, d);
        });
    }

    public void editEvent(Player p, EventDef d) {
        menu(p, "evento_edit", m -> {
            var run = plugin.events.running(d);
            m.cell(4, 0, it(d.type == EventDef.Type.KOTH ? Material.GOLDEN_HELMET : Material.WITHER_SKELETON_SKULL, "§6" + d.name,
                    "§7Id: §f" + d.id, "§7Estado: " + (run != null ? "§aEN MARCHA" : d.enabled ? "§7inactivo" : "§cdesactivado")), null);
            m.button("tipo", "§eTipo: §f" + d.type, List.of("§7KOTH: los gremios pelean por mantener la zona.", "§7JEFE: un jefe aparece y se reparte el botín.", "", "§aClic para cambiar"), e -> {
                plugin.events.setField(d, "tipo", d.type == EventDef.Type.KOTH ? "JEFE" : "KOTH");
                m.refresh();
            });
            m.button("zona", "§eZona: " + (d.hasZone ? "§a✔" : "§c✘"), List.of("§7Usa la varita (/territorio varita), marca", "§7las dos esquinas y haz clic aquí.", d.hasZone ? "§7Actual: §f" + d.world + " " + d.minX + "," + d.minZ + " → " + d.maxX + "," + d.maxZ : ""), e -> {
                Location[] sel = plugin.territories.sel(p.getUniqueId());
                String err = plugin.events.setZone(d, sel[0], sel[1]);
                Msg.to(p, err == null ? "§aZona fijada con tu selección." : "§c" + err);
                m.refresh();
            });
            m.button("duracion", "§eDuración: §f" + d.durationMin + " min", List.of("§7Tiempo máximo del evento.", "§aClic para cambiar"), e -> ask(p, d, "duracion", "§6Duración del evento en minutos:"));
            m.button("mantener", "§eMantener zona: §f" + d.holdSeconds + " s", List.of("§7KOTH: segundos acumulados para ganar.", "§aClic para cambiar"), e -> ask(p, d, "mantener", "§6Segundos que hay que mantener la zona:"));
            m.button("xp", "§eXP de gremio: §f" + d.rewardXp, List.of("§7Recompensa para el ganador (o por daño al jefe).", "§aClic para cambiar"), e -> ask(p, d, "xp", "§6XP de gremio de recompensa:"));
            m.button("coronas", "§eCoronas: §f" + d.rewardCrowns, List.of("§7Dinero para el banco del gremio ganador.", "§aClic para cambiar"), e -> ask(p, d, "coronas", "§6Coronas de recompensa (0 = ninguna):"));
            m.button("cada", "§eFrecuencia: §f" + (d.intervalMin == 0 ? "manual" : d.intervalMin + " min"), List.of("§7Cada cuántos minutos se lanza solo.", "§70 = solo cuando tú lo inicias.", "§aClic para cambiar"), e -> ask(p, d, "cada", "§6Cada cuántos minutos (0 = manual):"));
            m.button("nivel", "§eNivel mínimo: §f" + d.minGuildLevel, List.of("§7Nivel de gremio necesario para participar.", "§aClic para cambiar"), e -> ask(p, d, "nivel", "§6Nivel mínimo de gremio (1-20):"));
            StringBuilder sb = new StringBuilder();
            for (String id : d.allowed) {
                Guild g = plugin.guilds.byId(id);
                if (g != null) sb.append(g.name).append(", ");
            }
            m.button("gremios", "§eGremios: §f" + (d.allowed.isEmpty() ? "todos" : sb.toString().replaceAll(", $", "")), List.of("§7Quién puede participar.", "§aClic: escribe 'todos' o nombres separados por coma"), e -> ask(p, d, "gremios", "§6Gremios participantes (todos, o nombres separados por coma):"));
            m.button("jefe", "§eVida del jefe: §f" + (int) d.bossHp, List.of("§7Solo para tipo JEFE.", "§aClic para cambiar"), e -> ask(p, d, "jefe", "§6Vida del jefe:"));
            m.button("iniciar", "§aIniciar ahora", List.of("§7Lanza el evento ahora mismo."), e -> {
                String err = plugin.events.start(d);
                Msg.to(p, err == null ? "§aEvento iniciado." : "§c" + err);
                m.refresh();
            });
            m.button("parar", "§cParar evento", List.of("§7Detiene el evento en marcha."), e -> {
                String err = plugin.events.stop(d);
                Msg.to(p, err == null ? "§aDetenido." : "§c" + err);
                m.refresh();
            });
            m.button("estado", d.enabled ? "§aActivado §7(clic: desactivar)" : "§cDesactivado §7(clic: activar)", List.of("§7Si está desactivado no se lanza solo."), e -> {
                plugin.events.setField(d, "activo", d.enabled ? "no" : "si");
                m.refresh();
            });
            m.button("borrar", "§4Borrar evento", List.of("§cMayús+clic para borrar."), e -> {
                if (!e.getClick().isShiftClick()) {
                    Msg.to(p, "§7Usa Mayús+clic para confirmar.");
                    return;
                }
                plugin.events.delete(d.id);
                adminEvents(p, 0);
            });
            m.button("volver", "§7Volver", e -> adminEvents(p, 0));
        }).open();
    }
}
