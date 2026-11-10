package com.etheria.gremios.manager;

import com.etheria.gremios.GremiosPlugin;
import com.etheria.gremios.model.Guild;
import com.etheria.gremios.model.Rank;
import com.etheria.gremios.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Guerras, treguas, paz, rendición y vasallaje entre gremios.
 * - Guerra: se declara con coste; se gana por puntos (asedios ganados y bajas enemigas) o por ventaja al acabar el plazo.
 * - Tregua: tras toda guerra, nadie puede atacar al otro durante unos días.
 * - Vasallaje: un gremio pequeño se pone bajo la protección de uno mayor y le paga tributo.
 */
public class DiplomacyManager implements Listener {
    public static final class War {
        public String a, b;          // a = quien declaró
        public long start, end;
        public int scoreA, scoreB;
        public String reason = "";

        public boolean has(Guild g) {
            return g.id.equals(a) || g.id.equals(b);
        }

        public String other(Guild g) {
            return g.id.equals(a) ? b : a;
        }

        public int score(Guild g) {
            return g.id.equals(a) ? scoreA : scoreB;
        }
    }

    public static final class Vassal {
        public String lord;
        public long since;
        public long nextTribute;
    }

    public static final class Proposal {
        public final String type; // PAZ, VASALLAJE (el señor ofrece), PROTECCION (el vasallo pide)
        public final String from, to;
        public final long expires;

        Proposal(String type, String from, String to, long expires) {
            this.type = type;
            this.from = from;
            this.to = to;
            this.expires = expires;
        }
    }

    private final GremiosPlugin plugin;
    private final File file;
    private final List<War> wars = new ArrayList<>();
    private final Map<String, Long> truces = new HashMap<>();
    private final Map<String, Vassal> vassals = new LinkedHashMap<>(); // id vasallo -> datos
    private final List<Proposal> proposals = new ArrayList<>();
    private final Map<String, Long> lastDeclare = new HashMap<>();
    private final Map<String, Long> killCd = new HashMap<>();

