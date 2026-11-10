package com.etheria.gremios.tech;

import com.etheria.gremios.GremiosPlugin;
import com.etheria.gremios.model.Guild;
import com.etheria.gremios.model.Rank;
import com.etheria.gremios.model.Territory;
import com.etheria.gremios.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.block.data.Ageable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Árbol de tecnologías de gremio: se investigan con dinero del banco y tiempo real.
 * Cada tecnología da bonificaciones reales (miembros, mantenimiento, XP, interés, zonas, defensa, cultivos...).
 * La rama Slimefun es opcional: solo existe si está activada en config y el plugin Slimefun está cargado.
 */
public class TechManager implements Listener {
    private static final Map<String, Double> CAPS = Map.ofEntries(
            Map.entry("mantenimiento", 60.0), Map.entry("xp", 100.0), Map.entry("interes", 5.0), Map.entry("zona", 150.0),
            Map.entry("proteccion", 100.0), Map.entry("dmg-guerra", 40.0), Map.entry("def-terr", 40.0), Map.entry("cultivo", 80.0),
            Map.entry("minerales", 40.0), Map.entry("xp-orbes", 100.0), Map.entry("slots", 2.0), Map.entry("tributo", 50.0),
            Map.entry("botin-guerra", 100.0), Map.entry("costo", 40.0), Map.entry("investigacion", 50.0));

    private static final class State {
        final Set<String> done = new HashSet<>();
        final Map<String, Long> doing = new LinkedHashMap<>(); // id -> fin (ms)
        Map<String, Double> cache;
        Map<String, Integer> buffs;
        long lastInterest;
    }

    private final GremiosPlugin plugin;
    private final File file;
    private final Map<String, State> states = new HashMap<>();
    private final Set<UUID> synced = new HashSet<>();
    private final Random rnd = new Random();
    private boolean dirty;

