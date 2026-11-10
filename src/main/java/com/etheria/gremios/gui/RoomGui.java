package com.etheria.gremios.gui;

import com.etheria.gremios.GremiosPlugin;
import com.etheria.gremios.manager.RoomManager;
import com.etheria.gremios.manager.RoomManager.Access;
import com.etheria.gremios.manager.RoomManager.Room;
import com.etheria.gremios.model.Guild;
import com.etheria.gremios.model.Rank;
import com.etheria.gremios.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Menú de casas y habitaciones (/gremio habitaciones). */
public class RoomGui implements Listener {
    private enum Mode { LIST, DETAIL, PLAYERS }

    private static final int[] GRID = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

    private static final class Holder implements InventoryHolder {
        Inventory inv;
        Mode mode = Mode.LIST;
        String room;
        boolean confirmDelete;
        final Map<Integer, String> slotRoom = new java.util.HashMap<>();
        final Map<Integer, UUID> slotPlayer = new java.util.HashMap<>();

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final GremiosPlugin plugin;

    public RoomGui(GremiosPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player p) {
        open(p, null);
    }

    public void open(Player p, Room r) {
        if (!plugin.rooms.enabled()) {
            Msg.to(p, "§cLas habitaciones están desactivadas.");
            return;
        }
        if (plugin.guilds.of(p) == null) {
            Msg.to(p, "§cNecesitas un gremio.");
            return;
        }
        Holder h = new Holder();
        h.inv = Bukkit.createInventory(h, 54, "§8🏠 §6Casas y habitaciones");
        if (r != null) {
            h.mode = Mode.DETAIL;
            h.room = r.id;
        }
        draw(h, p);
        p.openInventory(h.inv);
    }

