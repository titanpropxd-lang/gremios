package com.etheria.gremios.manager;

import com.etheria.gremios.GremiosPlugin;
import com.etheria.gremios.model.Guild;
import com.etheria.gremios.model.Raid;
import com.etheria.gremios.util.Msg;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Límites contra trucos conocidos: entrar/salir de gremios, vaciar el banco antes de un asedio, etc. */
public class AntiAbuseManager {
    private final GremiosPlugin plugin;
    private final File file;
    private final Map<UUID, Long> lastLeave = new HashMap<>();

    public AntiAbuseManager(GremiosPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "antiabuso.yml");
        if (file.exists()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
            var s = y.getConfigurationSection("salidas");
            if (s != null) {
                for (String k : s.getKeys(false)) {
                    try {
                        lastLeave.put(UUID.fromString(k), s.getLong(k));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        }
    }

    private boolean on() {
        return plugin.getConfig().getBoolean("antiabuso.activo", true);
    }

    private boolean inRaid(Guild g) {
        Raid r = plugin.raids.of(g);
        return r != null && !r.test;
    }

    public void recordLeave(UUID u) {
        lastLeave.put(u, System.currentTimeMillis());
        save();
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        long limit = System.currentTimeMillis() - 7L * 24 * 3600_000L;
        lastLeave.values().removeIf(v -> v < limit);
        for (var e : lastLeave.entrySet()) y.set("salidas." + e.getKey(), e.getValue());
        try {
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudo guardar antiabuso.yml: " + ex.getMessage());
        }
    }

    /** Motivo por el que el jugador no puede entrar a ese gremio, o null. */
    public String canJoin(Guild g, UUID u) {
        if (!on()) return null;
        long wait = (long) (plugin.getConfig().getDouble("antiabuso.espera-tras-salir-horas", 12) * 3600_000L);
        Long l = lastLeave.get(u);
        if (l != null && wait > 0 && System.currentTimeMillis() - l < wait) {
            return "Dejaste un gremio hace poco: espera " + Msg.dur(wait - (System.currentTimeMillis() - l)) + " para unirte a otro.";
        }
        if (g != null && plugin.getConfig().getBoolean("antiabuso.bloquear-entrada-en-asedio", true) && inRaid(g)) {
            return "Ese gremio está en un asedio: no admite miembros nuevos hasta que termine.";
        }
        return null;
    }

    /** Salir o expulsar durante un asedio. */
    public String canLeave(Guild g) {
        if (!on() || g == null) return null;
        if (plugin.getConfig().getBoolean("antiabuso.bloquear-salida-en-asedio", true) && inRaid(g)) {
            return "No se puede salir ni expulsar mientras haya un asedio en curso o programado.";
        }
        return null;
    }

    /** Retirar del banco. */
    public String canWithdraw(Guild g, org.bukkit.entity.Player p, long amount) {
        if (!on() || g == null) return null;
        if (plugin.getConfig().getBoolean("antiabuso.bloquear-retiro-en-asedio", true) && inRaid(g)) {
            return "El banco está bloqueado mientras haya un asedio programado o en curso (evita vaciarlo para esquivar el botín).";
        }
        Guild.MemberData md = g.member(p.getUniqueId());
        long wait = (long) (plugin.getConfig().getDouble("antiabuso.retiro-nuevo-miembro-horas", 24) * 3600_000L);
        if (md != null && wait > 0 && System.currentTimeMillis() - md.joinedAt < wait) {
            return "Los miembros nuevos no pueden retirar del banco hasta pasadas " + Msg.dur(wait - (System.currentTimeMillis() - md.joinedAt)) + ".";
        }
        int pct = plugin.getConfig().getInt("antiabuso.retiro-alerta-pct", 50);
        if (pct > 0 && g.bank > 0 && amount * 100 / g.bank >= pct && amount >= plugin.getConfig().getLong("antiabuso.retiro-alerta-minimo", 64)) {
            plugin.audit.log(g, p, "RETIRO_GRANDE", Msg.num(amount) + " de " + Msg.num(g.bank));
            for (var o : plugin.guilds.onlineMembers(g)) {
                if (g.leader.equals(o.getUniqueId())) Msg.to(o, "§e⚠ §f" + p.getName() + " §7retira una parte grande del banco (§f" + Msg.num(amount) + "§7).");
            }
        }
        return null;
    }
}