    public DiplomacyManager(GremiosPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "diplomacia.yml");
        load();
    }

    // ------------------------------------------------------------------ consultas
    public boolean enabled() {
        return plugin.getConfig().getBoolean("diplomacia.activo", true);
    }

    private static String pair(String x, String y) {
        return x.compareTo(y) < 0 ? x + "~" + y : y + "~" + x;
    }

    public War warBetween(Guild a, Guild b) {
        if (a == null || b == null) return null;
        for (War w : wars) if (w.has(a) && w.has(b)) return w;
        return null;
    }

    public boolean atWar(Guild a, Guild b) {
        return warBetween(a, b) != null;
    }

    public List<War> warsOf(Guild g) {
        List<War> out = new ArrayList<>();
        for (War w : wars) if (w.has(g)) out.add(w);
        return out;
    }

    public List<War> allWars() {
        return wars;
    }

    public long truceLeft(Guild a, Guild b) {
        Long t = truces.get(pair(a.id, b.id));
        return t == null ? 0 : Math.max(0, t - System.currentTimeMillis());
    }

    public Map<String, Long> truces() {
        return truces;
    }

    public Guild lordOf(Guild g) {
        Vassal v = vassals.get(g.id);
        return v == null ? null : plugin.guilds.byId(v.lord);
    }

    public Vassal vassalData(Guild g) {
        return vassals.get(g.id);
    }

    public List<Guild> vassalsOf(Guild lord) {
        List<Guild> out = new ArrayList<>();
        for (var e : vassals.entrySet()) if (e.getValue().lord.equals(lord.id)) {
            Guild v = plugin.guilds.byId(e.getKey());
            if (v != null) out.add(v);
        }
        return out;
    }

    /** ¿Señor y vasallo (en cualquier sentido)? */
    public boolean linked(Guild a, Guild b) {
        if (a == null || b == null) return false;
        Vassal va = vassals.get(a.id), vb = vassals.get(b.id);
        return (va != null && va.lord.equals(b.id)) || (vb != null && vb.lord.equals(a.id));
    }

    public int maxWars(Guild g) {
        return plugin.getConfig().getInt("diplomacia.guerras-max", 2) + plugin.tech.bonusInt(g, "guerras");
    }

    public int maxVassals(Guild g) {
        return plugin.getConfig().getInt("diplomacia.vasallos-max", 0) + plugin.tech.bonusInt(g, "vasallos");
    }

    private Rank need() {
        return Rank.parse(plugin.getConfig().getString("diplomacia.rango-minimo", "MARISCAL"), Rank.MARISCAL);
    }

    private String rankErr(Player p, Guild g) {
        Rank mine = g.rankOf(p.getUniqueId());
        return mine == null || !mine.atLeast(need()) ? "Solo " + need().display + " o superior puede hacer diplomacia." : null;
    }

    private long days(String path, double def) {
        return (long) (plugin.getConfig().getDouble(path, def) * 86_400_000L);
    }

    // ------------------------------------------------------------------ comprobaciones de asedio
    /** Se llama al declarar un asedio. null = permitido. */
    public String raidCheck(Guild a, Guild d) {
        if (!enabled()) return null;
        long t = truceLeft(a, d);
        if (t > 0) return "Hay una tregua con " + d.name + " durante " + Msg.dur(t) + ".";
        if (linked(a, d)) return "No puedes atacar a tu " + (lordOf(a) == d ? "señor" : "vasallo") + ".";
        Guild lord = lordOf(d);
        if (lord != null && lord != a && !atWar(a, lord)) {
            return d.name + " es vasallo de " + lord.name + ": solo puedes atacarlo estando en guerra con su señor.";
        }
        if (plugin.getConfig().getBoolean("diplomacia.guerra-requerida", false) && !atWar(a, d)) {
            return "Necesitas declarar guerra a " + d.name + " antes (/gremio diplomacia).";
        }
        return null;
    }

    /** Resultado de un asedio: suma puntos de guerra si hay guerra entre ambos. */
    public void onRaidResult(Guild a, Guild d, boolean attackerWon) {
        if (!enabled() || a == null || d == null) return;
        War w = warBetween(a, d);
        if (w == null) return;
        int pts = plugin.getConfig().getInt("diplomacia.puntos-asedio", 8);
        add(w, attackerWon ? a : d, pts, attackerWon ? "ganó el asedio" : "defendió con éxito");
    }

    private void add(War w, Guild g, int pts, String why) {
        if (g.id.equals(w.a)) w.scoreA += pts;
        else w.scoreB += pts;
        Guild o = plugin.guilds.byId(w.other(g));
        plugin.guilds.broadcast(g, "§c⚔ §7+" + pts + " puntos de guerra (" + why + "): §a" + w.score(g) + "§7/§f" + target());
        if (o != null) plugin.guilds.broadcast(o, "§c⚔ §7" + g.name + " suma " + pts + " puntos de guerra (" + why + "): §c" + w.score(g) + "§7/§f" + target());
        if (w.score(g) >= target()) end(w, g, "alcanzó " + target() + " puntos");
        save();
    }

    private int target() {
        return plugin.getConfig().getInt("diplomacia.puntos-victoria", 40);
    }

    // ------------------------------------------------------------------ guerra
    public String declareWar(Player p, Guild a, Guild b) {
        if (!enabled()) return "La diplomacia está desactivada.";
        String re = rankErr(p, a);
        if (re != null) return re;
        if (b == null || a == b) return "Elige otro gremio.";
        if (atWar(a, b)) return "Ya estáis en guerra.";
        if (plugin.guilds.isAllied(a, b)) return "Es tu aliado: rompe la alianza antes.";
        if (linked(a, b)) return "Es tu " + (lordOf(a) == b ? "señor" : "vasallo") + ": usa independencia o liberación.";
        long t = truceLeft(a, b);
        if (t > 0) return "Hay una tregua durante " + Msg.dur(t) + ".";
        if (warsOf(a).size() >= maxWars(a)) return "Tu gremio ya mantiene " + warsOf(a).size() + " guerras (máximo " + maxWars(a) + ").";
        if (warsOf(b).size() >= maxWars(b)) return b.name + " ya está en demasiadas guerras.";
        int minM = plugin.getConfig().getInt("diplomacia.min-miembros", 2);
        if (a.members.size() < minM) return "Tu gremio necesita al menos " + minM + " miembros.";
        String unfair = plugin.justice.declareCheck(a, b);
        if (unfair != null) return unfair;
        long cd = plugin.getConfig().getLong("diplomacia.cooldown-declarar-horas", 6) * 3_600_000L;
        long last = lastDeclare.getOrDefault(a.id, 0L);
        if (System.currentTimeMillis() - last < cd) return "Debes esperar " + Msg.dur(cd - (System.currentTimeMillis() - last)) + " para declarar otra guerra.";
        long cost = plugin.getConfig().getLong("diplomacia.costo-guerra", 24);
        if (a.bank < cost) return "El banco necesita " + plugin.currency.fmt(cost) + " para declarar la guerra.";
        a.bank -= cost;
        a.log("§6-" + Msg.num(cost) + " §7declarar guerra");
        startWar(a, b, "Declaración formal");
        plugin.audit.log(a, p, "GUERRA_DECLARADA", "contra " + b.name + " (coste " + cost + ")");
        plugin.guilds.save();
        return null;
    }

    private War startWar(Guild a, Guild b, String reason) {
        War w = new War();
        w.a = a.id;
        w.b = b.id;
        w.start = System.currentTimeMillis();
        w.end = w.start + days("diplomacia.guerra-dias", 7);
        w.reason = reason;
        wars.add(w);
        lastDeclare.put(a.id, w.start);
        for (Player pl : plugin.guilds.onlineMembers(a)) pl.playSound(pl.getLocation(), Sound.EVENT_RAID_HORN, 1f, 1f);
        for (Player pl : plugin.guilds.onlineMembers(b)) pl.playSound(pl.getLocation(), Sound.EVENT_RAID_HORN, 1f, 0.8f);
        plugin.guilds.broadcast(a, "§c⚔ §lGUERRA §r§7contra §6" + b.name + "§7. Gana quien llegue a §f" + target() + " §7puntos (asedios y bajas). Dura " + Msg.dur(w.end - w.start) + ".");
        plugin.guilds.broadcast(b, "§c⚔ §lGUERRA §r§6" + a.name + " §7os ha declarado la guerra. Gana quien llegue a §f" + target() + " §7puntos (asedios y bajas).");
        for (Player pl : Bukkit.getOnlinePlayers()) Msg.raw(pl, "§c⚔ §6" + a.name + " §7ha declarado la guerra a §6" + b.name + "§7.");
        plugin.discord.announce("guerras", "⚔ " + a.name + " ha declarado la guerra a " + b.name + " (" + reason + ").");
        save();
        return w;
    }

    private void end(War w, Guild winner, String why) {
        wars.remove(w);
        Guild a = plugin.guilds.byId(w.a), b = plugin.guilds.byId(w.b);
        if (a == null || b == null) {
            save();
            return;
        }
        long tr = days("diplomacia.tregua-dias", 3);
        truces.put(pair(a.id, b.id), System.currentTimeMillis() + tr);
        String msg;
        if (winner == null) {
            msg = "La guerra entre " + a.name + " y " + b.name + " termina en empate (" + why + "). Hay tregua de " + Msg.dur(tr) + ".";
        } else {
            Guild loser = winner == a ? b : a;
            long spoils = loser.bank * plugin.getConfig().getLong("diplomacia.botin-pct", 10) / 100L;
            spoils = (long) (spoils * (1 + plugin.tech.bonus(winner, "botin-guerra") / 100.0) * plugin.justice.lootFactor(winner, loser));
            spoils = Math.min(spoils, loser.bank);
            if (spoils > 0) {
                loser.bank -= spoils;
                loser.log("§c-" + Msg.num(spoils) + " §7tributo de guerra");
                winner.bank += spoils;
                winner.log("§a+" + Msg.num(spoils) + " §7botín de guerra");
            }
            plugin.guilds.addXp(winner, plugin.getConfig().getLong("diplomacia.xp-victoria", 1200));
            msg = winner.name + " gana la guerra contra " + loser.name + " (" + why + ")" + (spoils > 0 ? " y se lleva " + plugin.currency.fmt(spoils) + " de botín" : "") + ". Tregua de " + Msg.dur(tr) + ".";
            plugin.audit.log(winner, "sistema", "GUERRA_GANADA", "contra " + loser.name + ", botín " + spoils);
        }
        for (Player pl : Bukkit.getOnlinePlayers()) Msg.raw(pl, "§c⚔ §7" + msg);
        plugin.discord.announce("guerras", "⚔ " + msg);
        plugin.guilds.save();
        save();
    }

    // ------------------------------------------------------------------ paz y rendición
    private Proposal find(String type, String from, String to) {
        long now = System.currentTimeMillis();
        proposals.removeIf(x -> x.expires < now);
        for (Proposal x : proposals) if (x.type.equals(type) && x.from.equals(from) && x.to.equals(to)) return x;
        return null;
    }

    public List<Proposal> proposalsFor(Guild g) {
        long now = System.currentTimeMillis();
        proposals.removeIf(x -> x.expires < now);
        List<Proposal> out = new ArrayList<>();
        for (Proposal x : proposals) if (x.to.equals(g.id)) out.add(x);
        return out;
    }

    private void propose(String type, Guild from, Guild to) {
        proposals.removeIf(x -> x.type.equals(type) && x.from.equals(from.id) && x.to.equals(to.id));
        proposals.add(new Proposal(type, from.id, to.id, System.currentTimeMillis() + 24 * 3_600_000L));
    }

    /** Propone la paz; si el otro ya la había propuesto, se firma. */
    public String peace(Player p, Guild a, Guild b) {
        String re = rankErr(p, a);
        if (re != null) return re;
        War w = warBetween(a, b);
        if (w == null) return "No estáis en guerra.";
        if (find("PAZ", b.id, a.id) != null) {
            proposals.removeIf(x -> x.type.equals("PAZ") && (x.from.equals(a.id) || x.from.equals(b.id)) && (x.to.equals(a.id) || x.to.equals(b.id)));
            wars.remove(w);
            truces.put(pair(a.id, b.id), System.currentTimeMillis() + days("diplomacia.tregua-dias", 3));
            for (Player pl : Bukkit.getOnlinePlayers()) Msg.raw(pl, "§a✌ §6" + a.name + " §7y §6" + b.name + " §7han firmado la paz.");
            plugin.discord.announce("guerras", "✌ " + a.name + " y " + b.name + " han firmado la paz.");
            save();
            return null;
        }
        propose("PAZ", a, b);
        plugin.guilds.broadcast(b, "§a✌ §6" + a.name + " §7propone la paz. Acéptala en §f/gremio diplomacia§7.");
        plugin.guilds.broadcast(a, "§7Propuesta de paz enviada a §6" + b.name + "§7 (válida 24 h).");
        return null;
    }

    /** Rendirse: el otro gana. Si asVassal, además pasa a ser su vasallo (si es posible). */
    public String surrender(Player p, Guild loser, Guild winner, boolean asVassal) {
        String re = rankErr(p, loser);
        if (re != null) return re;
        War w = warBetween(loser, winner);
        if (w == null) return "No estáis en guerra.";
        if (asVassal) {
            String e = canVassal(winner, loser);
            if (e != null) return e;
        }
        end(w, winner, loser.name + " se rinde");
        if (asVassal) makeVassal(winner, loser);
        return null;
    }

    // ------------------------------------------------------------------ vasallaje
    private String canVassal(Guild lord, Guild v) {
        if (lord == v) return "Elige otro gremio.";
        if (lordOf(v) != null) return v.name + " ya es vasallo de otro gremio.";
        if (lordOf(lord) != null) return lord.name + " es vasallo y no puede tener vasallos.";
        if (!vassalsOf(v).isEmpty()) return v.name + " tiene vasallos y no puede ser vasallo.";
        if (vassalsOf(lord).size() >= maxVassals(lord)) {
            return lord.name + " no puede tener más vasallos (máx. " + maxVassals(lord) + "; investiga tecnologías de Gobierno).";
        }
        if (plugin.guilds.isAllied(lord, v)) return "Son aliados: rompe la alianza primero.";
        return null;
    }

    private void makeVassal(Guild lord, Guild v) {
        Vassal d = new Vassal();
        d.lord = lord.id;
        d.since = System.currentTimeMillis();
        d.nextTribute = d.since + plugin.getConfig().getLong("diplomacia.tributo-horas", 24) * 3_600_000L;
        vassals.put(v.id, d);
        proposals.removeIf(x -> x.type.equals("VASALLAJE") || x.type.equals("PROTECCION"));
        for (Player pl : Bukkit.getOnlinePlayers()) Msg.raw(pl, "§6👑 §7" + v.name + " jura vasallaje a §6" + lord.name + "§7.");
        plugin.discord.announce("guerras", "👑 " + v.name + " es ahora vasallo de " + lord.name + ".");
        plugin.audit.log(lord, "sistema", "VASALLO_NUEVO", v.name);
        save();
    }

    /** El señor ofrece vasallaje a otro gremio (el otro acepta con vasallo aceptar). */
    public String offerVassal(Player p, Guild lord, Guild v) {
        String re = rankErr(p, lord);
        if (re != null) return re;
        String e = canVassal(lord, v);
        if (e != null) return e;
        if (atWar(lord, v)) return "Estáis en guerra: se resuelve con rendición.";
        if (find("PROTECCION", v.id, lord.id) != null) {
            makeVassal(lord, v);
            return null;
        }
        propose("VASALLAJE", lord, v);
        plugin.guilds.broadcast(v, "§6👑 §6" + lord.name + " §7os ofrece vasallaje (protección a cambio de tributo " + plugin.getConfig().getInt("diplomacia.tributo-pct", 10) + "%). Acepta en §f/gremio diplomacia§7.");
        plugin.guilds.broadcast(lord, "§7Oferta de vasallaje enviada a §6" + v.name + "§7 (válida 24 h).");
        return null;
    }

    /** Un gremio pide ser vasallo de otro. */
    public String askProtection(Player p, Guild v, Guild lord) {
        String re = rankErr(p, v);
        if (re != null) return re;
        String e = canVassal(lord, v);
        if (e != null) return e;
        if (find("VASALLAJE", lord.id, v.id) != null) {
            makeVassal(lord, v);
            return null;
        }
        propose("PROTECCION", v, lord);
        plugin.guilds.broadcast(lord, "§6👑 §6" + v.name + " §7pide ser vuestro vasallo. Decide en §f/gremio diplomacia§7.");
        plugin.guilds.broadcast(v, "§7Petición de vasallaje enviada a §6" + lord.name + "§7 (válida 24 h).");
        return null;
    }

    /** Acepta una propuesta recibida (paz, vasallaje o petición de protección). */
    public String accept(Player p, Guild me, Proposal pr) {
        String re = rankErr(p, me);
        if (re != null) return re;
        Guild from = plugin.guilds.byId(pr.from);
        if (from == null) return "Ese gremio ya no existe.";
        switch (pr.type) {
            case "PAZ" -> {
                return peace(p, me, from);
            }
            case "VASALLAJE" -> {
                String e = canVassal(from, me);
                if (e != null) return e;
                makeVassal(from, me);
            }
            case "PROTECCION" -> {
                String e = canVassal(me, from);
                if (e != null) return e;
                makeVassal(me, from);
            }
            default -> {
                return "Propuesta desconocida.";
            }
        }
        proposals.remove(pr);
        return null;
    }

    public String reject(Player p, Guild me, Proposal pr) {
        String re = rankErr(p, me);
        if (re != null) return re;
        proposals.remove(pr);
        Guild from = plugin.guilds.byId(pr.from);
        if (from != null) plugin.guilds.broadcast(from, "§c" + me.name + " ha rechazado vuestra propuesta.");
        return null;
    }

    /** El señor libera a un vasallo. */
    public String release(Player p, Guild lord, Guild v) {
        String re = rankErr(p, lord);
        if (re != null) return re;
        if (lordOf(v) != lord) return v.name + " no es vuestro vasallo.";
        vassals.remove(v.id);
        for (Player pl : Bukkit.getOnlinePlayers()) Msg.raw(pl, "§6👑 §6" + lord.name + " §7libera a su vasallo §6" + v.name + "§7.");
        save();
        return null;
    }

    /** El vasallo se rebela: rompe el vínculo y empieza una guerra contra su antiguo señor. */
    public String independence(Player p, Guild v) {
        String re = rankErr(p, v);
        if (re != null) return re;
        Guild lord = lordOf(v);
        if (lord == null) return "No eres vasallo de nadie.";
        long cost = plugin.getConfig().getLong("diplomacia.costo-independencia", 32);
        if (v.bank < cost) return "El banco necesita " + plugin.currency.fmt(cost) + " para financiar la rebelión.";
        v.bank -= cost;
        v.log("§6-" + Msg.num(cost) + " §7independencia");
        vassals.remove(v.id);
        startWar(v, lord, "Rebelión");
        plugin.guilds.save();
        return null;
    }

    // ------------------------------------------------------------------ administración
    public String adminWar(Guild a, Guild b) {
        if (a == null || b == null || a == b) return "Gremios inválidos.";
        if (atWar(a, b)) return "Ya están en guerra.";
        startWar(a, b, "Decreto de la administración");
        return null;
    }

    public String adminPeace(Guild a, Guild b) {
        War w = warBetween(a, b);
        truces.remove(pair(a.id, b.id));
        if (w == null) return "No estaban en guerra (tregua eliminada si la había).";
        wars.remove(w);
        save();
        return null;
    }

    public String adminVassal(Guild lord, Guild v) {
        if (lord == null || v == null) return "Gremios inválidos.";
        if (vassals.containsKey(v.id)) return v.name + " ya es vasallo.";
        if (lord == v) return "Gremios inválidos.";
        makeVassal(lord, v);
        return null;
    }

    public boolean adminRelease(Guild v) {
        boolean r = vassals.remove(v.id) != null;
        save();
        return r;
    }

    // ------------------------------------------------------------------ bucle (cada minuto)
    public void tick() {
        if (!enabled()) return;
        long now = System.currentTimeMillis();
        for (War w : new ArrayList<>(wars)) {
            Guild a = plugin.guilds.byId(w.a), b = plugin.guilds.byId(w.b);
            if (a == null || b == null) {
                wars.remove(w);
                continue;
            }
            if (now >= w.end) {
                int diff = Math.abs(w.scoreA - w.scoreB);
                int min = plugin.getConfig().getInt("diplomacia.diferencia-minima", 5);
                end(w, diff >= min ? (w.scoreA > w.scoreB ? a : b) : null, "se acabó el plazo");
            }
        }
        truces.values().removeIf(t -> t < now);
        boolean changed = false;
        for (var e : new ArrayList<>(vassals.entrySet())) {
            Vassal v = e.getValue();
            if (v.nextTribute > now) continue;
            Guild vg = plugin.guilds.byId(e.getKey()), lord = plugin.guilds.byId(v.lord);
            v.nextTribute = now + plugin.getConfig().getLong("diplomacia.tributo-horas", 24) * 3_600_000L;
            changed = true;
            if (vg == null || lord == null) {
                vassals.remove(e.getKey());
                continue;
            }
            long pay = vg.bank * plugin.getConfig().getInt("diplomacia.tributo-pct", 10) / 100L;
            pay = Math.min(pay, plugin.getConfig().getLong("diplomacia.tributo-tope", 150));
            if (pay <= 0) continue;
            long got = Math.round(pay * (1 + plugin.tech.bonus(lord, "tributo") / 100.0));
            vg.bank -= pay;
            vg.log("§c-" + Msg.num(pay) + " §7tributo a " + lord.name);
            lord.bank += got;
            lord.log("§a+" + Msg.num(got) + " §7tributo de " + vg.name);
            plugin.guilds.broadcast(vg, "§6👑 §7Habéis pagado §e" + plugin.currency.fmt(pay) + " §7de tributo a §6" + lord.name + "§7.");
            plugin.guilds.broadcast(lord, "§6👑 §7Recibís §e" + plugin.currency.fmt(got) + " §7de tributo de §6" + vg.name + "§7.");
        }
        if (changed) {
            plugin.guilds.save();
            save();
        }
    }

    // ------------------------------------------------------------------ bajas
    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        if (!enabled() || wars.isEmpty()) return;
        Player victim = e.getEntity(), killer = victim.getKiller();
        if (killer == null || killer == victim) return;
        Guild gk = plugin.guilds.of(killer), gv = plugin.guilds.of(victim);
        War w = warBetween(gk, gv);
        if (w == null) return;
        long cd = plugin.getConfig().getLong("diplomacia.baja-cooldown-minutos", 10) * 60_000L;
        String key = killer.getUniqueId() + ">" + victim.getUniqueId();
        long now = System.currentTimeMillis();
        if (now - killCd.getOrDefault(key, 0L) < cd) return;
        killCd.put(key, now);
        add(w, gk, plugin.getConfig().getInt("diplomacia.puntos-baja", 1), killer.getName() + " abatió a " + victim.getName());
    }

    // ------------------------------------------------------------------ limpieza
    public void onGuildGone(Guild g) {
        wars.removeIf(w -> w.has(g));
        vassals.remove(g.id);
        vassals.values().removeIf(v -> v.lord.equals(g.id));
        truces.keySet().removeIf(k -> k.contains(g.id));
        proposals.removeIf(x -> x.from.equals(g.id) || x.to.equals(g.id));
        save();
    }

    // ------------------------------------------------------------------ persistencia
    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (Map<?, ?> m : y.getMapList("guerras")) {
            War w = new War();
            w.a = String.valueOf(m.get("a"));
            w.b = String.valueOf(m.get("b"));
            w.start = m.get("inicio") instanceof Number n ? n.longValue() : 0;
            w.end = m.get("fin") instanceof Number n ? n.longValue() : 0;
            w.scoreA = m.get("pa") instanceof Number n ? n.intValue() : 0;
            w.scoreB = m.get("pb") instanceof Number n ? n.intValue() : 0;
            w.reason = m.get("motivo") == null ? "" : String.valueOf(m.get("motivo"));
            wars.add(w);
        }
        ConfigurationSection t = y.getConfigurationSection("treguas");
        if (t != null) for (String k : t.getKeys(false)) truces.put(k, t.getLong(k));
        ConfigurationSection v = y.getConfigurationSection("vasallos");
        if (v != null) for (String k : v.getKeys(false)) {
            Vassal d = new Vassal();
            d.lord = v.getString(k + ".senor", "");
            d.since = v.getLong(k + ".desde");
            d.nextTribute = v.getLong(k + ".proximo-tributo");
            vassals.put(k, d);
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        List<Map<String, Object>> ws = new ArrayList<>();
        for (War w : wars) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("a", w.a);
            m.put("b", w.b);
            m.put("inicio", w.start);
            m.put("fin", w.end);
            m.put("pa", w.scoreA);
            m.put("pb", w.scoreB);
            m.put("motivo", w.reason);
            ws.add(m);
        }
        y.set("guerras", ws);
        for (var e : truces.entrySet()) y.set("treguas." + e.getKey(), e.getValue());
        for (var e : vassals.entrySet()) {
            y.set("vasallos." + e.getKey() + ".senor", e.getValue().lord);
            y.set("vasallos." + e.getKey() + ".desde", e.getValue().since);
            y.set("vasallos." + e.getKey() + ".proximo-tributo", e.getValue().nextTribute);
        }
        try {
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudo guardar diplomacia.yml: " + ex.getMessage());
        }
    }
}
