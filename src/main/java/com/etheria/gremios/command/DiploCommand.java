package com.etheria.gremios.command;

import com.etheria.gremios.GremiosPlugin;
import com.etheria.gremios.manager.DiplomacyManager;
import com.etheria.gremios.model.Guild;
import com.etheria.gremios.tech.Tech;
import com.etheria.gremios.tech.TechTree;
import com.etheria.gremios.util.Msg;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Locale;

/** Comandos de texto de tecnologías y diplomacia (/gremio tecnologias, /gremio diplomacia). */
public class DiploCommand {
    private final GremiosPlugin plugin;

    public DiploCommand(GremiosPlugin plugin) {
        this.plugin = plugin;
    }

    private Guild guildArg(String[] a, int from) {
        if (a.length <= from) return null;
        return plugin.guilds.byName(String.join(" ", Arrays.copyOfRange(a, from, a.length)));
    }

    // ------------------------------------------------------------------ tecnologías
    public void tech(Player p, Guild g, String[] a) {
        if (g == null) {
            Msg.to(p, "§cNecesitas un gremio.");
            return;
        }
        String sub = a.length > 0 ? a[0].toLowerCase(Locale.ROOT) : "menu";
        switch (sub) {
            case "lista" -> {
                Msg.to(p, "§bTecnologías §7(" + plugin.tech.count(g) + "/" + plugin.tech.totalVisible() + ")");
                for (Tech.Branch b : Tech.Branch.values()) {
                    var l = plugin.tech.branch(b);
                    if (l.isEmpty()) continue;
                    int d = 0;
                    for (Tech t : l) if (plugin.tech.has(g, t.id)) d++;
                    p.sendMessage(" " + b.color + b.display + " §7" + d + "/" + l.size());
                }
                p.sendMessage("§7Detalle: §f/gremio tecnologias info <id>  §8| §7Investigar: §f/gremio tecnologias investigar <id>");
            }
            case "info" -> {
                Tech t = a.length > 1 ? TechTree.ALL.get(a[1].toLowerCase(Locale.ROOT)) : null;
                if (t == null) {
                    Msg.to(p, "§c/gremio tecnologias info <id>  (los ids salen en el menú o con /gremioadmin tecnologia lista)");
                    return;
                }
                p.sendMessage(t.branch.color + "§l" + t.name + " §8(" + t.id + ", nivel " + t.tier + ")");
                p.sendMessage("§7" + t.desc);
                for (String l : t.effectLines()) p.sendMessage(" §a▸ §f" + l);
                p.sendMessage("§7Coste: §e" + plugin.currency.fmt(plugin.tech.cost(g, t)) + " §8· §7Tiempo: §f" + Msg.dur(plugin.tech.millis(g, t)));
                String b = plugin.tech.blocked(g, t);
                p.sendMessage(plugin.tech.has(g, t.id) ? "§aInvestigada" : b == null ? "§aSe puede investigar ahora." : "§c" + b);
            }
            case "investigar", "start" -> {
                Tech t = a.length > 1 ? TechTree.ALL.get(a[1].toLowerCase(Locale.ROOT)) : null;
                if (t == null) {
                    Msg.to(p, "§c/gremio tecnologias investigar <id>");
                    return;
                }
                String err = plugin.tech.start(p, g, t);
                if (err != null) Msg.to(p, "§c" + err);
            }
            case "cancelar" -> {
                Tech t = a.length > 1 ? TechTree.ALL.get(a[1].toLowerCase(Locale.ROOT)) : null;
                if (t == null) {
                    Msg.to(p, "§c/gremio tecnologias cancelar <id>");
                    return;
                }
                String err = plugin.tech.cancel(p, g, t);
                if (err != null) Msg.to(p, "§c" + err);
            }
            default -> plugin.techGui.open(p);
        }
    }

