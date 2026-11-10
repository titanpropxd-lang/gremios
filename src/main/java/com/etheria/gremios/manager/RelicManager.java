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
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Reliquias: objetos únicos con efectos pasivos. Un gremio las custodia en su altar (dentro de su territorio);
 * si pierde un asedio, el atacante se las puede llevar. Las reliquias libres se reclaman en el mundo.
 * Las reliquias NO son ítems que se puedan llevar encima: viven en el altar (así no se pueden duplicar ni perder).
 */
public class RelicManager implements Listener {
    public static final class Relic {
        public final String id;
        public String name;
        public Material icon;
        public String desc = "";
        public final Map<String, Double> effects = new LinkedHashMap<>();
        public String holder;           // id de gremio, o null = libre
        public String world;
        public double x, y, z;
        public boolean placed;          // tiene posición en el mundo
        public long since;
        UUID stand;

        Relic(String id, String name, Material icon) {
            this.id = id;
            this.name = name;
            this.icon = icon;
        }
    }

    public record Altar(String world, double x, double y, double z) {
    }

    private static final List<String> KEYS = List.of("mantenimiento", "xp", "interes", "zona", "proteccion", "dmg-guerra", "def-terr", "cultivo",
            "minerales", "xp-orbes", "tributo", "botin-guerra", "costo", "investigacion");

    private final GremiosPlugin plugin;
    private final File file;
    private final NamespacedKey kRelic;
    private final Map<String, Relic> relics = new LinkedHashMap<>();
    private final Map<String, Altar> altars = new LinkedHashMap<>();