    public TechManager(GremiosPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "tecnologias.yml");
        load();
        validate();
    }

    private void validate() {
        for (Tech t : TechTree.ALL.values())
            for (String r : t.reqs)
                if (!TechTree.ALL.containsKey(r)) plugin.getLogger().warning("Tecnología " + t.id + ": requisito desconocido " + r);
    }

    // ------------------------------------------------------------------ datos
    public boolean enabled() {
        return plugin.getConfig().getBoolean("tecnologias.activo", true);
    }

    public boolean slimefunOn() {
        if (!plugin.getConfig().getBoolean("slimefun.activo", false)) return false;
        return Bukkit.getPluginManager().isPluginEnabled(plugin.getConfig().getString("slimefun.plugin", "Slimefun"));
    }

    /** ¿Está disponible esta tecnología en este servidor? */
    public boolean visible(Tech t) {
        return t.branch != Tech.Branch.SLIMEFUN || slimefunOn();
    }

    public List<Tech> branch(Tech.Branch b) {
        List<Tech> out = new ArrayList<>();
        for (Tech t : TechTree.ALL.values()) if (t.branch == b && visible(t)) out.add(t);
        out.sort((x, y) -> x.tier != y.tier ? Integer.compare(x.tier, y.tier) : x.name.compareTo(y.name));
        return out;
    }

    public int totalVisible() {
        int n = 0;
        for (Tech t : TechTree.ALL.values()) if (visible(t)) n++;
        return n;
    }

    private State st(Guild g) {
        return states.computeIfAbsent(g.id, k -> new State());
    }

    public Set<String> done(Guild g) {
        return st(g).done;
    }

    public boolean has(Guild g, String id) {
        return st(g).done.contains(id);
    }

    public Map<String, Long> doing(Guild g) {
        return st(g).doing;
    }

    public int count(Guild g) {
        int n = 0;
        for (String id : st(g).done) {
            Tech t = TechTree.ALL.get(id);
            if (t != null && visible(t)) n++;
        }
        return n;
    }

    // ------------------------------------------------------------------ bonificaciones
    private void build(State s) {
        Map<String, Double> sum = new HashMap<>();
        Map<String, Integer> buffs = new HashMap<>();
        for (String id : s.done) {
            Tech t = TechTree.ALL.get(id);
            if (t == null || !visible(t)) continue;
            for (var e : t.effects.entrySet()) {
                if (e.getKey().startsWith("buff:")) buffs.merge(e.getKey().substring(5), (int) Math.round(e.getValue()), Math::max);
                else sum.merge(e.getKey(), e.getValue(), Double::sum);
            }
        }
        for (var e : CAPS.entrySet()) sum.computeIfPresent(e.getKey(), (k, v) -> Math.min(v, e.getValue()));
        s.cache = sum;
        s.buffs = buffs;
    }

    public double bonus(Guild g, String key) {
        if (g == null) return 0;
        double rel = plugin.relics == null ? 0 : plugin.relics.bonus(g, key);
        if (!enabled()) return rel;
        State s = st(g);
        if (s.cache == null) build(s);
        return s.cache.getOrDefault(key, 0.0) + rel;
    }

    public int bonusInt(Guild g, String key) {
        return (int) Math.round(bonus(g, key));
    }

    // ------------------------------------------------------------------ investigar
    private List<Long> longs(String path, List<Long> def) {
        List<Long> l = plugin.getConfig().getLongList(path);
        return l.isEmpty() ? def : l;
    }

    public long cost(Guild g, Tech t) {
        List<Long> l = longs("tecnologias.costo-por-tier", List.of(12L, 30L, 70L, 140L, 280L, 560L));
        double base = l.get(Math.min(l.size(), t.tier) - 1) * plugin.getConfig().getDouble("tecnologias.multiplicador-costo", 1.0);
        return Math.max(1, Math.round(base * (1 - bonus(g, "costo") / 100.0)));
    }

    public long millis(Guild g, Tech t) {
        List<Long> l = longs("tecnologias.minutos-por-tier", List.of(10L, 30L, 60L, 120L, 240L, 420L));
        double base = l.get(Math.min(l.size(), t.tier) - 1) * plugin.getConfig().getDouble("tecnologias.multiplicador-tiempo", 1.0) * 60_000.0;
        return Math.max(10_000L, Math.round(base * (1 - bonus(g, "investigacion") / 100.0)));
    }

    public int minGuildLevel(Tech t) {
        List<Long> l = longs("tecnologias.nivel-minimo-por-tier", List.of(1L, 2L, 4L, 7L, 11L, 15L));
        return l.get(Math.min(l.size(), t.tier) - 1).intValue();
    }

    public int slots(Guild g) {
        return 1 + bonusInt(g, "slots");
    }

    /** Motivo por el que no se puede investigar ahora (null = se puede). */
    public String blocked(Guild g, Tech t) {
        if (!enabled()) return "Las tecnologías están desactivadas.";
        if (!visible(t)) return "La rama Slimefun no está activada en este servidor.";
        State s = st(g);
        if (s.done.contains(t.id)) return "Ya la tenéis investigada.";
        if (s.doing.containsKey(t.id)) return "Ya la estáis investigando.";
        if (g.level < minGuildLevel(t)) return "Necesitas gremio de nivel " + minGuildLevel(t) + ".";
        for (String r : t.reqs) if (!s.done.contains(r)) return "Falta: " + TechTree.ALL.get(r).name + ".";
        if (s.doing.size() >= slots(g)) return "Ya estáis investigando " + s.doing.size() + " (máx. " + slots(g) + " a la vez).";
        if (g.bank < cost(g, t)) return "El banco necesita " + plugin.currency.fmt(cost(g, t)) + ".";
        return null;
    }

    public String start(Player p, Guild g, Tech t) {
        Rank need = Rank.parse(plugin.getConfig().getString("tecnologias.rango-minimo", "MARISCAL"), Rank.MARISCAL);
        Rank mine = g.rankOf(p.getUniqueId());
        if (mine == null || !mine.atLeast(need)) return "Solo " + need.display + " o superior puede investigar.";
        String b = blocked(g, t);
        if (b != null) return b;
        long c = cost(g, t);
        g.bank -= c;
        g.log("§6-" + Msg.num(c) + " §7investigar " + t.name);
        st(g).doing.put(t.id, System.currentTimeMillis() + millis(g, t));
        dirty = true;
        plugin.audit.log(g, p, "TECNOLOGIA_INICIO", t.name + " (coste " + c + ")");
        plugin.guilds.broadcast(g, "§b🔬 §7Se ha empezado a investigar §f" + t.name + " §7(" + Msg.dur(millis(g, t)) + ").");
        plugin.guilds.save();
        save();
        return null;
    }

    public String cancel(Player p, Guild g, Tech t) {
        Rank need = Rank.parse(plugin.getConfig().getString("tecnologias.rango-minimo", "MARISCAL"), Rank.MARISCAL);
        Rank mine = g.rankOf(p.getUniqueId());
        if (mine == null || !mine.atLeast(need)) return "Solo " + need.display + " o superior puede cancelar.";
        if (st(g).doing.remove(t.id) == null) return "Esa tecnología no se está investigando.";
        long back = cost(g, t) / 2;
        g.bank += back;
        g.log("§a+" + Msg.num(back) + " §7cancelar investigación");
        plugin.guilds.broadcast(g, "§c🔬 Investigación de §f" + t.name + " §ccancelada (se devuelve el 50%).");
        plugin.guilds.save();
        save();
        return null;
    }

    /** Admin: da una tecnología sin coste. */
    public boolean grant(Guild g, String id) {
        Tech t = TechTree.ALL.get(id);
        if (t == null) return false;
        State s = st(g);
        s.doing.remove(id);
        if (s.done.add(id)) finish(g, t, false);
        s.cache = null;
        dirty = true;
        save();
        return true;
    }

    public boolean revoke(Guild g, String id) {
        State s = st(g);
        boolean r = s.done.remove(id);
        s.doing.remove(id);
        s.cache = null;
        dirty = true;
        save();
        return r;
    }

    public void reset(Guild g) {
        states.remove(g.id);
        dirty = true;
        save();
    }

    public void onGuildGone(Guild g) {
        states.remove(g.id);
        save();
    }

    private void finish(Guild g, Tech t, boolean announce) {
        State s = st(g);
        s.cache = null;
        if (announce) {
            for (Player p : plugin.guilds.onlineMembers(g)) {
                Msg.to(p, "§b🔬 §a¡Investigación completada: §f" + t.name + "§a!");
                for (String l : t.effectLines()) p.sendMessage("   §8• §7" + l);
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            }
            plugin.discord.announce("gremios", g.name + " ha investigado " + t.name + ".");
            plugin.audit.log(g, "sistema", "TECNOLOGIA_FIN", t.name);
        }
        syncSlimefun(g, t, null);
    }

    // ------------------------------------------------------------------ Slimefun
    @SuppressWarnings("unchecked")
    private void syncSlimefun(Guild g, Tech t, Player only) {
        if (t.branch != Tech.Branch.SLIMEFUN || !slimefunOn()) return;
        List<String> keys = plugin.getConfig().getStringList("slimefun.investigaciones." + t.id);
        if (keys.isEmpty()) return;
        String cmd = plugin.getConfig().getString("slimefun.comando", "sf research {jugador} {investigacion}");
        List<Player> targets = only != null ? List.of(only) : plugin.guilds.onlineMembers(g);
        for (Player p : targets) for (String k : keys) {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{jugador}", p.getName()).replace("{investigacion}", k));
            } catch (Exception e) {
                plugin.getLogger().warning("Slimefun: no se pudo ejecutar el comando para " + p.getName() + ": " + e.getMessage());
            }
        }
    }

    /** Vuelve a aplicar las investigaciones de Slimefun del gremio a un jugador (al entrar o al unirse al gremio). */
    public void syncPlayer(Player p) {
        if (!slimefunOn()) return;
        Guild g = plugin.guilds.of(p);
        if (g == null) return;
        for (String id : st(g).done) {
            Tech t = TechTree.ALL.get(id);
            if (t != null) syncSlimefun(g, t, p);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!slimefunOn() || !synced.add(e.getPlayer().getUniqueId())) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (e.getPlayer().isOnline()) syncPlayer(e.getPlayer());
        }, 100L);
    }

    // ------------------------------------------------------------------ bucle (cada 5 s)
    public void tick() {
        if (!enabled()) return;
        long now = System.currentTimeMillis();
        long interestEvery = plugin.getConfig().getLong("tecnologias.interes-horas", 12) * 3_600_000L;
        for (Guild g : new ArrayList<>(plugin.guilds.all())) {
            State s = st(g);
            for (var it : new ArrayList<>(s.doing.entrySet())) {
                if (it.getValue() > now) continue;
                s.doing.remove(it.getKey());
                Tech t = TechTree.ALL.get(it.getKey());
                if (t == null) continue;
                s.done.add(t.id);
                dirty = true;
                finish(g, t, true);
            }
            if (s.cache == null) build(s);
            // interés del banco
            double pct = bonus(g, "interes");
            if (pct > 0 && g.bank > 0) {
                if (s.lastInterest == 0) s.lastInterest = now;
                if (now - s.lastInterest >= interestEvery) {
                    s.lastInterest = now;
                    long cap = plugin.getConfig().getLong("tecnologias.interes-tope", 200);
                    long gain = Math.min(cap, Math.round(g.bank * pct / 100.0));
                    if (gain > 0) {
                        g.bank += gain;
                        g.log("§a+" + Msg.num(gain) + " §7interés");
                        plugin.guilds.broadcast(g, "§6💰 §7Vuestro banco ha generado §e" + plugin.currency.fmt(gain) + " §7de interés.");
                        dirty = true;
                    }
                }
            }
            // efectos en el territorio propio
            if (!s.buffs.isEmpty()) {
                Territory own = plugin.territories.ownedBy(g);
                if (own != null) for (Player p : plugin.guilds.onlineMembers(g)) {
                    if (!p.getWorld().getName().equals(own.world) || !own.contains(p.getLocation().getBlockX(), p.getLocation().getBlockY(), p.getLocation().getBlockZ())) continue;
                    for (var b : s.buffs.entrySet()) {
                        PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(b.getKey().toLowerCase()));
                        if (type == null) continue;
                        p.addPotionEffect(new PotionEffect(type, 160, Math.max(0, b.getValue() - 1), true, false, true));
                    }
                }
            }
        }
        if (dirty) save();
    }

    // ------------------------------------------------------------------ eventos
    private Guild ownerAt(Location l) {
        Territory t = plugin.territories.at(l);
        return t == null || t.ownerGuildId == null ? null : plugin.guilds.byId(t.ownerGuildId);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!enabled() || !(e.getEntity() instanceof Player victim)) return;
        Guild vg = plugin.guilds.of(victim);
        double mult = 1.0;
        if (vg != null) {
            double def = bonus(vg, "def-terr");
            if (def > 0) {
                Guild o = ownerAt(victim.getLocation());
                if (o == vg) mult *= 1 - def / 100.0;
            }
        }
        if (e instanceof EntityDamageByEntityEvent be) {
            Player att = be.getDamager() instanceof Player pp ? pp
                    : be.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player ps ? ps : null;
            if (att != null && plugin.diplo != null) {
                Guild ag = plugin.guilds.of(att);
                if (ag != null && vg != null && plugin.diplo.atWar(ag, vg)) mult *= 1 + bonus(ag, "dmg-guerra") / 100.0;
            }
        }
        if (mult != 1.0) e.setDamage(e.getDamage() * mult);
    }

    @EventHandler(ignoreCancelled = true)
    public void onGrow(BlockGrowEvent e) {
        if (!enabled()) return;
        Guild g = ownerAt(e.getBlock().getLocation());
        if (g == null) return;
        double chance = bonus(g, "cultivo");
        if (chance <= 0 || rnd.nextDouble() * 100 >= chance) return;
        if (e.getNewState().getBlockData() instanceof Ageable ag && ag.getAge() < ag.getMaximumAge()) {
            ag.setAge(ag.getAge() + 1);
            e.getNewState().setBlockData(ag);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onOre(BlockDropItemEvent e) {
        if (!enabled()) return;
        String n = e.getBlockState().getType().name();
        if (!n.endsWith("_ORE") && !n.equals("ANCIENT_DEBRIS")) return;
        Guild g = plugin.guilds.of(e.getPlayer());
        if (g == null || ownerAt(e.getBlock().getLocation()) != g) return;
        double chance = bonus(g, "minerales");
        if (chance <= 0 || rnd.nextDouble() * 100 >= chance) return;
        for (var item : new ArrayList<>(e.getItems())) {
            if (item.getItemStack().getType().name().endsWith("_ORE")) continue; // toque de seda: no se duplica
            e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation().add(0.5, 0.5, 0.5), item.getItemStack().clone());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onXp(PlayerExpChangeEvent e) {
        if (!enabled() || e.getAmount() <= 0) return;
        Guild g = plugin.guilds.of(e.getPlayer());
        if (g == null) return;
        double b = bonus(g, "xp-orbes");
        if (b <= 0 || ownerAt(e.getPlayer().getLocation()) != g) return;
        e.setAmount((int) Math.round(e.getAmount() * (1 + b / 100.0)));
    }

    // ------------------------------------------------------------------ persistencia
    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = y.getConfigurationSection("gremios");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection c = root.getConfigurationSection(id);
            if (c == null) continue;
            State s = new State();
            s.done.addAll(c.getStringList("hechas"));
            s.lastInterest = c.getLong("interes", 0);
            ConfigurationSection d = c.getConfigurationSection("investigando");
            if (d != null) for (String k : d.getKeys(false)) s.doing.put(k, d.getLong(k));
            states.put(id, s);
        }
    }

    public void save() {
        dirty = false;
        YamlConfiguration y = new YamlConfiguration();
        for (var e : states.entrySet()) {
            State s = e.getValue();
            if (s.done.isEmpty() && s.doing.isEmpty()) continue;
            String p = "gremios." + e.getKey();
            y.set(p + ".hechas", new ArrayList<>(s.done));
            y.set(p + ".interes", s.lastInterest);
            for (var d : s.doing.entrySet()) y.set(p + ".investigando." + d.getKey(), d.getValue());
        }
        try {
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudo guardar tecnologias.yml: " + ex.getMessage());
        }
    }
}
