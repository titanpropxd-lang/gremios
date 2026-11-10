package com.etheria.gremios.manager;

import com.etheria.gremios.GremiosPlugin;
import com.etheria.gremios.model.Guild;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Reglas de justicia en la guerra: protege a los gremios pequeños y reduce el botín cuando el ataque es abusivo. */
public class JusticeManager {
    private final GremiosPlugin plugin;

    public JusticeManager(GremiosPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean on() {
        return plugin.getConfig().getBoolean("justicia.activo", true);
    }

    public double ratio(Guild a, Guild d) {
        return a.members.size() / (double) Math.max(1, d.members.size());
    }

    /** null si se puede declarar; si no, el motivo. */
    public String declareCheck(Guild a, Guild d) {
        if (!on()) return null;
        double max = plugin.getConfig().getDouble("justicia.ratio-maximo", 4.0);
        if (max > 0 && ratio(a, d) > max) {
            return "Tu gremio es más de " + trim(max) + " veces mayor que " + d.name + ": no puedes asediarlo (protección de gremios pequeños).";
        }
        return null;
    }

    /** Factor del botín (1 = completo) cuando el atacante es mucho mayor que el defensor. */
    public double lootFactor(Guild a, Guild d) {
        if (!on() || a == null || d == null) return 1.0;
        double th = plugin.getConfig().getDouble("justicia.ratio-penalizacion", 2.0);
        double r = ratio(a, d);
        if (th <= 0 || r <= th) return 1.0;
        double min = plugin.getConfig().getDouble("justicia.botin-minimo-pct", 10) / 100.0;
        return Math.max(min, Math.min(1.0, th / r));
    }

    public String lootNote(Guild a, Guild d) {
        double f = lootFactor(a, d);
        if (f >= 0.999) return null;
        return "Tu gremio es mucho mayor que " + d.name + ": el botín y la XP de esta victoria serán del " + Math.round(f * 100) + "%.";
    }

    /** Defensores conectados necesarios para que el asedio pueda empezar. */
    public int requiredDefenders(Guild d) {
        int base = plugin.getConfig().getInt("raid.min-defensores-online", 1);
        if (!on()) return base;
        int pct = plugin.getConfig().getInt("justicia.defensores-pct", 30);
        int cap = plugin.getConfig().getInt("justicia.defensores-max", 5);
        int need = (int) Math.ceil(d.members.size() * pct / 100.0);
        return Math.max(base, Math.min(cap, need));
    }

    private boolean isSmall(Guild d) {
        return d.members.size() <= plugin.getConfig().getInt("justicia.noche.pequeno-max-miembros", 4);
    }

    private boolean inNight(int h) {
        int ini = plugin.getConfig().getInt("justicia.noche.inicio-hora", 23);
        int fin = plugin.getConfig().getInt("justicia.noche.fin-hora", 9);
        return ini <= fin ? (h >= ini && h < fin) : (h >= ini || h < fin);
    }

    /** Un gremio pequeño no puede ser asediado de noche: se retrasa el inicio hasta que acabe la franja. */
    public long adjustStart(Guild d, long ms) {
        if (!on() || d == null || !plugin.getConfig().getBoolean("justicia.noche.activo", true) || !isSmall(d)) return ms;
        ZonedDateTime z = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault());
        if (!inNight(z.getHour())) return ms;
        int fin = plugin.getConfig().getInt("justicia.noche.fin-hora", 9);
        ZonedDateTime next = z.withHour(fin).withMinute(0).withSecond(0).withNano(0);
        if (!next.isAfter(z)) next = next.plusDays(1);
        return next.toInstant().toEpochMilli();
    }

    /** ¿Está ahora mismo en franja nocturna protegida para este gremio? */
    public boolean nightProtected(Guild d) {
        if (!on() || d == null || !plugin.getConfig().getBoolean("justicia.noche.activo", true) || !isSmall(d)) return false;
        return inNight(ZonedDateTime.now().getHour());
    }

    private static String trim(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
}