    public RelicManager(GremiosPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "reliquias.yml");
        this.kRelic = plugin.key("reliquia");
        boolean first = !file.exists();
        load();
        if (first && relics.isEmpty()) seed();
    }

    public static List<String> effectKeys() {
        return KEYS;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("reliquias.activo", true);
    }

    public Collection<Relic> all() {
        return relics.values();
    }

    public Relic get(String id) {
        return id == null ? null : relics.get(id.toLowerCase(Locale.ROOT));
    }

    public List<Relic> heldBy(Guild g) {
        List<Relic> out = new ArrayList<>();
        if (g != null) for (Relic r : relics.values()) if (g.id.equals(r.holder)) out.add(r);
        return out;
    }

    public int countHeld(Guild g) {
        return heldBy(g).size();
    }

    public double bonus(Guild g, String key) {
        if (g == null || !enabled()) return 0;
        double v = 0;
        for (Relic r : relics.values()) if (g.id.equals(r.holder)) v += r.effects.getOrDefault(key, 0.0);
        return v;
    }

    public Altar altarOf(Guild g) {
        return g == null ? null : altars.get(g.id);
    }

    public int maxPerGuild() {
        return plugin.getConfig().getInt("reliquias.max-por-gremio", 3);
    }

    // ------------------------------------------------------------------ administración

    private void seed() {
        create("corona_rota", "Corona Rota", Material.GOLDEN_HELMET, "Símbolo del reino caído. Atrae gloria al que la custodia.", Map.of("xp", 15.0));
        create("cetro_alba", "Cetro del Alba", Material.BLAZE_ROD, "Hace florecer las tierras bajo su luz.", Map.of("zona", 20.0, "cultivo", 10.0));
        create("escudo_ancestral", "Escudo Ancestral", Material.SHIELD, "Ningún muro cae mientras él esté en pie.", Map.of("proteccion", 25.0, "def-terr", 8.0));
        create("caliz_dorado", "Cáliz Dorado", Material.HONEY_BOTTLE, "El oro llama al oro.", Map.of("interes", 1.5, "mantenimiento", 10.0));
        save();
    }

    public String create(String id, String name, Material icon, String desc, Map<String, Double> eff) {
        id = id.toLowerCase(Locale.ROOT);
        if (!id.matches("[a-z0-9_\\-]{2,24}")) return "Id inválido (2-24: a-z, 0-9, _ -).";
        if (relics.containsKey(id)) return "Ya existe una reliquia con ese id.";
        Relic r = new Relic(id, name, icon == null ? Material.NETHER_STAR : icon);
        r.desc = desc == null ? "" : desc;
        r.effects.putAll(eff);
        relics.put(id, r);
        save();
        return null;
    }

    public boolean delete(String id) {
        Relic r = relics.remove(id == null ? "" : id.toLowerCase(Locale.ROOT));
        if (r == null) return false;
        removeStand(r);
        save();
        return true;
    }

    /** Pone la reliquia libre en el mundo (cualquiera con rango suficiente puede reclamarla). */
    public String place(Relic r, Location loc) {
        r.holder = null;
        r.world = loc.getWorld().getName();
        r.x = loc.getX();
        r.y = loc.getY();
        r.z = loc.getZ();
        r.placed = true;
        r.since = System.currentTimeMillis();
        removeStand(r);
        spawnStand(r);
        save();
        return null;
    }

    public String setEffect(Relic r, String key, double v) {
        if (!KEYS.contains(key)) return "Clave desconocida. Usa: " + String.join(", ", KEYS);
        if (v == 0) r.effects.remove(key);
        else r.effects.put(key, v);
        save();
        return null;
    }

    // ------------------------------------------------------------------ custodia

    /** Dónde se muestra una reliquia custodiada por g: su altar, o el spawn del territorio. */
    private Location displayFor(Guild g, int index) {
        Altar a = altars.get(g.id);
        World w = null;
        double x = 0, y = 0, z = 0;
        if (a != null) {
            w = Bukkit.getWorld(a.world());
            x = a.x();
            y = a.y();
            z = a.z();
        } else {
            Territory t = plugin.territories.ownedBy(g);
            if (t != null && t.bukkitWorld() != null) {
                w = t.bukkitWorld();
                double[] s = t.spawn != null ? t.spawn : new double[]{(t.minX + t.maxX) / 2.0, t.minY + 1, (t.minZ + t.maxZ) / 2.0};
                x = s[0];
                y = s[1];
                z = s[2];
            }
        }
        if (w == null) return null;
        double ang = index * (Math.PI / 2) ;
        return new Location(w, x + Math.cos(ang) * 1.6 * (index > 0 ? 1 : 0), y, z + Math.sin(ang) * 1.6 * (index > 0 ? 1 : 0));
    }

    private void relocate(Guild g) {
        int i = 0;
        for (Relic r : heldBy(g)) {
            Location l = displayFor(g, i++);
            if (l == null) continue;
            r.world = l.getWorld().getName();
            r.x = l.getX();
            r.y = l.getY();
            r.z = l.getZ();
            r.placed = true;
            removeStand(r);
            spawnStand(r);
        }
    }

    public String setAltar(Player p, Guild g) {
        Territory t = plugin.territories.ownedBy(g);
        if (t == null) return "Tu gremio no tiene territorio.";
        Location l = p.getLocation();
        Territory here = plugin.territories.at(l);
        if (here == null || !here.id.equals(t.id)) return "Debes estar dentro de tu territorio para fijar el altar.";
        altars.put(g.id, new Altar(l.getWorld().getName(), l.getX(), l.getY(), l.getZ()));
        relocate(g);
        save();
        return null;
    }

    public String claim(Player p, Relic r) {
        if (!enabled()) return "Las reliquias están desactivadas.";
        if (r.holder != null) return "Esta reliquia ya está custodiada por otro gremio.";
        Guild g = plugin.guilds.of(p);
        if (g == null) return "Necesitas un gremio para reclamar una reliquia.";
        Rank need = Rank.OFICIAL;
        try {
            need = Rank.valueOf(plugin.getConfig().getString("reliquias.rango-reclamar", "OFICIAL").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
        }
        Rank mine = g.rankOf(p.getUniqueId());
        if (mine == null || !mine.atLeast(need)) return "Hace falta rango " + need.display + " o superior.";
        if (plugin.territories.ownedBy(g) == null) return "Tu gremio necesita un territorio donde custodiarla.";
        if (countHeld(g) >= maxPerGuild()) return "Tu gremio ya custodia el máximo de reliquias (" + maxPerGuild() + ").";
        give(r, g);
        broadcast("§d✦ §f" + g.name + " §dha reclamado la reliquia §f" + r.name + "§d.");
        plugin.discord.announce("gremios", g.name + " ha reclamado la reliquia " + r.name + ".");
        return null;
    }

    private void give(Relic r, Guild g) {
        r.holder = g.id;
        r.since = System.currentTimeMillis();
        relocate(g);
        save();
    }

    private void free(Relic r) {
        r.holder = null;
        r.since = System.currentTimeMillis();
        removeStand(r);
        if (r.placed) spawnStand(r);
        save();
    }

    /** Al perder un asedio, el atacante puede llevarse reliquias del defensor. */
    public void onRaidResult(Guild a, Guild d, boolean attackerWon) {
        if (!enabled() || !attackerWon || d == null) return;
        List<Relic> held = heldBy(d);
        if (held.isEmpty()) return;
        int n = plugin.getConfig().getInt("reliquias.robo-por-raid", 1);
        List<Relic> pool = new ArrayList<>(held);
        java.util.Collections.shuffle(pool);
        int take = n < 0 ? pool.size() : Math.min(n, pool.size());
        for (int i = 0; i < take; i++) {
            Relic r = pool.get(i);
            if (a != null && plugin.territories.ownedBy(a) != null && countHeld(a) < maxPerGuild()) {
                give(r, a);
                broadcast("§c⚔ §f" + a.name + " §cle ha robado la reliquia §d" + r.name + " §ca §f" + d.name + "§c.");
                plugin.discord.announce("gremios", a.name + " robó la reliquia " + r.name + " a " + d.name + ".");
            } else {
                free(r);
                broadcast("§c⚔ La reliquia §d" + r.name + " §cha caído de las manos de §f" + d.name + " §cy queda libre.");
                plugin.discord.announce("gremios", "La reliquia " + r.name + " ha quedado libre tras el asedio a " + d.name + ".");
            }
        }
        if (d != null) relocate(d);
    }

    public void onGuildGone(Guild g) {
        for (Relic r : heldBy(g)) free(r);
        altars.remove(g.id);
        save();
    }

    private void broadcast(String msg) {
        for (Player p : Bukkit.getOnlinePlayers()) Msg.raw(p, msg);
    }

    // ------------------------------------------------------------------ entidades (soporte visual)

    public ItemStack iconItem(Relic r) {
        ItemStack it = new ItemStack(r.icon);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName("§d✦ " + r.name);
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        m.addEnchant(Enchantment.UNBREAKING, 1, true);
        it.setItemMeta(m);
        return it;
    }

    private void spawnStand(Relic r) {
        if (!r.placed || r.world == null) return;
        World w = Bukkit.getWorld(r.world);
        if (w == null) return;
        Location l = new Location(w, r.x, r.y, r.z);
        if (!w.isChunkLoaded(l.getBlockX() >> 4, l.getBlockZ() >> 4)) return;
        ArmorStand as = w.spawn(l.clone().add(0, -0.3, 0), ArmorStand.class);
        as.setVisible(false);
        as.setSmall(true);
        as.setGravity(false);
        as.setInvulnerable(true);
        as.setPersistent(false);
        as.setSilent(true);
        as.setGlowing(true);
        as.setBasePlate(false);
        as.setHelmet(iconItem(r));
        as.setCustomName("§d✦ §f" + r.name);
        as.setCustomNameVisible(true);
        as.getPersistentDataContainer().set(kRelic, PersistentDataType.STRING, r.id);
        r.stand = as.getUniqueId();
    }

    private void removeStand(Relic r) {
        if (r.stand != null) {
            Entity e = Bukkit.getEntity(r.stand);
            if (e != null) e.remove();
            r.stand = null;
        }
    }

    /** Elimina soportes huérfanos y recrea los que falten; también hace brillar el altar. */
    public void tick() {
        if (!enabled()) return;
        for (Relic r : relics.values()) {
            if (!r.placed || r.world == null) continue;
            World w = Bukkit.getWorld(r.world);
            if (w == null || !w.isChunkLoaded((int) Math.floor(r.x) >> 4, (int) Math.floor(r.z) >> 4)) {
                r.stand = null;
                continue;
            }
            Entity e = r.stand == null ? null : Bukkit.getEntity(r.stand);
            if (e == null || e.isDead()) {
                r.stand = null;
                spawnStand(r);
            }
            w.spawnParticle(Particle.END_ROD, new Location(w, r.x, r.y + 0.8, r.z), 2, 0.25, 0.3, 0.25, 0.01);
        }
    }

    public void cleanup() {
        for (Relic r : relics.values()) removeStand(r);
        for (World w : Bukkit.getWorlds()) {
            for (ArmorStand as : w.getEntitiesByClass(ArmorStand.class)) {
                if (as.getPersistentDataContainer().has(kRelic, PersistentDataType.STRING)) as.remove();
            }
        }
    }

    private Relic relicOf(Entity e) {
        if (!(e instanceof ArmorStand)) return null;
        String id = e.getPersistentDataContainer().get(kRelic, PersistentDataType.STRING);
        return id == null ? null : relics.get(id);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractAtEntityEvent e) {
        Relic r = relicOf(e.getRightClicked());
        if (r == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (r.holder == null) {
            String err = claim(p, r);
            Msg.to(p, err == null ? "§dHas reclamado §f" + r.name + "§d para tu gremio." : "§c" + err);
        } else {
            Guild h = plugin.guilds.byId(r.holder);
            Msg.to(p, "§d✦ §f" + r.name + " §7— custodiada por §6" + (h == null ? "?" : h.name) + "§7. §8Para llevártela hay que ganar un asedio.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent e) {
        if (relicOf(e.getEntity()) != null) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ persistencia

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Relic r : relics.values()) {
            String k = "reliquias." + r.id + ".";
            y.set(k + "nombre", r.name);
            y.set(k + "material", r.icon.name());
            y.set(k + "descripcion", r.desc);
            for (var en : r.effects.entrySet()) y.set(k + "efectos." + en.getKey(), en.getValue());
            y.set(k + "gremio", r.holder);
            y.set(k + "colocada", r.placed);
            y.set(k + "mundo", r.world);
            y.set(k + "x", r.x);
            y.set(k + "y", r.y);
            y.set(k + "z", r.z);
            y.set(k + "desde", r.since);
        }
        for (var en : altars.entrySet()) {
            String k = "altares." + en.getKey() + ".";
            y.set(k + "mundo", en.getValue().world());
            y.set(k + "x", en.getValue().x());
            y.set(k + "y", en.getValue().y());
            y.set(k + "z", en.getValue().z());
        }
        try {
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudo guardar reliquias.yml: " + ex.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection rs = y.getConfigurationSection("reliquias");
        if (rs != null) for (String id : rs.getKeys(false)) {
            ConfigurationSection s = rs.getConfigurationSection(id);
            if (s == null) continue;
            Material m = Material.matchMaterial(s.getString("material", "NETHER_STAR"));
            Relic r = new Relic(id, s.getString("nombre", id), m == null ? Material.NETHER_STAR : m);
            r.desc = s.getString("descripcion", "");
            ConfigurationSection ef = s.getConfigurationSection("efectos");
            if (ef != null) for (String k : ef.getKeys(false)) r.effects.put(k, ef.getDouble(k));
            r.holder = s.getString("gremio");
            r.placed = s.getBoolean("colocada");
            r.world = s.getString("mundo");
            r.x = s.getDouble("x");
            r.y = s.getDouble("y");
            r.z = s.getDouble("z");
            r.since = s.getLong("desde");
            relics.put(id, r);
        }
        ConfigurationSection as = y.getConfigurationSection("altares");
        if (as != null) for (String gid : as.getKeys(false)) {
            ConfigurationSection s = as.getConfigurationSection(gid);
            if (s != null) altars.put(gid, new Altar(s.getString("mundo", "world"), s.getDouble("x"), s.getDouble("y"), s.getDouble("z")));
        }
    }

    /** Efectos en texto legible para menús. */
    public static List<String> effectLines(Relic r) {
        List<String> out = new ArrayList<>();
        for (var en : r.effects.entrySet()) {
            String v = en.getValue() == Math.rint(en.getValue()) ? String.valueOf(en.getValue().intValue()) : String.valueOf(en.getValue());
            out.add("§a▸ §f" + en.getKey() + " §7+" + v);
        }
        if (out.isEmpty()) out.add("§8(sin efectos)");
        return out;
    }
}
