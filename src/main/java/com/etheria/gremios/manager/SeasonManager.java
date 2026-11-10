package com.etheria.gremios.manager;

import com.etheria.gremios.GremiosPlugin;
import com.etheria.gremios.model.Guild;
import com.etheria.gremios.model.Raid;
import com.etheria.gremios.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ranking semanal con MVP y temporadas. Cada semana se premia al mejor jugador (MVP) y a los mejores gremios;
 * cada N semanas termina una temporada: se premia a los campeones, reciben título y se reinician las estadísticas.
 * Datos en temporadas.yml.
 */
public class SeasonManager implements Listener {
    /** Estadísticas de un periodo (semana o temporada). */
    public static final class Stats {
        public long pts, kills, raids, missions, nodeDmg;

        void clear() {
            pts = kills = raids = missions = nodeDmg = 0;
        }
    }

    public static final class PRec {
        public String name = "?";
        public String guildId;
        public final Stats week = new Stats(), season = new Stats();
        double nodeFrac;
    }

    public static final class GRec {
        public String name = "?";
        public long weekPts, seasonPts;
        public int weekWins, seasonWins;
    }

    /** Fila de un ranking. */
    public record Row(String key, String name, String sub, long pts, Stats stats) {
    }

    private final GremiosPlugin plugin;
    private final File file;
    final Map<UUID, PRec> players = new LinkedHashMap<>();
    final Map<String, GRec> guilds = new LinkedHashMap<>();
    final Map<String, String> weekGuildTitle = new HashMap<>(), seasonGuildTitle = new HashMap<>();
    final Map<UUID, String> weekPlayerTitle = new HashMap<>(), seasonPlayerTitle = new HashMap<>();
    final Map<UUID, Long> pendingMoney = new HashMap<>();
    final Map<UUID, List<String>> pendingCmds = new HashMap<>();
    final List<String> history = new ArrayList<>();   // resumen de semanas y temporadas (más nuevo primero)
    private final Map<String, Long> killCd = new HashMap<>();
    public int season = 1, weekInSeason = 1;
    public long seasonStart, weekEnd;

