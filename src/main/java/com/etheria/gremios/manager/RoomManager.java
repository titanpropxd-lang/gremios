package com.etheria.gremios.manager;

import com.etheria.gremios.GremiosPlugin;
import com.etheria.gremios.model.Guild;
import com.etheria.gremios.model.Rank;
import com.etheria.gremios.model.Territory;
import com.etheria.gremios.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Casas y habitaciones: zonas dentro del territorio del gremio con permisos propios.
 * Cada habitación tiene dueño opcional, rangos mínimos para entrar/usar/construir, modo público/aliados
 * y permisos individuales por jugador. Durante un asedio sobre el territorio las habitaciones se ignoran.
 */
public class RoomManager implements Listener {
    public enum Access {
        DENEGADO("§cDenegado"), ENTRAR("§eEntrar"), USAR("§aUsar"), CONSTRUIR("§bConstruir");
        public final String label;

        Access(String l) {
            this.label = l;
        }
    }

    public static final class Room {
        public final String id;
        public String guildId, territoryId, name, world;
        public int x1, y1, z1, x2, y2, z2;
        public UUID owner;
        public Rank entry = Rank.RECLUTA, use = Rank.RECLUTA, build = Rank.RECLUTA; // null = nadie (solo líder/dueño)
        public boolean publico, aliados;
        public final Map<UUID, Access> players = new LinkedHashMap<>();
        public double[] spawn;

        Room(String id) {
            this.id = id;
        }

        public boolean contains(String w, int x, int y, int z) {
            return world.equals(w) && x >= x1 && x <= x2 && y >= y1 && y <= y2 && z >= z1 && z <= z2;
        }

        public long volume() {
            return (long) (x2 - x1 + 1) * (y2 - y1 + 1) * (z2 - z1 + 1);
        }

        boolean overlaps(Room o) {
            return world.equals(o.world) && x1 <= o.x2 && x2 >= o.x1 && y1 <= o.y2 && y2 >= o.y1 && z1 <= o.z2 && z2 >= o.z1;
        }
    }

    private final GremiosPlugin plugin;
    private final File file;
    private final NamespacedKey kWand;
    private final Map<String, Room> rooms = new LinkedHashMap<>();
    private final Map<UUID, Location[]> sel = new HashMap<>();
    private int next = 1;

