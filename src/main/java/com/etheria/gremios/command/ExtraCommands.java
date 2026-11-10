package com.etheria.gremios.command;

import com.etheria.gremios.GremiosPlugin;
import com.etheria.gremios.manager.RelicManager;
import com.etheria.gremios.manager.RelicManager.Relic;
import com.etheria.gremios.model.Guild;
import com.etheria.gremios.model.Rank;
import com.etheria.gremios.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Subcomandos de temporadas, habitaciones y reliquias (jugador y admin). */
public class ExtraCommands {
    private final GremiosPlugin plugin;

    public ExtraCommands(GremiosPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ jugador

    public void ranking(Player p) {
        plugin.seasonGui.open(p);
    }

    public void habitaciones(Player p, String[] a) {
        if (a.length > 0 && a[0].equalsIgnoreCase("varita")) {
            Guild g = plugin.guilds.of(p);
            if (g == null || !plugin.rooms.canManage(p, g)) {
                Msg.to(p, "§cSolo " + plugin.rooms.manageRank().display + " o superior.");
                return;
            }
            plugin.rooms.giveWand(p);
            Msg.to(p, "§dVarita entregada. Clic izq = punto 1, clic der = punto 2.");
            return;
        }
        plugin.roomGui.open(p);
    }

    public void casa(Player p) {
        Guild g = plugin.guilds.of(p);
        var r = g == null ? null : plugin.rooms.ownedBy(g, p.getUniqueId());
        if (r == null) {
            Msg.to(p, "§cNo tienes una casa asignada. Pídele a un líder que te asigne una habitación.");
            return;
        }
        String err = plugin.rooms.teleport(p, r);
        if (err != null) Msg.to(p, "§c" + err);
    }

    public void reliquias(Player p) {
        plugin.relicGui.open(p);
    }

    public void altar(Player p) {
        Guild g = plugin.guilds.of(p);
        if (g == null || g.rankOf(p.getUniqueId()) != Rank.LIDER) {
            Msg.to(p, "§cSolo el líder puede fijar el altar.");
            return;
        }
        String err = plugin.relics.setAltar(p, g);
        Msg.to(p, err == null ? "§dAltar fijado aquí. Tus reliquias se mostrarán en este punto." : "§c" + err);
    }

    // ------------------------------------------------------------------ admin

    public void adminSeason(CommandSender s, String[] a) {
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "estado";
        var sm = plugin.seasons;
        switch (sub) {
            case "estado" -> {
                s.sendMessage("§6Temporada §f" + sm.season + " §7— semana §f" + sm.weekInSeason + "§7/§f" + sm.weeksPerSeason());
                s.sendMessage("§7Cierre de semana en §f" + Msg.dur(sm.weekLeftMs()) + " §7| fin de temporada en §f" + Msg.dur(sm.seasonLeftMs()));
                s.sendMessage("§7Activo: §f" + sm.enabled());
            }
            case "cerrarsemana" -> {
                sm.closeWeek();
                s.sendMessage("§aSemana cerrada y premios repartidos.");
            }
            case "cerrartemporada" -> {
                sm.closeSeason();
                s.sendMessage("§aTemporada cerrada y premios repartidos.");
            }
            case "puntos" -> {
                if (a.length < 4) {
                    s.sendMessage("§c/gremioadmin temporada puntos <jugador> <n>");
                    return;
                }
                Player t = Bukkit.getPlayerExact(a[2]);
                if (t == null) {
                    s.sendMessage("§cEse jugador debe estar conectado.");
                    return;
                }
                try {
                    sm.addPoints(t, Long.parseLong(a[3]));
                    s.sendMessage("§aPuntos añadidos a " + t.getName() + ".");
                } catch (NumberFormatException e) {
                    s.sendMessage("§cNúmero inválido.");
                }
            }
            case "reset" -> {
                if (a.length < 3 || !a[2].equalsIgnoreCase("confirmar")) {
                    s.sendMessage("§cEsto borra TODO el ranking. Repite con: §f/gremioadmin temporada reset confirmar");
                    return;
                }
                sm.resetAll();
                s.sendMessage("§eRanking reiniciado.");
            }
            default -> s.sendMessage("§e/gremioadmin temporada <estado|cerrarsemana|cerrartemporada|puntos <jugador> <n>|reset confirmar>");
        }
    }

    public void adminRelic(CommandSender s, String[] a) {
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "lista";
        var rm = plugin.relics;
        Player p = s instanceof Player pl ? pl : null;
        switch (sub) {
            case "lista" -> {
                if (rm.all().isEmpty()) s.sendMessage("§7No hay reliquias.");
                for (Relic r : rm.all()) {
                    Guild g = r.holder == null ? null : plugin.guilds.byId(r.holder);
                    s.sendMessage("§d" + r.id + " §7— §f" + r.name + " §7— " + (r.holder == null ? "§alibre" + (r.placed ? " (colocada)" : " (sin colocar)") : "§6" + (g == null ? "?" : g.name)));
                }
            }
            case "info" -> {
                Relic r = a.length > 2 ? rm.get(a[2]) : null;
                if (r == null) {
                    s.sendMessage("§c/gremioadmin reliquia info <id>");
                    return;
                }
                s.sendMessage("§d" + r.name + " §7(" + r.icon + ")");
                for (String l : RelicManager.effectLines(r)) s.sendMessage(l);
            }
            case "crear" -> {
                if (a.length < 5) {
                    s.sendMessage("§c/gremioadmin reliquia crear <id> <MATERIAL> <nombre...>");
                    return;
                }
                Material m = Material.matchMaterial(a[3]);
                if (m == null) {
                    s.sendMessage("§cMaterial inválido.");
                    return;
                }
                String err = rm.create(a[2], String.join(" ", Arrays.copyOfRange(a, 4, a.length)), m, "", new LinkedHashMap<>());
                s.sendMessage(err == null ? "§aReliquia creada. Añade efectos con §f/gremioadmin reliquia efecto " + a[2].toLowerCase(Locale.ROOT) + " <clave> <valor>" : "§c" + err);
            }
            case "efecto" -> {
                Relic r = a.length > 2 ? rm.get(a[2]) : null;
                if (r == null || a.length < 5) {
                    s.sendMessage("§c/gremioadmin reliquia efecto <id> <clave> <valor>  §7(0 = quitar). Claves: " + String.join(", ", RelicManager.effectKeys()));
                    return;
                }
                try {
                    String err = rm.setEffect(r, a[3].toLowerCase(Locale.ROOT), Double.parseDouble(a[4]));
                    s.sendMessage(err == null ? "§aEfecto actualizado." : "§c" + err);
                } catch (NumberFormatException e) {
                    s.sendMessage("§cValor inválido.");
                }
            }
            case "descripcion" -> {
                Relic r = a.length > 2 ? rm.get(a[2]) : null;
                if (r == null || a.length < 4) {
                    s.sendMessage("§c/gremioadmin reliquia descripcion <id> <texto...>");
                    return;
                }
                r.desc = String.join(" ", Arrays.copyOfRange(a, 3, a.length));
                rm.save();
                s.sendMessage("§aDescripción actualizada.");
            }
            case "colocar" -> {
                Relic r = a.length > 2 ? rm.get(a[2]) : null;
                if (r == null || p == null) {
                    s.sendMessage("§c/gremioadmin reliquia colocar <id> §7(en el sitio donde estás; solo jugadores)");
                    return;
                }
                rm.place(r, p.getLocation());
                s.sendMessage("§aReliquia colocada aquí y libre. Clic derecho para reclamarla.");
            }
            case "devolver" -> {
                Relic r = a.length > 2 ? rm.get(a[2]) : null;
                if (r == null) {
                    s.sendMessage("§c/gremioadmin reliquia devolver <id>");
                    return;
                }
                if (r.holder != null) {
                    Guild g = plugin.guilds.byId(r.holder);
                    if (g != null) {
                        r.holder = null;
                        rm.onGuildGone(g); // libera todas las del gremio
                    }
                }
                s.sendMessage("§aReliquia liberada.");
            }
            case "borrar" -> s.sendMessage(a.length > 2 && rm.delete(a[2]) ? "§aReliquia borrada." : "§cNo existe.");
            default -> s.sendMessage("§e/gremioadmin reliquia <lista|info|crear|efecto|descripcion|colocar|devolver|borrar>");
        }
    }

    public static Map<String, String> unused() {
        return Map.of();
    }
}