    public SeasonManager(GremiosPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "temporadas.yml");
        load();
        if (seasonStart == 0) seasonStart = System.currentTimeMillis();
        if (weekEnd == 0) weekEnd = nextWeekEnd(System.currentTimeMillis());
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("temporadas.activo", true);
    }

    private long cfg(String path, long def) {
        return plugin.getConfig().getLong("temporadas." + path, def);
    }

    // ------------------------------------------------------------------ calendario

    static DayOfWeek parseDay(String s) {
        if (s == null) return DayOfWeek.MONDAY;
        return switch (s.trim().toUpperCase().replace("É", "E").replace("Á", "A")) {
            case "LUNES", "MONDAY" -> DayOfWeek.MONDAY;
            case "MARTES", "TUESDAY" -> DayOfWeek.TUESDAY;
            case "MIERCOLES", "WEDNESDAY" -> DayOfWeek.WEDNESDAY;
            case "JUEVES", "THURSDAY" -> DayOfWeek.THURSDAY;
            case "VIERNES", "FRIDAY" -> DayOfWeek.FRIDAY;
            case "SABADO", "SATURDAY" -> DayOfWeek.SATURDAY;
            case "DOMINGO", "SUNDAY" -> DayOfWeek.SUNDAY;
            default -> DayOfWeek.MONDAY;
        };
    }

    /** Próximo cierre de semana (día y hora de la config, zona horaria del servidor) estrictamente posterior a 'from'. */
    public long nextWeekEnd(long from) {
        return nextWeekEnd(from, parseDay(plugin.getConfig().getString("temporadas.dia-cierre", "LUNES")),
                (int) cfg("hora-cierre", 0), ZoneId.systemDefault());
    }

    static long nextWeekEnd(long from, DayOfWeek day, int hour, ZoneId zone) {
        ZonedDateTime now = java.time.Instant.ofEpochMilli(from).atZone(zone);
        ZonedDateTime t = now.withHour(Math.max(0, Math.min(23, hour))).withMinute(0).withSecond(0).withNano(0);
        int add = (day.getValue() - t.getDayOfWeek().getValue() + 7) % 7;
        t = t.plusDays(add);
        if (!t.toInstant().isAfter(java.time.Instant.ofEpochMilli(from))) t = t.plusDays(7);
        return t.toInstant().toEpochMilli();
    }

    public long weekLeftMs() {
        return Math.max(0, weekEnd - System.currentTimeMillis());
    }

    public int weeksPerSeason() {
        return (int) Math.max(1, cfg("semanas", 4));
    }

    /** Tiempo hasta el final de la temporada (semanas restantes completas + lo que queda de esta). */
    public long seasonLeftMs() {
        return weekLeftMs() + (long) Math.max(0, weeksPerSeason() - weekInSeason) * 7L * 86_400_000L;
    }

    public void tick() {
        if (!enabled()) return;
        if (System.currentTimeMillis() >= weekEnd) closeWeek();
    }

    // ------------------------------------------------------------------ puntos

    private PRec rec(Player p) {
        PRec r = players.computeIfAbsent(p.getUniqueId(), k -> new PRec());
        r.name = p.getName();
        Guild g = plugin.guilds.of(p);
        r.guildId = g == null ? r.guildId : g.id;
        return r;
    }

    private GRec grec(Guild g) {
        GRec r = guilds.computeIfAbsent(g.id, k -> new GRec());
        r.name = g.name;
        return r;
    }

    /** Suma puntos a un jugador (y a su gremio). stat: kills, raids, missions, otros. */
    public void add(Player p, String stat, long pts) {
        if (!enabled() || p == null || pts <= 0) return;
        Guild g = plugin.guilds.of(p);
        if (g == null) return;
        PRec r = rec(p);
        for (Stats s : new Stats[]{r.week, r.season}) {
            s.pts += pts;
            switch (stat) {
                case "kills" -> s.kills++;
                case "raids" -> s.raids++;
                case "missions" -> s.missions++;
                default -> {
                }
            }
        }
        GRec gr = grec(g);
        gr.weekPts += pts;
        gr.seasonPts += pts;
    }

    public void onMission(Player p) {
        add(p, "missions", cfg("puntos.mision", 10));
    }

    public void onNodeDamage(Player p, double dmg) {
        if (!enabled() || p == null || dmg <= 0 || plugin.guilds.of(p) == null) return;
        PRec r = rec(p);
        r.week.nodeDmg += (long) dmg;
        r.season.nodeDmg += (long) dmg;
        double per100 = plugin.getConfig().getDouble("temporadas.puntos.nodo-por-100-de-dano", 2.0);
        r.nodeFrac += dmg * per100 / 100.0;
        long whole = (long) r.nodeFrac;
        if (whole > 0) {
            r.nodeFrac -= whole;
            add(p, "otros", whole);
        }
    }

    public void onRaidResult(Guild a, Guild d, boolean attackerWon) {
        if (!enabled()) return;
        Guild winner = attackerWon ? a : d;
        if (winner == null) return;
        GRec gr = grec(winner);
        long bonus = cfg(attackerWon ? "puntos.raid-victoria-gremio" : "puntos.raid-defensa-gremio", attackerWon ? 100 : 80);
        gr.weekPts += bonus;
        gr.seasonPts += bonus;
        gr.weekWins++;
        gr.seasonWins++;
        long each = cfg("puntos.raid-participante", 25);
        for (Player p : plugin.guilds.onlineMembers(winner)) {
            add(p, "raids", each);
            Msg.actionbar(p, "§6✦ §e+" + each + " puntos de temporada §7(asedio)");
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        if (!enabled()) return;
        Player victim = e.getEntity(), killer = victim.getKiller();
        if (killer == null || killer == victim) return;
        Guild gk = plugin.guilds.of(killer), gv = plugin.guilds.of(victim);
        if (gk == null || gv == null || gk.id.equals(gv.id)) return;
        boolean valid = plugin.diplo.atWar(gk, gv);
        Raid r = plugin.raids.of(gk);
        if (!valid && r != null && r.state == Raid.State.ACTIVE
                && (r.attackerId.equals(gv.id) || r.defenderId.equals(gv.id))) valid = true;
        if (!valid) return;
        String key = killer.getUniqueId() + ">" + victim.getUniqueId();
        long now = System.currentTimeMillis();
        if (now - killCd.getOrDefault(key, 0L) < cfg("baja-cooldown-minutos", 10) * 60_000L) return;
        killCd.put(key, now);
        add(killer, "kills", cfg("puntos.baja", 15));
    }

    // ------------------------------------------------------------------ rankings

    public List<Row> topPlayers(boolean seasonal, int n) {
        List<Row> out = new ArrayList<>();
        for (var en : players.entrySet()) {
            PRec r = en.getValue();
            Stats s = seasonal ? r.season : r.week;
            if (s.pts <= 0) continue;
            Guild g = r.guildId == null ? null : plugin.guilds.byId(r.guildId);
            out.add(new Row(en.getKey().toString(), r.name, g == null ? "§8sin gremio" : g.name, s.pts, s));
        }
        out.sort(Comparator.comparingLong(Row::pts).reversed());
        return out.size() > n ? new ArrayList<>(out.subList(0, n)) : out;
    }

    public List<Row> topGuilds(boolean seasonal, int n) {
        List<Row> out = new ArrayList<>();
        for (var en : guilds.entrySet()) {
            GRec r = en.getValue();
            long pts = seasonal ? r.seasonPts : r.weekPts;
            if (pts <= 0) continue;
            Guild g = plugin.guilds.byId(en.getKey());
            if (g == null) continue;
            out.add(new Row(en.getKey(), g.name, "§7victorias: §f" + (seasonal ? r.seasonWins : r.weekWins), pts, null));
        }
        out.sort(Comparator.comparingLong(Row::pts).reversed());
        return out.size() > n ? new ArrayList<>(out.subList(0, n)) : out;
    }

    public long ptsOf(Player p, boolean seasonal) {
        PRec r = players.get(p.getUniqueId());
        return r == null ? 0 : (seasonal ? r.season.pts : r.week.pts);
    }

    public int positionOf(String key, boolean seasonal, boolean guildRank) {
        List<Row> l = guildRank ? topGuilds(seasonal, 1000) : topPlayers(seasonal, 1000);
        for (int i = 0; i < l.size(); i++) if (l.get(i).key().equals(key)) return i + 1;
        return 0;
    }

    public List<String> historyLines() {
        return history;
    }

    // ------------------------------------------------------------------ títulos

    public String titleOf(Guild g) {
        if (g == null) return "";
        String s = seasonGuildTitle.get(g.id);
        if (s != null) return s;
        return weekGuildTitle.getOrDefault(g.id, "");
    }

    public String titleOf(UUID u) {
        String s = seasonPlayerTitle.get(u);
        if (s != null) return s;
        return weekPlayerTitle.getOrDefault(u, "");
    }

    // ------------------------------------------------------------------ cierres

    private List<Long> prizes(String path, List<Long> def) {
        List<Long> l = plugin.getConfig().getLongList("temporadas.premios." + path);
        return l.isEmpty() ? def : l;
    }

    private void payPlayer(UUID u, String name, long money, List<String> cmds) {
        Player p = Bukkit.getPlayer(u);
        if (money > 0) {
            if (p != null) {
                plugin.currency.give(p, money);
                Msg.to(p, "§6✦ §ePremio: §f" + plugin.currency.fmt(money));
            } else pendingMoney.merge(u, money, Long::sum);
        }
        for (String c : cmds) {
            if (p != null) runCmd(c, name);
            else pendingCmds.computeIfAbsent(u, k -> new ArrayList<>()).add(c);
        }
    }

    private void runCmd(String cmd, String name) {
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{jugador}", name));
        } catch (Exception ex) {
            plugin.getLogger().warning("Temporadas: comando de premio falló (" + cmd + "): " + ex.getMessage());
        }
    }

    private void payGuild(String guildId, long money, long xp) {
        Guild g = plugin.guilds.byId(guildId);
        if (g == null) return;
        if (money > 0) {
            g.bank += money;
            g.log("§a+" + Msg.num(money) + " §7premio de ranking");
        }
        if (xp > 0) plugin.guilds.addXp(g, xp);
    }

    /** Suma a los gremios los puntos pasivos de la semana (reliquias custodiadas). */
    private void passivePoints() {
        if (plugin.relics == null) return;
        long per = cfg("puntos.reliquia-semanal", 25);
        for (Guild g : plugin.guilds.all()) {
            int n = plugin.relics.countHeld(g);
            if (n > 0 && per > 0) {
                GRec r = grec(g);
                r.weekPts += per * n;
                r.seasonPts += per * n;
            }
        }
    }

    public void closeWeek() {
        passivePoints();
        List<Row> tp = topPlayers(false, 10), tg = topGuilds(false, 10);
        weekPlayerTitle.clear();
        weekGuildTitle.clear();
        List<String> msgs = new ArrayList<>();
        msgs.add("§6§l✦ RESUMEN SEMANAL §7(temporada " + season + ", semana " + weekInSeason + "/" + weeksPerSeason() + ")");
        List<Long> pp = prizes("jugador-top", List.of(32L, 16L, 8L));
        List<Long> gp = prizes("gremio-top", List.of(64L, 32L, 16L));
        List<String> mvpCmds = plugin.getConfig().getStringList("temporadas.premios.comandos-mvp");
        for (int i = 0; i < tp.size() && i < pp.size(); i++) {
            Row r = tp.get(i);
            UUID u = UUID.fromString(r.key());
            payPlayer(u, r.name(), pp.get(i), i == 0 ? mvpCmds : List.of());
            if (i == 0) {
                weekPlayerTitle.put(u, "§e★ MVP semanal");
                msgs.add("§e★ MVP: §f" + r.name() + " §7(" + Msg.num(r.pts()) + " pts · " + r.sub() + "§7)");
            } else msgs.add("§7" + (i + 1) + "º jugador: §f" + r.name() + " §7(" + Msg.num(r.pts()) + " pts)");
        }
        long xp = cfg("premios.gremio-xp", 400);
        for (int i = 0; i < tg.size() && i < gp.size(); i++) {
            Row r = tg.get(i);
            payGuild(r.key(), gp.get(i), i == 0 ? xp : xp / 4);
            if (i == 0) {
                weekGuildTitle.put(r.key(), "§6Gremio de la semana");
                msgs.add("§6♛ Mejor gremio: §f" + r.name() + " §7(" + Msg.num(r.pts()) + " pts)");
            } else msgs.add("§7" + (i + 1) + "º gremio: §f" + r.name() + " §7(" + Msg.num(r.pts()) + " pts)");
        }
        if (tp.isEmpty() && tg.isEmpty()) msgs.add("§7Esta semana nadie sumó puntos.");
        broadcast(msgs);
        history.add(0, "Semana " + weekInSeason + " (T" + season + "): MVP " + (tp.isEmpty() ? "—" : tp.get(0).name())
                + " · gremio " + (tg.isEmpty() ? "—" : tg.get(0).name()));
        while (history.size() > 24) history.remove(history.size() - 1);
        plugin.discord.announce("ranking", String.join("\n", msgs).replaceAll("§.", ""));
        boolean endSeason = weekInSeason >= weeksPerSeason();
        // reinicio semanal
        for (PRec r : players.values()) {
            r.week.clear();
            r.nodeFrac = 0;
        }
        for (GRec r : guilds.values()) {
            r.weekPts = 0;
            r.weekWins = 0;
        }
        weekInSeason++;
        weekEnd = nextWeekEnd(System.currentTimeMillis());
        if (endSeason) closeSeason();
        save();
    }

    public void closeSeason() {
        List<Row> tp = topPlayers(true, 10), tg = topGuilds(true, 10);
        List<String> msgs = new ArrayList<>();
        msgs.add("§6§l♛ FIN DE LA TEMPORADA " + season + " ♛");
        seasonGuildTitle.clear();
        seasonPlayerTitle.clear();
        List<Long> pp = prizes("temporada-jugador", List.of(200L, 100L, 50L));
        List<Long> gp = prizes("temporada-gremio", List.of(400L, 200L, 100L));
        List<String> cmds = plugin.getConfig().getStringList("temporadas.premios.comandos-campeon");
        String[] names = {"Campeón", "Subcampeón", "Tercer puesto"};
        for (int i = 0; i < tg.size() && i < gp.size(); i++) {
            Row r = tg.get(i);
            payGuild(r.key(), gp.get(i), cfg("premios.temporada-xp", 2000) / (i + 1));
            seasonGuildTitle.put(r.key(), "§6" + names[Math.min(i, 2)] + " T" + season);
            msgs.add("§6" + (i + 1) + "º gremio: §f" + r.name() + " §7(" + Msg.num(r.pts()) + " pts)");
        }
        for (int i = 0; i < tp.size() && i < pp.size(); i++) {
            Row r = tp.get(i);
            UUID u = UUID.fromString(r.key());
            payPlayer(u, r.name(), pp.get(i), i == 0 ? cmds : List.of());
            seasonPlayerTitle.put(u, "§6" + names[Math.min(i, 2)] + " T" + season);
            msgs.add("§e" + (i + 1) + "º jugador: §f" + r.name() + " §7(" + Msg.num(r.pts()) + " pts)");
        }
        if (tp.isEmpty() && tg.isEmpty()) msgs.add("§7Nadie sumó puntos esta temporada.");
        msgs.add("§7Empieza la temporada §f" + (season + 1) + "§7. ¡Todos de vuelta a cero!");
        broadcast(msgs);
        history.add(0, "TEMPORADA " + season + ": campeón " + (tg.isEmpty() ? "—" : tg.get(0).name())
                + " · jugador " + (tp.isEmpty() ? "—" : tp.get(0).name()));
        while (history.size() > 24) history.remove(history.size() - 1);
        plugin.discord.announce("ranking", String.join("\n", msgs).replaceAll("§.", ""));
        for (PRec r : players.values()) r.season.clear();
        for (GRec r : guilds.values()) {
            r.seasonPts = 0;
            r.seasonWins = 0;
        }
        season++;
        weekInSeason = 1;
        seasonStart = System.currentTimeMillis();
    }

    private void broadcast(List<String> lines) {
        for (Player p : Bukkit.getOnlinePlayers()) for (String l : lines) Msg.raw(p, l);
    }

    public void onGuildGone(Guild g) {
        weekGuildTitle.remove(g.id);
        seasonGuildTitle.remove(g.id);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Long m = pendingMoney.remove(p.getUniqueId());
        List<String> cmds = pendingCmds.remove(p.getUniqueId());
        if (m == null && cmds == null) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (m != null && m > 0) {
                plugin.currency.give(p, m);
                Msg.to(p, "§6✦ §eRecibes tu premio pendiente de ranking: §f" + plugin.currency.fmt(m));
            }
            if (cmds != null) for (String c : cmds) runCmd(c, p.getName());
            save();
        }, 60L);
    }

    // ------------------------------------------------------------------ persistencia

    private static void putStats(ConfigurationSection s, String key, Stats st) {
        s.set(key + ".pts", st.pts);
        s.set(key + ".kills", st.kills);
        s.set(key + ".raids", st.raids);
        s.set(key + ".missions", st.missions);
        s.set(key + ".nodo", st.nodeDmg);
    }

    private static void getStats(ConfigurationSection s, String key, Stats st) {
        st.pts = s.getLong(key + ".pts");
        st.kills = s.getLong(key + ".kills");
        st.raids = s.getLong(key + ".raids");
        st.missions = s.getLong(key + ".missions");
        st.nodeDmg = s.getLong(key + ".nodo");
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("temporada", season);
        y.set("semana", weekInSeason);
        y.set("inicio", seasonStart);
        y.set("cierre-semana", weekEnd);
        for (var en : players.entrySet()) {
            String k = "jugadores." + en.getKey();
            PRec r = en.getValue();
            y.set(k + ".nombre", r.name);
            y.set(k + ".gremio", r.guildId);
            putStats(y, k + ".semana", r.week);
            putStats(y, k + ".temporada", r.season);
        }
        for (var en : guilds.entrySet()) {
            String k = "gremios." + en.getKey();
            GRec r = en.getValue();
            y.set(k + ".nombre", r.name);
            y.set(k + ".semana", r.weekPts);
            y.set(k + ".temporada", r.seasonPts);
            y.set(k + ".victorias-semana", r.weekWins);
            y.set(k + ".victorias-temporada", r.seasonWins);
        }
        weekGuildTitle.forEach((k, v) -> y.set("titulos.gremio-semana." + k, v));
        seasonGuildTitle.forEach((k, v) -> y.set("titulos.gremio-temporada." + k, v));
        weekPlayerTitle.forEach((k, v) -> y.set("titulos.jugador-semana." + k, v));
        seasonPlayerTitle.forEach((k, v) -> y.set("titulos.jugador-temporada." + k, v));
        pendingMoney.forEach((k, v) -> y.set("pendiente.dinero." + k, v));
        pendingCmds.forEach((k, v) -> y.set("pendiente.comandos." + k, v));
        y.set("historial", history);
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("No se pudo guardar temporadas.yml: " + e.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        season = Math.max(1, y.getInt("temporada", 1));
        weekInSeason = Math.max(1, y.getInt("semana", 1));
        seasonStart = y.getLong("inicio");
        weekEnd = y.getLong("cierre-semana");
        ConfigurationSection ps = y.getConfigurationSection("jugadores");
        if (ps != null) for (String k : ps.getKeys(false)) {
            try {
                PRec r = new PRec();
                ConfigurationSection s = ps.getConfigurationSection(k);
                r.name = s.getString("nombre", "?");
                r.guildId = s.getString("gremio");
                getStats(s, "semana", r.week);
                getStats(s, "temporada", r.season);
                players.put(UUID.fromString(k), r);
            } catch (IllegalArgumentException ignored) {
            }
        }
        ConfigurationSection gs = y.getConfigurationSection("gremios");
        if (gs != null) for (String k : gs.getKeys(false)) {
            GRec r = new GRec();
            ConfigurationSection s = gs.getConfigurationSection(k);
            r.name = s.getString("nombre", "?");
            r.weekPts = s.getLong("semana");
            r.seasonPts = s.getLong("temporada");
            r.weekWins = s.getInt("victorias-semana");
            r.seasonWins = s.getInt("victorias-temporada");
            guilds.put(k, r);
        }
        readMap(y, "titulos.gremio-semana", weekGuildTitle);
        readMap(y, "titulos.gremio-temporada", seasonGuildTitle);
        readUuid(y, "titulos.jugador-semana", weekPlayerTitle);
        readUuid(y, "titulos.jugador-temporada", seasonPlayerTitle);
        ConfigurationSection pm = y.getConfigurationSection("pendiente.dinero");
        if (pm != null) for (String k : pm.getKeys(false)) {
            try {
                pendingMoney.put(UUID.fromString(k), pm.getLong(k));
            } catch (IllegalArgumentException ignored) {
            }
        }
        ConfigurationSection pc = y.getConfigurationSection("pendiente.comandos");
        if (pc != null) for (String k : pc.getKeys(false)) {
            try {
                pendingCmds.put(UUID.fromString(k), new ArrayList<>(pc.getStringList(k)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        history.addAll(y.getStringList("historial"));
    }

    private static void readMap(YamlConfiguration y, String path, Map<String, String> into) {
        ConfigurationSection s = y.getConfigurationSection(path);
        if (s != null) for (String k : s.getKeys(false)) into.put(k, s.getString(k, ""));
    }

    private static void readUuid(YamlConfiguration y, String path, Map<UUID, String> into) {
        ConfigurationSection s = y.getConfigurationSection(path);
        if (s != null) for (String k : s.getKeys(false)) {
            try {
                into.put(UUID.fromString(k), s.getString(k, ""));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    // ------------------------------------------------------------------ admin

    public void addPoints(Player target, long pts) {
        add(target, "otros", pts);
    }

    public void resetAll() {
        for (PRec r : players.values()) {
            r.week.clear();
            r.season.clear();
        }
        for (GRec r : guilds.values()) {
            r.weekPts = r.seasonPts = 0;
            r.weekWins = r.seasonWins = 0;
        }
        save();
    }
}