    public RoomManager(GremiosPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "habitaciones.yml");
        this.kWand = plugin.key("varita_habitacion");
        load();
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("habitaciones.activo", true);
    }

    public Collection<Room> all() {
        return rooms.values();
    }

    public Room get(String id) {
        return id == null ? null : rooms.get(id);
    }

    public List<Room> of(Guild g) {
        List<Room> out = new ArrayList<>();
        if (g != null) for (Room r : rooms.values()) if (g.id.equals(r.guildId)) out.add(r);
        return out;
    }

    public Room ownedBy(Guild g, UUID u) {
        for (Room r : of(g)) if (u.equals(r.owner) && g.members.containsKey(u)) return r;
        return null;
    }

    public Room at(Location l) {
        if (l == null || l.getWorld() == null) return null;
        String w = l.getWorld().getName();
        int x = l.getBlockX(), y = l.getBlockY(), z = l.getBlockZ();
        for (Room r : rooms.values()) if (r.contains(w, x, y, z)) return r;
        return null;
    }

    // ------------------------------------------------------------------ permisos

    public Rank manageRank() {
        return rankCfg("habitaciones.rango-gestionar", Rank.MARISCAL, false);
    }

    private Rank rankCfg(String path, Rank def, boolean allowNone) {
        String s = plugin.getConfig().getString(path, def == null ? "NADIE" : def.name()).toUpperCase(Locale.ROOT);
        if (allowNone && s.equals("NADIE")) return null;
        try {
            return Rank.valueOf(s);
        } catch (IllegalArgumentException e) {
            return def;
        }
    }

    public boolean canManage(Player p, Guild g) {
        if (g == null) return false;
        if (plugin.bypass.contains(p.getUniqueId())) return true;
        Rank r = g.rankOf(p.getUniqueId());
        return r != null && r.atLeast(manageRank());
    }

    /** Dueños pueden ajustar permisos y spawn de su habitación; gestores todo. */
    public boolean canEdit(Player p, Room r) {
        Guild g = plugin.guilds.byId(r.guildId);
        if (g == null) return false;
        return canManage(p, g) || (p.getUniqueId().equals(r.owner) && g.members.containsKey(p.getUniqueId()));
    }

    public Access level(Player p, Room r) {
        UUID u = p.getUniqueId();
        if (plugin.bypass.contains(u)) return Access.CONSTRUIR;
        if (plugin.raids.get(r.territoryId) != null) return Access.CONSTRUIR;
        Guild g = plugin.guilds.of(p);
        boolean member = g != null && g.id.equals(r.guildId);
        if (member) {
            Rank rk = g.rankOf(u);
            if (rk == Rank.LIDER || u.equals(r.owner)) return Access.CONSTRUIR;
            Access ov = r.players.get(u);
            if (ov != null) return ov;
            if (rk != null) {
                if (r.build != null && rk.atLeast(r.build)) return Access.CONSTRUIR;
                if (r.use != null && rk.atLeast(r.use)) return Access.USAR;
                if (r.entry != null && rk.atLeast(r.entry)) return Access.ENTRAR;
            }
            return Access.DENEGADO;
        }
        Access ov = r.players.get(u);
        if (ov != null) return ov;
        if (r.publico) return Access.ENTRAR;
        Guild owner = plugin.guilds.byId(r.guildId);
        if (r.aliados && g != null && owner != null && plugin.guilds.isAllied(g, owner)) return Access.ENTRAR;
        return Access.DENEGADO;
    }

    // ------------------------------------------------------------------ selección

    public Location[] selection(UUID u) {
        return sel.computeIfAbsent(u, k -> new Location[2]);
    }

    public ItemStack wand() {
        Material m = Material.matchMaterial(plugin.getConfig().getString("habitaciones.varita", "STICK"));
        ItemStack it = new ItemStack(m == null ? Material.STICK : m);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName("§d✦ Varita de habitaciones");
        im.setLore(List.of("§7Clic izquierdo: §fpunto 1", "§7Clic derecho: §fpunto 2", "§8Luego: /gremio habitaciones → Crear"));
        im.getPersistentDataContainer().set(kWand, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(im);
        return it;
    }

    private boolean isWand(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return false;
        return it.getItemMeta().getPersistentDataContainer().has(kWand, PersistentDataType.BYTE);
    }

    public void giveWand(Player p) {
        p.getInventory().addItem(wand());
    }

    // ------------------------------------------------------------------ operaciones

    private int[][] bounds(Location a, Location b) {
        return new int[][]{{Math.min(a.getBlockX(), b.getBlockX()), Math.min(a.getBlockY(), b.getBlockY()), Math.min(a.getBlockZ(), b.getBlockZ())},
                {Math.max(a.getBlockX(), b.getBlockX()), Math.max(a.getBlockY(), b.getBlockY()), Math.max(a.getBlockZ(), b.getBlockZ())}};
    }

    /** Valida y aplica la selección del jugador a la habitación r (que puede ser nueva). Devuelve error o null. */
    private String applySelection(Player p, Guild g, Room r) {
        Location[] s = selection(p.getUniqueId());
        if (s[0] == null || s[1] == null) return "Marca dos puntos con la varita (/gremio habitaciones varita).";
        if (!s[0].getWorld().equals(s[1].getWorld())) return "Los dos puntos deben estar en el mismo mundo.";
        Territory t = plugin.territories.ownedBy(g);
        if (t == null) return "Tu gremio no tiene territorio.";
        int[][] b = bounds(s[0], s[1]);
        String w = s[0].getWorld().getName();
        if (!t.world.equals(w) || !t.contains(b[0][0], b[0][1], b[0][2]) || !t.contains(b[1][0], b[1][1], b[1][2]))
            return "La habitación debe estar completa dentro de tu territorio.";
        Room tmp = new Room("tmp");
        tmp.world = w;
        tmp.x1 = b[0][0];
        tmp.y1 = b[0][1];
        tmp.z1 = b[0][2];
        tmp.x2 = b[1][0];
        tmp.y2 = b[1][1];
        tmp.z2 = b[1][2];
        long max = plugin.getConfig().getLong("habitaciones.volumen-maximo", 4000);
        if (tmp.volume() > max) return "Demasiado grande (" + tmp.volume() + " bloques, máximo " + max + ").";
        for (Room o : rooms.values()) if (o != r && o.overlaps(tmp)) return "Se solapa con la habitación «" + o.name + "».";
        r.world = w;
        r.territoryId = t.id;
        r.guildId = g.id;
        r.x1 = tmp.x1;
        r.y1 = tmp.y1;
        r.z1 = tmp.z1;
        r.x2 = tmp.x2;
        r.y2 = tmp.y2;
        r.z2 = tmp.z2;
        if (r.spawn != null && !r.contains(w, (int) Math.floor(r.spawn[0]), (int) Math.floor(r.spawn[1]), (int) Math.floor(r.spawn[2]))) r.spawn = null;
        return null;
    }

    public String create(Player p, String name) {
        Guild g = plugin.guilds.of(p);
        if (g == null) return "No tienes gremio.";
        if (!enabled()) return "Las habitaciones están desactivadas.";
        if (!canManage(p, g)) return "Hace falta rango " + manageRank().display + " o superior.";
        name = name.trim();
        if (name.length() < 2 || name.length() > 24) return "El nombre debe tener entre 2 y 24 caracteres.";
        for (Room o : of(g)) if (o.name.equalsIgnoreCase(name)) return "Ya tienes una habitación con ese nombre.";
        if (of(g).size() >= plugin.getConfig().getInt("habitaciones.maximo-por-gremio", 8)) return "Tu gremio alcanzó el máximo de habitaciones.";
        Room r = new Room("h" + next);
        String err = applySelection(p, g, r);
        if (err != null) return err;
        next++;
        r.name = name;
        r.entry = rankCfg("habitaciones.defecto.entrar", Rank.RECLUTA, true);
        r.use = rankCfg("habitaciones.defecto.usar", Rank.RECLUTA, true);
        r.build = rankCfg("habitaciones.defecto.construir", Rank.RECLUTA, true);
        rooms.put(r.id, r);
        save();
        return null;
    }

    public String redefine(Player p, Room r) {
        Guild g = plugin.guilds.byId(r.guildId);
        if (g == null || !canManage(p, g)) return "No tienes permiso.";
        String err = applySelection(p, g, r);
        if (err == null) save();
        return err;
    }

    public String rename(Room r, String name) {
        name = name.trim();
        if (name.length() < 2 || name.length() > 24) return "El nombre debe tener entre 2 y 24 caracteres.";
        for (Room o : rooms.values()) if (o != r && o.guildId.equals(r.guildId) && o.name.equalsIgnoreCase(name)) return "Ya existe una habitación con ese nombre.";
        r.name = name;
        save();
        return null;
    }

    public void delete(Room r) {
        rooms.remove(r.id);
        save();
    }

    public String setOwner(Room r, UUID u) {
        Guild g = plugin.guilds.byId(r.guildId);
        if (u != null && (g == null || !g.members.containsKey(u))) return "Ese jugador no es miembro del gremio.";
        r.owner = u;
        save();
        return null;
    }

    public String setSpawn(Player p, Room r) {
        Location l = p.getLocation();
        if (!r.contains(l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ())) return "Debes estar dentro de la habitación.";
        r.spawn = new double[]{l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch()};
        save();
        return null;
    }

    public Rank cycle(Rank cur) {
        if (cur == null) return Rank.RECLUTA;
        if (cur == Rank.LIDER) return null;
        return cur.up();
    }

    public void setPlayer(Room r, UUID u, Access a) {
        if (a == null) r.players.remove(u);
        else r.players.put(u, a);
        save();
    }

    public String teleport(Player p, Room r) {
        if (level(p, r).ordinal() < Access.ENTRAR.ordinal()) return "No tienes acceso a esa habitación.";
        World w = Bukkit.getWorld(r.world);
        if (w == null) return "Mundo no cargado.";
        Location l;
        if (r.spawn != null) l = new Location(w, r.spawn[0], r.spawn[1], r.spawn[2], (float) r.spawn[3], (float) r.spawn[4]);
        else l = new Location(w, (r.x1 + r.x2) / 2.0 + 0.5, r.y1 + 1, (r.z1 + r.z2) / 2.0 + 0.5);
        p.teleport(l);
        return null;
    }

    public void onGuildGone(Guild g) {
        rooms.values().removeIf(r -> g.id.equals(r.guildId));
        save();
    }

    public void onTerritoryReleased(Territory t) {
        rooms.values().removeIf(r -> t.id.equals(r.territoryId));
        save();
    }

    // ------------------------------------------------------------------ eventos

    private boolean checkAct(Player p, Location l, Access need, boolean notify) {
        if (!enabled()) return true;
        Room r = at(l);
        if (r == null) return true;
        if (level(p, r).ordinal() >= need.ordinal()) return true;
        if (notify) Msg.actionbar(p, "§cHabitación «" + r.name + "»: necesitas permiso de §f" + need.label.substring(2).toLowerCase(Locale.ROOT) + "§c.");
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!checkAct(e.getPlayer(), e.getBlock().getLocation(), Access.CONSTRUIR, true)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!checkAct(e.getPlayer(), e.getBlock().getLocation(), Access.CONSTRUIR, true)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (!checkAct(e.getPlayer(), e.getBlock().getLocation(), Access.CONSTRUIR, true)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (!checkAct(e.getPlayer(), e.getBlock().getLocation(), Access.CONSTRUIR, true)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        if (isWand(e.getItem())) return;
        Block b = e.getClickedBlock();
        if (!b.getType().isInteractable()) return;
        if (!checkAct(e.getPlayer(), b.getLocation(), Access.USAR, true)) {
            e.setUseInteractedBlock(Event.Result.DENY);
            e.setUseItemInHand(Event.Result.DENY);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onUseEntity(PlayerInteractEntityEvent e) {
        if (!checkAct(e.getPlayer(), e.getRightClicked().getLocation(), Access.USAR, true)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null || !enabled()) return;
        Location f = e.getFrom(), t = e.getTo();
        if (f.getBlockX() == t.getBlockX() && f.getBlockY() == t.getBlockY() && f.getBlockZ() == t.getBlockZ()) return;
        Room r = at(t);
        if (r == null || r == at(f)) return;
        if (level(e.getPlayer(), r).ordinal() < Access.ENTRAR.ordinal()) {
            e.setCancelled(true);
            Msg.actionbar(e.getPlayer(), "§cHabitación privada: «" + r.name + "».");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (e.getTo() == null || !enabled()) return;
        Room r = at(e.getTo());
        if (r != null && level(e.getPlayer(), r).ordinal() < Access.ENTRAR.ordinal()) {
            e.setCancelled(true);
            Msg.actionbar(e.getPlayer(), "§cHabitación privada: «" + r.name + "».");
        }
    }

    /** Varita de selección. */
    @EventHandler(priority = EventPriority.LOW)
    public void onWand(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || e.getClickedBlock() == null) return;
        if (!isWand(e.getItem())) return;
        Player p = e.getPlayer();
        Guild g = plugin.guilds.of(p);
        e.setCancelled(true);
        if (g == null || !canManage(p, g)) {
            Msg.to(p, "§cSolo los rangos " + manageRank().display + "+ pueden usar la varita.");
            return;
        }
        Location[] s = selection(p.getUniqueId());
        Location l = e.getClickedBlock().getLocation();
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            s[0] = l;
            Msg.to(p, "§dPunto 1 §7→ §f" + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ());
        } else if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            s[1] = l;
            Msg.to(p, "§dPunto 2 §7→ §f" + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ());
        } else return;
        if (s[0] != null && s[1] != null && s[0].getWorld().equals(s[1].getWorld())) {
            int[][] b = bounds(s[0], s[1]);
            long v = (long) (b[1][0] - b[0][0] + 1) * (b[1][1] - b[0][1] + 1) * (b[1][2] - b[0][2] + 1);
            Msg.to(p, "§7Selección: §f" + v + " §7bloques. Abre §f/gremio habitaciones §7y pulsa «Crear».");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        sel.remove(e.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ ticks

    /** Expulsa a quien quede dentro sin permiso y dibuja la selección. */
    public void tick(boolean particles) {
        if (!enabled()) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            Room r = at(p.getLocation());
            if (r != null && !p.hasMetadata("NPC") && level(p, r).ordinal() < Access.ENTRAR.ordinal()) {
                Territory t = plugin.territories.get(r.territoryId);
                Location out = null;
                if (t != null && t.bukkitWorld() != null) {
                    out = t.spawn != null ? new Location(t.bukkitWorld(), t.spawn[0], t.spawn[1], t.spawn[2]) : t.center();
                }
                if (out == null) out = p.getWorld().getSpawnLocation();
                Msg.to(p, "§cHas sido sacado de la habitación privada «" + r.name + "».");
                p.teleport(out);
            }
            if (particles) {
                Location[] s = sel.get(p.getUniqueId());
                if (s != null && s[0] != null && s[1] != null && isWand(p.getInventory().getItemInMainHand()) && s[0].getWorld().equals(p.getWorld())) {
                    int[][] b = bounds(s[0], s[1]);
                    drawBox(p, b);
                }
            }
        }
    }

    private void drawBox(Player p, int[][] b) {
        double x1 = b[0][0], y1 = b[0][1], z1 = b[0][2], x2 = b[1][0] + 1, y2 = b[1][1] + 1, z2 = b[1][2] + 1;
        double[] xs = {x1, x2}, ys = {y1, y2}, zs = {z1, z2};
        int budget = 400;
        for (double y : ys) for (double z : zs) budget = line(p, x1, y, z, x2, y, z, budget);
        for (double x : xs) for (double z : zs) budget = line(p, x, y1, z, x, y2, z, budget);
        for (double x : xs) for (double y : ys) budget = line(p, x, y, z1, x, y, z2, budget);
    }

    private int line(Player p, double ax, double ay, double az, double bx, double by, double bz, int budget) {
        double len = Math.max(Math.abs(bx - ax), Math.max(Math.abs(by - ay), Math.abs(bz - az)));
        int n = (int) Math.max(1, Math.min(len, budget / 4));
        for (int i = 0; i <= n && budget > 0; i++, budget--) {
            double f = (double) i / n;
            p.spawnParticle(Particle.END_ROD, new Location(p.getWorld(), ax + (bx - ax) * f, ay + (by - ay) * f, az + (bz - az) * f), 1, 0, 0, 0, 0);
        }
        return budget;
    }

    // ------------------------------------------------------------------ persistencia

    private static String rk(Rank r) {
        return r == null ? "NADIE" : r.name();
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("siguiente", next);
        for (Room r : rooms.values()) {
            String k = "habitaciones." + r.id + ".";
            y.set(k + "gremio", r.guildId);
            y.set(k + "territorio", r.territoryId);
            y.set(k + "nombre", r.name);
            y.set(k + "mundo", r.world);
            y.set(k + "min", List.of(r.x1, r.y1, r.z1));
            y.set(k + "max", List.of(r.x2, r.y2, r.z2));
            y.set(k + "dueno", r.owner == null ? null : r.owner.toString());
            y.set(k + "entrar", rk(r.entry));
            y.set(k + "usar", rk(r.use));
            y.set(k + "construir", rk(r.build));
            y.set(k + "publico", r.publico);
            y.set(k + "aliados", r.aliados);
            if (r.spawn != null) y.set(k + "spawn", List.of(r.spawn[0], r.spawn[1], r.spawn[2], r.spawn[3], r.spawn[4]));
            for (var en : r.players.entrySet()) y.set(k + "jugadores." + en.getKey(), en.getValue().name());
        }
        try {
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudo guardar habitaciones.yml: " + ex.getMessage());
        }
    }

    private static Rank parseRank(String s) {
        if (s == null || s.equalsIgnoreCase("NADIE")) return null;
        try {
            return Rank.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Rank.RECLUTA;
        }
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        next = y.getInt("siguiente", 1);
        ConfigurationSection rs = y.getConfigurationSection("habitaciones");
        if (rs == null) return;
        for (String id : rs.getKeys(false)) {
            ConfigurationSection s = rs.getConfigurationSection(id);
            if (s == null) continue;
            List<Integer> mn = s.getIntegerList("min"), mx = s.getIntegerList("max");
            if (mn.size() < 3 || mx.size() < 3) continue;
            Room r = new Room(id);
            r.guildId = s.getString("gremio");
            r.territoryId = s.getString("territorio");
            r.name = s.getString("nombre", id);
            r.world = s.getString("mundo", "world");
            r.x1 = mn.get(0);
            r.y1 = mn.get(1);
            r.z1 = mn.get(2);
            r.x2 = mx.get(0);
            r.y2 = mx.get(1);
            r.z2 = mx.get(2);
            String o = s.getString("dueno");
            if (o != null) try {
                r.owner = UUID.fromString(o);
            } catch (IllegalArgumentException ignored) {
            }
            r.entry = parseRank(s.getString("entrar", "RECLUTA"));
            r.use = parseRank(s.getString("usar", "RECLUTA"));
            r.build = parseRank(s.getString("construir", "RECLUTA"));
            r.publico = s.getBoolean("publico");
            r.aliados = s.getBoolean("aliados");
            List<Double> sp = s.getDoubleList("spawn");
            if (sp.size() >= 5) r.spawn = new double[]{sp.get(0), sp.get(1), sp.get(2), sp.get(3), sp.get(4)};
            ConfigurationSection ps = s.getConfigurationSection("jugadores");
            if (ps != null) for (String u : ps.getKeys(false)) {
                try {
                    r.players.put(UUID.fromString(u), Access.valueOf(ps.getString(u, "DENEGADO")));
                } catch (IllegalArgumentException ignored) {
                }
            }
            rooms.put(id, r);
            try {
                next = Math.max(next, Integer.parseInt(id.substring(1)) + 1);
            } catch (NumberFormatException ignored) {
            }
        }
    }
}
