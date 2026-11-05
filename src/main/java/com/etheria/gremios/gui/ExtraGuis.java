package com.etheria.gremios.gui;

import com.etheria.gremios.GremiosPlugin;
import com.etheria.gremios.manager.ReinoManager;
import com.etheria.gremios.manager.TurretAmmo;
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
                    "", "§8Disparan solas a mobs y enemigos", "§8si tienen flechas cargadas.", "§8Fabrícalas en el §6Herrero§8."), null);
            List<TurretManager.Turret> list = plugin.turrets.of(g);
            m.paged(list, tu -> {
                Entity e = Bukkit.getEntity(tu.slime);
                List<String> l = new ArrayList<>();
                l.add("§7Nivel: §f" + tu.level + "§7/3");
                l.add("§7Vida: §c" + (int) tu.hp + "§7/" + (int) plugin.turrets.maxHp(tu.level));
                l.add("§7Flechas: §f" + plugin.turrets.total(tu) + " §8(" + tu.ammo[0] + "/" + tu.ammo[1] + "/" + tu.ammo[2] + "/" + tu.ammo[3] + ")");
                if (e != null) l.add("§7Posición: §f" + e.getLocation().getBlockX() + ", " + e.getLocation().getBlockY() + ", " + e.getLocation().getBlockZ());
                l.add("");
                long cost = plugin.turrets.upgradeCost(tu.level);
                if (tu.level < 3 && cost >= 0) l.add("§aClic: mejorar §7(§e" + plugin.currency.fmt(cost) + "§7)");
                else l.add("§6Nivel máximo");
                l.add("§eClic derecho: munición");
                l.add("§cMayús+clic: desmontar y recoger");
                return it(Material.CROSSBOW, "§6Torre nivel " + tu.level, l);
            }, (tu, e) -> {
                if (e.getClick().isRightClick() && !e.getClick().isShiftClick()) {
                    ammo(p, tu);
                    return;
                }
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

    // ------------------------------------------------------------------ misiones

    public void missions(Player p, int page) {
        menu(p, "misiones", m -> {
            Guild g = plugin.guilds.of(p);
            if (g == null) {
                p.closeInventory();
                return;
            }
            m.cell(4, 0, it(Material.WRITABLE_BOOK, "§6Misiones de gremio", "§7La XP va a §f" + g.name, "§7Nivel de gremio: §f" + g.level, "",
                    "§8Entregar: clic con los objetos en el inventario.", "§8Matar / minar: se cuentan solas."), null);
            List<com.etheria.gremios.manager.MissionManager.Mission> list = new ArrayList<>();
            for (var mi : plugin.missions.all()) if (mi.enabled) list.add(mi);
            m.paged(list, mi -> {
                long wait = plugin.missions.waitMs(p, mi);
                List<String> l = new ArrayList<>();
                String verbo = switch (mi.type) {
                    case ENTREGAR -> "Entrega";
                    case MATAR -> "Mata";
                    case MINAR -> "Mina";
                };
                l.add("§7" + verbo + ": §f" + mi.amount + "x " + mi.targetDisplay());
                l.add("§7Progreso: §f" + plugin.missions.count(p, mi) + "§7/§f" + mi.amount);
                l.add("§7Recompensa: §e+" + mi.rewardXp + " XP de gremio" + (mi.rewardCrowns > 0 ? "§7 y §e" + plugin.currency.fmt(mi.rewardCrowns) + " al banco" : ""));
                l.add("§7Se repite: §f" + mi.repeat.toLowerCase());
                l.add("");
                if (wait < 0) l.add("§8Completada (misión única).");
                else if (wait > 0) l.add("§cDisponible en " + Msg.dur(wait));
                else if (mi.type == com.etheria.gremios.manager.MissionManager.Type.ENTREGAR) l.add("§aClic para entregar");
                else l.add("§aEn curso (se cuenta sola)");
                Material icon = mi.type == com.etheria.gremios.manager.MissionManager.Type.MATAR ? Material.IRON_SWORD : mi.material();
                return it(icon, "§6" + mi.name, l);
            }, (mi, e) -> {
                if (mi.type != com.etheria.gremios.manager.MissionManager.Type.ENTREGAR) return;
                int n = plugin.missions.deliver(p, mi);
                if (n == 0) Msg.to(p, "§cNo tienes §f" + mi.targetDisplay() + " §co la misión no está disponible.");
                m.refresh();
            });
            m.button("volver", "§7Volver", e -> plugin.guis.main(p));
        }).at(page).open();
    }

    // ------------------------------------------------------------------ zonas de recursos (admin)

    public void adminZones(Player p, int page) {
        if (!p.hasPermission("gremios.admin")) {
            Msg.to(p, "§cSin permiso.");
            return;
        }
        menu(p, "admin_zonas", m -> {
            List<com.etheria.gremios.manager.ZoneManager.Zone> list = new ArrayList<>(plugin.zones.all());
            m.paged(list, z -> {
                List<String> l = new ArrayList<>();
                l.add("§7Tipo: §f" + z.kind);
                l.add("§7Dueño: " + (z.owner == null ? "§aLibre" : "§6" + plugin.zones.factionName(z.owner)));
                l.add("§7Recursos: §f" + z.produce.size() + " §8| cada " + z.intervalMin + " min");
                l.add("§7Captura: §f" + z.captureSeconds + " s");
                l.add("§7Área: " + (z.hasArea ? "§a✔" : "§c✘") + " §7Aldeano: " + (z.npc != null ? "§a✔" : "§c✘"));
                l.add("");
                l.add("§aClic: editar");
                return it(switch (z.kind) {
                    case "TALA" -> Material.IRON_AXE;
                    case "CANTERA" -> Material.STONE_PICKAXE;
                    case "GRANJA" -> Material.IRON_HOE;
                    default -> Material.IRON_PICKAXE;
                }, "§6" + z.name + " §8(" + z.id + ")", l);
            }, (z, e) -> editZone(p, z));
            m.button("nuevo", "§aNueva zona", List.of("§7Te pedirá un nombre.", "§7Luego marca área, aldeano y recursos."), e ->
                    plugin.prompts.ask(p, "§6Nombre de la nueva zona (ej: Mina de Hierro):", name -> {
                        String id = name.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
                        if (id.length() > 24) id = id.substring(0, 24);
                        String err = plugin.zones.create(id, "MINA", name);
                        if (err != null) {
                            Msg.to(p, "§c" + err);
                            adminZones(p, 0);
                            return;
                        }
                        Msg.to(p, "§aZona creada. Edítala en el menú.");
                        editZone(p, plugin.zones.get(id));
                    }));
            m.button("volver", "§7Volver", e -> plugin.guis.admin(p));
        }).at(page).open();
    }

    private void askZone(Player p, com.etheria.gremios.manager.ZoneManager.Zone z, String field, String question) {
        plugin.prompts.ask(p, question, v -> {
            String err = plugin.zones.setField(z, field, v);
            Msg.to(p, err == null ? "§aActualizado." : "§c" + err);
            editZone(p, z);
        });
    }

    public void editZone(Player p, com.etheria.gremios.manager.ZoneManager.Zone z) {
        menu(p, "zona_edit", m -> {
            m.cell(4, 0, it(Material.IRON_PICKAXE, "§6" + z.name, "§7Id: §f" + z.id, "§7Tipo: §f" + z.kind,
                    "§7Dueño: " + (z.owner == null ? "§aLibre" : "§6" + plugin.zones.factionName(z.owner))), null);
            m.button("area", "§eÁrea: " + (z.hasArea ? "§a✔" : "§c✘"), List.of("§7Marca 2 esquinas con la varita", "§7(/territorio varita) y haz clic aquí."), e -> {
                Location[] sel = plugin.territories.sel(p.getUniqueId());
                String err = plugin.zones.setArea(z, sel[0], sel[1]);
                Msg.to(p, err == null ? "§aÁrea fijada con tu selección." : "§c" + err);
                m.refresh();
            });
            m.button("npc", "§eAldeano: " + (z.npc != null ? "§a✔" : "§c✘"), List.of("§7Lo coloca donde estás parado.", "§7Con clic derecho abre el cofre de la zona."), e -> {
                plugin.zones.setNpc(z, p.getLocation());
                Msg.to(p, "§aAldeano colocado aquí.");
                m.refresh();
            });
            List<String> rl = new ArrayList<>();
            rl.add("§7Sostén en la mano el objeto que produce.");
            rl.add("§7La cantidad de la mano = cantidad por ciclo.");
            rl.add("");
            for (ItemStack r : z.produce) rl.add("§f- " + r.getAmount() + "x " + r.getType().name());
            m.button("recurso", "§eAgregar recurso (mano)", rl, e -> {
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (hand == null || hand.getType().isAir()) {
                    Msg.to(p, "§cSostén un objeto en la mano.");
                    return;
                }
                plugin.zones.addProduce(z, hand, hand.getAmount());
                Msg.to(p, "§aAñadido: §f" + hand.getAmount() + "x " + hand.getType().name() + " §apor ciclo.");
                m.refresh();
            });
            m.button("limpiar", "§cQuitar recursos", List.of("§7Borra la lista de recursos."), e -> {
                plugin.zones.clearProduce(z);
                m.refresh();
            });
            m.button("cada", "§eCada: §f" + z.intervalMin + " min", List.of("§7Cada cuánto produce un ciclo.", "§aClic para cambiar"), e -> askZone(p, z, "cada", "§6Minutos entre ciclos de producción:"));
            m.button("capacidad", "§eCapacidad: §f" + z.maxCycles + " ciclos", List.of("§7Cuántos ciclos se acumulan como máximo.", "§aClic para cambiar"), e -> askZone(p, z, "capacidad", "§6Ciclos que se pueden acumular:"));
            m.button("captura", "§eCaptura: §f" + z.captureSeconds + " s", List.of("§7Segundos dentro de la zona para conquistarla.", "§aClic para cambiar"), e -> askZone(p, z, "captura", "§6Segundos para conquistar la zona:"));
            m.button("tipo", "§eTipo: §f" + z.kind, List.of("§7MINA, TALA, CANTERA, GRANJA, PESCA...", "§aClic para cambiar"), e -> askZone(p, z, "tipo", "§6Tipo de zona (MINA, TALA, CANTERA, GRANJA, PESCA):"));
            m.button("liberar", "§6Liberar zona", List.of("§7Quita al dueño actual y vacía el almacén."), e -> {
                plugin.zones.release(z);
                m.refresh();
            });
            m.button("borrar", "§4Borrar zona", List.of("§cMayús+clic para borrar."), e -> {
                if (!e.getClick().isShiftClick()) {
                    Msg.to(p, "§7Usa Mayús+clic para confirmar.");
                    return;
                }
                plugin.zones.delete(z);
                adminZones(p, 0);
            });
            m.button("volver", "§7Volver", e -> adminZones(p, 0));
        }).open();
    }

    // ------------------------------------------------------------------ munición de torre

    public void ammo(Player p, TurretManager.Turret tu) {
        menu(p, "municion", m -> {
            Guild g = plugin.guilds.of(p);
            if (g == null || !g.id.equals(tu.guildId)) {
                p.closeInventory();
                return;
            }
            int cap = plugin.turrets.capacity(tu);
            m.cell(4, 0, it(Material.CROSSBOW, "§6Torre nivel " + tu.level, "§7Flechas: §f" + plugin.turrets.total(tu) + "§7 (máx. §f" + cap + " §7por tipo)",
                    "§7Vida: §c" + (int) tu.hp, "", "§8Sin flechas la torre no dispara.", "§8Se fabrican en el §6Herrero§8."), null);
            for (TurretAmmo.Kind k : TurretAmmo.Kind.values()) {
                int i = k.ordinal();
                List<String> lore = new ArrayList<>();
                lore.add(plugin.ammo.describe(k));
                lore.add("§7Daño extra: §f+" + plugin.ammo.extraDamage(k));
                lore.add("§7En tu inventario: §f" + countKind(p, k));
                lore.add(tu.sel == i ? "§a★ Se dispara primero" : "§8Automático");
                lore.add("");
                lore.add("§aClic: cargar las que lleves");
                lore.add("§eClic derecho: sacar todas");
                lore.add("§bMayús+clic: usar primero");
                m.button("m" + i, k.color + k.display + " §7" + tu.ammo[i] + "/" + cap, lore, e -> {
                    if (e.getClick().isShiftClick()) {
                        tu.sel = tu.sel == i ? -1 : i;
                        plugin.turrets.persist(tu);
                    } else if (e.getClick().isRightClick()) {
                        int n = plugin.turrets.unload(p, tu, k);
                        Msg.to(p, n > 0 ? "§aRecuperaste §f" + n + " §7flechas." : "§7No había flechas de ese tipo.");
                    } else {
                        int n = plugin.turrets.load(p, tu, k);
                        Msg.to(p, n > 0 ? "§a+" + n + " §7" + k.display + "." : "§cNo tienes flechas de ese tipo o la torre está llena.");
                    }
                    m.refresh();
                });
            }
            m.button("volver", "§7Volver", e -> turrets(p, 0));
        }).open();
    }

    private int countKind(Player p, TurretAmmo.Kind k) {
        int n = 0;
        for (ItemStack i : p.getInventory().getStorageContents()) if (i != null && plugin.ammo.kindOf(i) == k) n += i.getAmount();
        return n;
    }

    // ------------------------------------------------------------------ mejoras de reino

    private String effectText(ReinoManager.Track t, int lv) {
        double a = lv * plugin.reino.perLevel(t), b = lv * plugin.reino.perLevel2(t);
        return switch (t) {
            case ARMERIA -> "-" + (int) a + "% precio de flechas, +" + (int) b + " capacidad";
            case ATALAYA -> "+" + (int) a + "% daño, +" + (int) b + " bloques de alcance";
            case MURALLA -> "+" + (int) a + "% vida de puertas";
            case TESORO -> "+" + (int) a + "% producción de zonas";
            case HOSPITAL -> "Regeneración " + lv;
            case FORJA -> "-" + (int) a + "% precios del Herrero";
        };
    }

    public void reino(Player p, int page) {
        menu(p, "reino", m -> {
            Guild g = plugin.guilds.of(p);
            if (g == null) {
                p.closeInventory();
                return;
            }
            m.cell(4, 0, it(Material.GOLDEN_HELMET, "§6Mejoras del Reino", "§7Banco: §e" + plugin.currency.fmt(g.bank),
                    "§7Territorio: §f" + Guild.levelName(g.territoryLevel), "", "§8Se pagan del banco (Mariscal o superior)."), null);
            m.paged(java.util.Arrays.asList(ReinoManager.Track.values()), tr -> {
                int lv = plugin.reino.level(g, tr), max = plugin.reino.max(tr);
                List<String> l = new ArrayList<>();
                l.add(tr.desc);
                l.add("");
                l.add("§7Nivel: §f" + lv + "§7/§f" + max);
                l.add("§7Ahora: §f" + (lv == 0 ? "—" : effectText(tr, lv)));
                if (lv < max) {
                    l.add("§7Siguiente: §a" + effectText(tr, lv + 1));
                    l.add("§7Coste: §e" + plugin.currency.fmt(plugin.reino.cost(tr, lv)));
                    if (g.territoryLevel < plugin.reino.minTerritory(tr)) l.add("§cRequiere " + Guild.levelName(plugin.reino.minTerritory(tr)));
                    else l.add("§aClic para mejorar");
                } else l.add("§6Nivel máximo");
                return it(tr.icon, "§6" + tr.display, l);
            }, (tr, e) -> {
                String err = plugin.reino.buy(p, tr);
                Msg.to(p, err == null ? "§a" + tr.display + " mejorada." : "§c" + err);
                m.refresh();
            });
            m.button("volver", "§7Volver", e -> plugin.guis.upgrades(p));
        }).at(page).open();
    }

    // ------------------------------------------------------------------ discord

    public void discord(Player p) {
        menu(p, "discord", m -> {
            var d = plugin.discord;
            m.cell(4, 0, it(Material.NETHER_STAR, "§9Anuncios a Discord",
                    "§7Estado: " + (d.enabled() ? "§aACTIVO" : "§cAPAGADO"),
                    d.hasWebhook() ? "§7Webhook: §aconfigurado" : "§7Webhook: §cfalta (config.yml → discord.webhook)",
                    "", "§8Clic en un tipo para activarlo o apagarlo."), null);
            String[] names = com.etheria.gremios.manager.DiscordManager.KINDS;
            for (int i = 0; i < names.length; i++) {
                String k = names[i];
                boolean on = d.typeOn(k);
                m.button("t" + i, (on ? "§a" : "§c") + com.etheria.gremios.manager.DiscordManager.label(k),
                        List.of("§7" + com.etheria.gremios.manager.DiscordManager.describe(k), "", on ? "§aActivado §7(clic: apagar)" : "§cApagado §7(clic: activar)"),
                        e -> {
                            d.setType(k, !d.typeOn(k));
                            m.refresh();
                        });
            }
            m.button("probar", "§6Enviar prueba", List.of("§7Manda un mensaje al canal y te dice", "§7si Discord lo aceptó."), e -> d.test(p));
            m.button("volver", "§7Volver", e -> plugin.guis.admin(p));
        }).open();
    }
}