    private void redraw(Holder h, Player p) {
        draw(h, p);
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

    private static String rk(Rank r) {
        return r == null ? "§8Solo dueño/líder" : r.colored() + "§7+";
    }

    private String ownerName(Room r) {
        if (r.owner == null) return "§8(sin dueño)";
        String n = Bukkit.getOfflinePlayer(r.owner).getName();
        return "§f" + (n == null ? "?" : n);
    }

    private void draw(Holder h, Player p) {
        h.inv.clear();
        h.slotRoom.clear();
        h.slotPlayer.clear();
        Guild g = plugin.guilds.of(p);
        Room r = h.room == null ? null : plugin.rooms.get(h.room);
        if (g == null || (h.mode != Mode.LIST && (r == null || !g.id.equals(r.guildId)))) {
            h.mode = Mode.LIST;
            h.room = null;
            r = null;
        }
        if (g == null) return;
        boolean manage = plugin.rooms.canManage(p, g);
        if (h.mode == Mode.LIST) {
            List<Room> list = plugin.rooms.of(g);
            for (int i = 0; i < list.size() && i < GRID.length; i++) {
                Room o = list.get(i);
                Access mine = plugin.rooms.level(p, o);
                List<String> lore = new ArrayList<>();
                lore.add("§7Dueño: " + ownerName(o));
                lore.add("§7Tamaño: §f" + (o.x2 - o.x1 + 1) + "×" + (o.y2 - o.y1 + 1) + "×" + (o.z2 - o.z1 + 1));
                lore.add("§7Tu acceso: " + mine.label);
                lore.add("§7Entrar: " + rk(o.entry));
                if (o.publico) lore.add("§a● Pública");
                if (o.aliados) lore.add("§a● Abierta a aliados");
                lore.add("");
                lore.add("§eClic: §fabrir detalles");
                lore.add("§eClic derecho: §fteletransportarte");
                h.inv.setItem(GRID[i], item(p.getUniqueId().equals(o.owner) ? Material.RED_BED : Material.OAK_DOOR, "§6" + o.name, lore, p.getUniqueId().equals(o.owner)));
                h.slotRoom.put(GRID[i], o.id);
            }
            if (list.isEmpty()) h.inv.setItem(22, item(Material.BARRIER, "§7Aún no hay habitaciones", List.of("§8Usa la varita y pulsa «Crear»."), false));
            Location[] s = plugin.rooms.selection(p.getUniqueId());
            boolean ok = s[0] != null && s[1] != null;
            if (manage) {
                h.inv.setItem(46, item(Material.STICK, "§dVarita de selección", List.of("§7Clic izquierdo: punto 1", "§7Clic derecho: punto 2", "", "§eClic: §frecibir la varita"), false));
                h.inv.setItem(47, item(ok ? Material.EMERALD : Material.GRAY_DYE, ok ? "§a§lCrear habitación" : "§7Crear habitación",
                        List.of(ok ? "§7Usa tu selección actual." : "§cPrimero marca dos puntos con la varita.", "", "§eClic: §fponerle nombre"), ok));
            }
            Room mine = plugin.rooms.ownedBy(g, p.getUniqueId());
            h.inv.setItem(45, item(Material.RED_BED, "§bMi casa", mine == null ? List.of("§8Aún no tienes casa asignada.", "§8Pídele a un líder que te asigne una.") : List.of("§7«" + mine.name + "»", "", "§eClic: §fir a mi casa"), mine != null));
            h.inv.setItem(49, item(Material.BOOK, "§6Habitaciones", List.of("§7Tu gremio: §f" + list.size() + "§7/§f" + plugin.getConfig().getInt("habitaciones.maximo-por-gremio", 8),
                    "§7Gestionan: " + plugin.rooms.manageRank().colored() + "§7+", "§8Cada habitación define quién puede", "§8entrar, usar (cofres/puertas) y construir."), false));
        } else if (h.mode == Mode.DETAIL) {
            boolean edit = plugin.rooms.canEdit(p, r);
            h.inv.setItem(4, item(Material.OAK_DOOR, "§6§l" + r.name, List.of("§7Dueño: " + ownerName(r), "§7Tu acceso: " + plugin.rooms.level(p, r).label), true));
            h.inv.setItem(10, item(Material.NAME_TAG, "§eRenombrar", List.of(manage ? "§eClic: §fcambiar nombre" : "§8Solo gestores"), false));
            h.inv.setItem(11, item(Material.PLAYER_HEAD, "§eAsignar dueño", List.of("§7Actual: " + ownerName(r), manage ? "§eClic: §fescribir el nombre del miembro" : "§8Solo gestores", manage ? "§eClic derecho: §fquitar dueño" : ""), false));
            h.inv.setItem(12, item(Material.OAK_DOOR, "§eEntrar", List.of("§7Rango mínimo: " + rk(r.entry), edit ? "§eClic: §fcambiar" : "§8Sin permiso"), false));
            h.inv.setItem(13, item(Material.CHEST, "§eUsar §8(cofres, puertas, palancas)", List.of("§7Rango mínimo: " + rk(r.use), edit ? "§eClic: §fcambiar" : "§8Sin permiso"), false));
            h.inv.setItem(14, item(Material.IRON_PICKAXE, "§eConstruir", List.of("§7Rango mínimo: " + rk(r.build), edit ? "§eClic: §fcambiar" : "§8Sin permiso"), false));
            h.inv.setItem(15, item(r.publico ? Material.LIME_DYE : Material.GRAY_DYE, "§ePública", List.of("§7Cualquiera puede entrar: " + (r.publico ? "§aSí" : "§cNo"), edit ? "§eClic: §falternar" : "§8Sin permiso"), r.publico));
            h.inv.setItem(16, item(r.aliados ? Material.LIME_DYE : Material.GRAY_DYE, "§eAbierta a aliados", List.of("§7Gremios aliados pueden entrar: " + (r.aliados ? "§aSí" : "§cNo"), edit ? "§eClic: §falternar" : "§8Sin permiso"), r.aliados));
            h.inv.setItem(19, item(Material.PLAYER_HEAD, "§ePermisos por jugador", List.of("§7Excepciones: §f" + r.players.size(), "§8Dar o quitar acceso a alguien concreto.", edit ? "§eClic: §fabrir" : "§8Sin permiso"), false));
            h.inv.setItem(20, item(Material.COMPASS, "§eFijar punto de aparición", List.of(r.spawn == null ? "§8(centro de la sala)" : "§7Ya definido", edit ? "§eClic: §fusar tu posición (dentro de la sala)" : "§8Sin permiso"), false));
            h.inv.setItem(21, item(Material.ENDER_PEARL, "§bTeletransportarme", List.of("§eClic: §fir a la habitación"), false));
            h.inv.setItem(22, item(Material.STICK, "§dRedefinir zona", List.of(manage ? "§7Usa tu selección actual de la varita." : "§8Solo gestores", manage ? "§eClic: §faplicar" : ""), false));
            h.inv.setItem(23, item(h.confirmDelete ? Material.TNT : Material.LAVA_BUCKET, h.confirmDelete ? "§c§l¿Seguro? Clic otra vez" : "§cEliminar habitación",
                    List.of(manage ? "§7No borra bloques, solo los permisos." : "§8Solo gestores"), h.confirmDelete));
            h.inv.setItem(45, item(Material.ARROW, "§7« Volver", List.of(), false));
        } else {
            h.inv.setItem(4, item(Material.PLAYER_HEAD, "§6Permisos por jugador: §f" + r.name, List.of("§eClic en una cabeza: §fcambia el nivel", "§7Denegado → Entrar → Usar → Construir → quitar"), true));
            int i = 0;
            for (var en : r.players.entrySet()) {
                if (i >= GRID.length) break;
                ItemStack it = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta m = (SkullMeta) it.getItemMeta();
                m.setOwningPlayer(Bukkit.getOfflinePlayer(en.getKey()));
                String n = Bukkit.getOfflinePlayer(en.getKey()).getName();
                m.setDisplayName("§f" + (n == null ? en.getKey().toString().substring(0, 8) : n));
                m.setLore(List.of("§7Nivel: " + en.getValue().label, "", "§eClic: §fsiguiente nivel", "§eClic derecho: §fquitar"));
                it.setItemMeta(m);
                h.inv.setItem(GRID[i], it);
                h.slotPlayer.put(GRID[i], en.getKey());
                i++;
            }
            h.inv.setItem(47, item(Material.EMERALD, "§aAñadir jugador", List.of("§eClic: §fescribir su nombre"), false));
            h.inv.setItem(45, item(Material.ARROW, "§7« Volver", List.of(), false));
        }
        h.inv.setItem(53, item(Material.BARRIER, "§cCerrar", List.of(), false));
    }

    private UUID findUuid(String name, Guild restrict) {
        Player on = Bukkit.getPlayerExact(name);
        if (on != null) return on.getUniqueId();
        if (restrict != null) {
            for (UUID u : restrict.members.keySet()) {
                String n = Bukkit.getOfflinePlayer(u).getName();
                if (n != null && n.equalsIgnoreCase(name)) return u;
            }
        }
        org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(name);
        return op.hasPlayedBefore() ? op.getUniqueId() : null;
    }

    private void reopen(Player p, Room r) {
        open(p, r);
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
        int s = e.getRawSlot();
        boolean right = e.isRightClick();
        if (s == 53) {
            p.closeInventory();
            return;
        }
        Room r = h.room == null ? null : plugin.rooms.get(h.room);
        boolean manage = plugin.rooms.canManage(p, g);
        if (h.mode == Mode.LIST) {
            if (h.slotRoom.containsKey(s)) {
                Room o = plugin.rooms.get(h.slotRoom.get(s));
                if (o == null) return;
                if (right) {
                    String err = plugin.rooms.teleport(p, o);
                    if (err != null) Msg.to(p, "§c" + err);
                    else p.closeInventory();
                } else {
                    h.mode = Mode.DETAIL;
                    h.room = o.id;
                    h.confirmDelete = false;
                    redraw(h, p);
                }
            } else if (s == 45) {
                Room mine = plugin.rooms.ownedBy(g, p.getUniqueId());
                if (mine != null) {
                    String err = plugin.rooms.teleport(p, mine);
                    if (err != null) Msg.to(p, "§c" + err);
                    else p.closeInventory();
                }
            } else if (s == 46 && manage) {
                plugin.rooms.giveWand(p);
                Msg.to(p, "§dTe di la varita de habitaciones.");
            } else if (s == 47 && manage) {
                Location[] sel = plugin.rooms.selection(p.getUniqueId());
                if (sel[0] == null || sel[1] == null) {
                    Msg.to(p, "§cMarca dos puntos con la varita primero.");
                    return;
                }
                plugin.prompts.ask(p, "§eEscribe el nombre de la nueva habitación:", txt -> {
                    String err = plugin.rooms.create(p, txt);
                    if (err != null) Msg.to(p, "§c" + err);
                    else Msg.to(p, "§aHabitación «" + txt + "» creada.");
                    open(p);
                });
            }
            return;
        }
        if (r == null) return;
        if (s == 45) {
            h.mode = h.mode == Mode.PLAYERS ? Mode.DETAIL : Mode.LIST;
            h.confirmDelete = false;
            if (h.mode == Mode.LIST) h.room = null;
            redraw(h, p);
            return;
        }
        boolean edit = plugin.rooms.canEdit(p, r);
        if (h.mode == Mode.DETAIL) {
            switch (s) {
                case 10 -> {
                    if (!manage) return;
                    plugin.prompts.ask(p, "§eNuevo nombre de la habitación:", txt -> {
                        String err = plugin.rooms.rename(r, txt);
                        if (err != null) Msg.to(p, "§c" + err);
                        reopen(p, r);
                    });
                }
                case 11 -> {
                    if (!manage) return;
                    if (right) {
                        plugin.rooms.setOwner(r, null);
                        redraw(h, p);
                    } else plugin.prompts.ask(p, "§eEscribe el nombre del miembro que será dueño:", txt -> {
                        UUID u = findUuid(txt, g);
                        String err = u == null ? "No encuentro a ese jugador." : plugin.rooms.setOwner(r, u);
                        if (err != null) Msg.to(p, "§c" + err);
                        else Msg.to(p, "§aDueño asignado.");
                        reopen(p, r);
                    });
                }
                case 12 -> {
                    if (!edit) return;
                    r.entry = plugin.rooms.cycle(r.entry);
                    plugin.rooms.save();
                    redraw(h, p);
                }
                case 13 -> {
                    if (!edit) return;
                    r.use = plugin.rooms.cycle(r.use);
                    plugin.rooms.save();
                    redraw(h, p);
                }
                case 14 -> {
                    if (!edit) return;
                    r.build = plugin.rooms.cycle(r.build);
                    plugin.rooms.save();
                    redraw(h, p);
                }
                case 15 -> {
                    if (!edit) return;
                    r.publico = !r.publico;
                    plugin.rooms.save();
                    redraw(h, p);
                }
                case 16 -> {
                    if (!edit) return;
                    r.aliados = !r.aliados;
                    plugin.rooms.save();
                    redraw(h, p);
                }
                case 19 -> {
                    if (!edit) return;
                    h.mode = Mode.PLAYERS;
                    redraw(h, p);
                }
                case 20 -> {
                    if (!edit) return;
                    String err = plugin.rooms.setSpawn(p, r);
                    Msg.to(p, err == null ? "§aPunto de aparición fijado." : "§c" + err);
                    redraw(h, p);
                }
                case 21 -> {
                    String err = plugin.rooms.teleport(p, r);
                    if (err != null) Msg.to(p, "§c" + err);
                    else p.closeInventory();
                }
                case 22 -> {
                    if (!manage) return;
                    String err = plugin.rooms.redefine(p, r);
                    Msg.to(p, err == null ? "§aZona actualizada." : "§c" + err);
                    redraw(h, p);
                }
                case 23 -> {
                    if (!manage) return;
                    if (!h.confirmDelete) {
                        h.confirmDelete = true;
                        redraw(h, p);
                    } else {
                        plugin.rooms.delete(r);
                        Msg.to(p, "§cHabitación eliminada.");
                        h.mode = Mode.LIST;
                        h.room = null;
                        h.confirmDelete = false;
                        redraw(h, p);
                    }
                }
                default -> {
                }
            }
            return;
        }
        // PLAYERS
        if (!edit) return;
        if (s == 47) {
            plugin.prompts.ask(p, "§eEscribe el nombre del jugador (puede ser de otro gremio):", txt -> {
                UUID u = findUuid(txt, g);
                if (u == null) Msg.to(p, "§cNo encuentro a ese jugador.");
                else {
                    plugin.rooms.setPlayer(r, u, Access.ENTRAR);
                    Msg.to(p, "§aAñadido con acceso para entrar. Cambia el nivel con clic.");
                }
                open(p, r);
                Inventory top = p.getOpenInventory().getTopInventory();
                if (top.getHolder() instanceof Holder nh) {
                    nh.mode = Mode.PLAYERS;
                    draw(nh, p);
                }
            });
            return;
        }
        UUID u = h.slotPlayer.get(s);
        if (u == null) return;
        Access cur = r.players.get(u);
        if (right) plugin.rooms.setPlayer(r, u, null);
        else if (cur == Access.CONSTRUIR) plugin.rooms.setPlayer(r, u, null);
        else plugin.rooms.setPlayer(r, u, Access.values()[cur == null ? 1 : cur.ordinal() + 1]);
        redraw(h, p);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