    // ------------------------------------------------------------------ diplomacia
    public void diplo(Player p, Guild g, String[] a) {
        if (g == null) {
            Msg.to(p, "§cNecesitas un gremio.");
            return;
        }
        var d = plugin.diplo;
        String sub = a.length > 0 ? a[0].toLowerCase(Locale.ROOT) : "menu";
        String err = null;
        switch (sub) {
            case "estado" -> {
                Msg.to(p, "§cDiplomacia de §6" + g.name);
                for (var w : d.warsOf(g)) {
                    Guild o = plugin.guilds.byId(w.other(g));
                    if (o != null) p.sendMessage(" §c⚔ §7Guerra con §6" + o.name + " §7" + w.score(g) + "-" + w.score(o) + " §8(" + Msg.dur(Math.max(0, w.end - System.currentTimeMillis())) + ")");
                }
                Guild lord = d.lordOf(g);
                if (lord != null) p.sendMessage(" §6👑 §7Vasallo de §6" + lord.name);
                for (Guild v : d.vassalsOf(g)) p.sendMessage(" §6👑 §7Vasallo: §6" + v.name);
                for (Guild al : plugin.guilds.all()) if (g.allies.contains(al.id)) p.sendMessage(" §a🤝 §7Aliado: §6" + al.name);
                for (var pr : d.proposalsFor(g)) {
                    Guild f = plugin.guilds.byId(pr.from);
                    if (f != null) p.sendMessage(" §e✉ §7" + pr.type.toLowerCase() + " de §6" + f.name + " §8(/gremio diplomacia aceptar " + f.name + ")");
                }
            }
            case "guerra", "declarar" -> {
                Guild o = guildArg(a, 1);
                err = o == null ? "/gremio diplomacia guerra <gremio>" : d.declareWar(p, g, o);
            }
            case "paz" -> {
                Guild o = guildArg(a, 1);
                err = o == null ? "/gremio diplomacia paz <gremio>" : d.peace(p, g, o);
                if (err == null) Msg.to(p, "§aPaz gestionada.");
            }
            case "rendirse" -> {
                boolean vas = a.length > 1 && a[a.length - 1].equalsIgnoreCase("vasallo");
                Guild o = guildArg(Arrays.copyOf(a, vas ? a.length - 1 : a.length), 1);
                err = o == null ? "/gremio diplomacia rendirse <gremio> [vasallo]" : d.surrender(p, g, o, vas);
            }
            case "vasallo", "vasallaje" -> {
                String v = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "";
                Guild o = guildArg(a, 2);
                if (o == null) {
                    err = "/gremio diplomacia vasallo <ofrecer|pedir|liberar|aceptar|rechazar> <gremio>";
                    break;
                }
                switch (v) {
                    case "ofrecer" -> err = d.offerVassal(p, g, o);
                    case "pedir" -> err = d.askProtection(p, g, o);
                    case "liberar" -> err = d.release(p, g, o);
                    case "aceptar", "rechazar" -> err = respond(p, g, o, v.equals("aceptar"));
                    default -> err = "/gremio diplomacia vasallo <ofrecer|pedir|liberar|aceptar|rechazar> <gremio>";
                }
            }
            case "aceptar", "rechazar" -> {
                Guild o = guildArg(a, 1);
                err = o == null ? "/gremio diplomacia " + sub + " <gremio>" : respond(p, g, o, sub.equals("aceptar"));
            }
            case "independencia" -> err = d.independence(p, g);
            default -> {
                plugin.diploGui.open(p);
                return;
            }
        }
        if (err != null) Msg.to(p, "§c" + err);
        else if (!sub.equals("estado")) Msg.to(p, "§aHecho.");
    }

    private String respond(Player p, Guild me, Guild from, boolean accept) {
        DiplomacyManager.Proposal pr = null;
        for (var x : plugin.diplo.proposalsFor(me)) if (x.from.equals(from.id)) pr = x;
        if (pr == null) return from.name + " no te ha enviado ninguna propuesta.";
        return accept ? plugin.diplo.accept(p, me, pr) : plugin.diplo.reject(p, me, pr);
    }
}
