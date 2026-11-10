package com.etheria.gremios.command;

import com.etheria.gremios.GremiosPlugin;
import com.etheria.gremios.manager.SiegeItems;
import com.etheria.gremios.model.Guild;
import com.etheria.gremios.model.Territory;
import com.etheria.gremios.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** /gremioadmin ... */
public class GremioAdminCommand implements CommandExecutor, TabCompleter {
    private final GremiosPlugin plugin;

    public GremioAdminCommand(GremiosPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        if (!s.hasPermission("gremios.admin")) {
            s.sendMessage("§cSin permiso.");
            return true;
        }
        if (args.length == 0) {
            help(s);
            return true;
        }
        Player p = s instanceof Player pl ? pl : null;
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "panel" -> {
                if (p == null) s.sendMessage("§cSolo jugadores.");
                else plugin.guis.admin(p);
            }
            case "moneda" -> currency(s, p, args);
            case "permitir" -> {
                OfflinePlayer t = target(s, args, 1);
                if (t == null) return true;
                plugin.guilds.setCanCreate(t.getUniqueId(), true);
                s.sendMessage("§a" + t.getName() + " §7puede fundar un gremio.");
                Player on = t.getPlayer();
                if (on != null) Msg.to(on, "§aUn administrador te ha dado permiso para fundar un gremio. Usa §f/gremio§a.");
            }
            case "quitar" -> {
                OfflinePlayer t = target(s, args, 1);
                if (t == null) return true;
                plugin.guilds.setCanCreate(t.getUniqueId(), false);
                s.sendMessage("§7Permiso retirado a " + t.getName() + ".");
            }
            case "bypass" -> {
                if (p == null) {
                    s.sendMessage("§cSolo jugadores.");
                    return true;
                }
                if (!plugin.bypass.remove(p.getUniqueId())) {
                    plugin.bypass.add(p.getUniqueId());
                    Msg.to(p, "§aBypass de protección ACTIVADO.");
                } else Msg.to(p, "§7Bypass de protección desactivado.");
            }
            case "evento", "eventos" -> new EventoCommand(plugin).run(s, args);
            case "zona", "zonas" -> new ZonaCommand(plugin).run(s, args);
            case "mision", "misiones" -> new MisionCommand(plugin).run(s, args);
            case "tablon" -> new TablonCommand(plugin).run(s, args);
            case "tecnologia", "tecnologias", "tech" -> adminTech(s, args);
            case "diplomacia", "dip" -> adminDiplo(s, args);
            case "temporada", "temporadas", "ranking" -> new ExtraCommands(plugin).adminSeason(s, args);
            case "reliquia", "reliquias" -> new ExtraCommands(plugin).adminRelic(s, args);
            case "discord" -> {
                String dsub = args.length > 1 ? args[1].toLowerCase() : "";
                switch (dsub) {
                    case "webhook" -> {
                        if (args.length < 3) s.sendMessage("§e/gremioadmin discord webhook <url>");
                        else {
                            String err = plugin.discord.setWebhook(args[2]);
                            s.sendMessage(err == null ? "§aWebhook guardado y Discord activado. Prueba con §f/gremioadmin discord probar" : "§c" + err);
                        }
                    }
                    case "activar" -> {
                        plugin.discord.setActive(true);
                        s.sendMessage("§aAnuncios a Discord activados.");
                    }
                    case "apagar" -> {
                        plugin.discord.setActive(false);
                        s.sendMessage("§eAnuncios a Discord apagados.");
                    }
                    case "probar", "test" -> plugin.discord.test(s);
                    case "anunciar" -> plugin.discord.custom(s, String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)));
                    case "estado" -> {
                        s.sendMessage("§7Origen del webhook: §f" + plugin.discord.source() + " §7(" + plugin.discord.masked() + ") | activo: §f" + plugin.discord.active());
                        if (plugin.discord.problem() != null) s.sendMessage("§c" + plugin.discord.problem());
                        s.sendMessage("§6Discord: " + (plugin.discord.enabled() ? "§aACTIVO" : "§cAPAGADO") + " §7| webhook: " + (plugin.discord.hasWebhook() ? "§aconfigurado" : "§cfalta"));
                        for (String k : com.etheria.gremios.manager.DiscordManager.KINDS)
                            s.sendMessage(" §7" + com.etheria.gremios.manager.DiscordManager.label(k) + ": " + (plugin.discord.typeOn(k) ? "§así" : "§cno"));
                    }
                    default -> {
                        if (!dsub.isEmpty()) s.sendMessage("§cSubcomando desconocido: " + dsub + ". §7Si no existe 'webhook', tu plugin es una versión vieja: mira /gremioadmin version.");
                        if (dsub.isEmpty() && s instanceof org.bukkit.entity.Player pl) plugin.extraGuis.discord(pl);
                        else s.sendMessage("§e/gremioadmin discord webhook <url>|probar|estado|activar|apagar|anunciar <texto>");
                    }
                }
            }
            case "web", "mapaweb" -> {
                String ws = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "estado";
                if (plugin.web == null) {
                    s.sendMessage("§cEl mapa web aún no está listo.");
                } else if (ws.equals("reiniciar")) {
                    plugin.reloadConfig();
                    plugin.web.stop();
                    plugin.web.start();
                    s.sendMessage(plugin.web.running() ? "§aMapa web reiniciado: §f" + plugin.web.link() : "§cNo se pudo iniciar (mira la consola: puerto ocupado o web.activo: false).");
                } else if (ws.equals("ocultar")) {
                    Player tp = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : (s instanceof Player pl0 ? pl0 : null);
                    if (tp == null) s.sendMessage("§c/gremioadmin web ocultar <jugador> §7(debe estar en línea)");
                    else s.sendMessage("§a" + tp.getName() + (plugin.web.toggleHidden(tp) ? " ahora está OCULTO en el mapa." : " ahora APARECE en el mapa."));
                } else if (ws.equals("todo") || ws.equals("dibujar")) {
                    String wn = args.length > 2 ? args[2] : (s instanceof Player pl ? pl.getWorld().getName() : Bukkit.getWorlds().get(0).getName());
                    String err = plugin.web.renderAll(wn);
                    s.sendMessage(err == null ? "§aDibujando TODO el mundo §f" + wn + "§a hasta su borde, en segundo plano. Sigue con §f/gremioadmin web estado§a. Puedes ver el mapa mientras tanto; para cancelar: §f/gremioadmin web parar" : "§c" + err);
                } else if (ws.equals("parar")) {
                    plugin.web.stopAll();
                    s.sendMessage("§eDibujado detenido (se conserva lo ya hecho).");
                } else if (ws.equals("renderizar")) {
                    int rad = 0;
                    try {
                        rad = args.length > 2 ? Integer.parseInt(args[2]) : 0;
                    } catch (NumberFormatException ignored) {
                    }
                    s.sendMessage("§aDibujando el terreno alrededor de territorios, zonas, eventos y spawn (" + plugin.web.prerender(rad) + " teselas). Tarda un poco; mira la consola.");
                } else if (ws.equals("limpiar")) {
                    plugin.web.clearTiles();
                    s.sendMessage("§aCaché de terreno borrada. Se vuelve a dibujar al abrir el mapa (o con §f/gremioadmin web renderizar§a).");
                } else if (ws.equals("refrescar")) {
                    plugin.web.refresh();
                    s.sendMessage("§aDatos del mapa actualizados.");
                } else {
                    s.sendMessage("§6Mapa web: " + (plugin.web.running() ? "§aENCENDIDO" : "§cAPAGADO"));
                    if (plugin.web.running()) {
                        s.sendMessage("§7Puerto: §f" + plugin.web.port() + " §7| Enlace: §b" + plugin.web.link());
                        s.sendMessage("§7Texturas: §f" + plugin.web.textures().status());
                        s.sendMessage("§7Dibujado completo: §f" + plugin.web.allStatus());
                        s.sendMessage("§8En este mismo PC: http://localhost:" + plugin.web.port() + " . Para otros jugadores hay que abrir el puerto (ver guía).");
                    }
                    s.sendMessage("§7/gremioadmin web §ereiniciar|refrescar|todo [mundo]|parar|renderizar [radio]|limpiar|ocultar [jugador]");
                }
            }
            case "version", "build" -> s.sendMessage("§6GremiosGUI §7build §f" + com.etheria.gremios.GremiosPlugin.BUILD);
            case "ayuda", "help" -> ayuda(s, args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "");
            case "npc", "npcs" -> new NpcCommand(plugin).run(s, args);
            case "modelo", "modelos" -> new ModeloCommand(plugin).run(s, args);
            case "diagnostico", "diag" -> new DiagnosticCommand(plugin).run(s);
            case "asistente", "setup" -> new SetupAssistant(plugin, s).run();
            case "auditoria", "audit" -> {
                int n = 15;
                StringBuilder f = new StringBuilder();
                for (int i = 1; i < args.length; i++) {
                    try {
                        n = Math.max(1, Math.min(60, Integer.parseInt(args[i])));
                    } catch (NumberFormatException ex) {
                        if (f.length() > 0) f.append(' ');
                        f.append(args[i]);
                    }
                }
                List<String> rows = plugin.audit.recent(f.toString(), n);
                s.sendMessage("§6Auditoría §7(últimas " + rows.size() + (f.length() > 0 ? ", filtro: " + f : "") + ")");
                if (rows.isEmpty()) s.sendMessage("§7Nada registrado todavía.");
                for (String r : rows) s.sendMessage(com.etheria.gremios.manager.AuditManager.pretty(r));
                s.sendMessage("§8Uso: /gremioadmin auditoria [gremio|jugador|acción] [n]  — archivo: auditoria.log");
            }
            case "dar" -> give(s, args);
            case "raid" -> raid(s, args);
            case "xp" -> {
                Guild g = guild(s, args, 1);
                if (g == null) return true;
                long n = num(s, args, 2);
                if (n < 0) return true;
                plugin.guilds.addXp(g, n);
                plugin.guilds.save();
                s.sendMessage("§a+" + n + " XP a " + g.name + " (nivel " + g.level + ").");
            }
            case "nivel" -> {
                Guild g = guild(s, args, 1);
                if (g == null) return true;
                long n = num(s, args, 2);
                if (n < 1 || n > Guild.MAX_LEVEL) {
                    s.sendMessage("§cNivel 1-" + Guild.MAX_LEVEL);
                    return true;
                }
                g.level = (int) n;
                g.xp = 0;
                plugin.guilds.save();
                s.sendMessage("§aNivel de " + g.name + " = " + n);
            }
            case "banco" -> {
                Guild g = guild(s, args, 1);
                if (g == null) return true;
                long n;
                try {
                    n = Long.parseLong(args.length > 2 ? args[2] : "x");
                } catch (NumberFormatException ex) {
                    s.sendMessage("§cCantidad inválida (puede ser negativa).");
                    return true;
                }
                plugin.audit.log(g, s.getName(), "BANCO_ADMIN", (n >= 0 ? "+" : "") + n);
                g.bank = Math.max(0, g.bank + n);
                g.log("§d" + (n >= 0 ? "+" : "") + n + " §7admin");
                plugin.guilds.save();
                s.sendMessage("§aBanco de " + g.name + ": " + g.bank);
            }
            case "disolver" -> {
                Guild g = guild(s, args, 1);
                if (g == null) return true;
                String n = g.name;
                plugin.guilds.disband(g);
                Bukkit.broadcastMessage(Msg.PREFIX + "§7El gremio §e" + n + " §7ha sido disuelto por un administrador.");
            }
            case "reload" -> {
                plugin.reloadConfig();
                s.sendMessage("§aConfiguración recargada. (Para cambios en layouts.yml/recursos reinicia el servidor.)");
            }
            default -> help(s);
        }
        return true;
    }

    private void currency(CommandSender s, Player p, String[] args) {
        String a = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "info";
        switch (a) {
            case "set" -> {
                if (p == null) {
                    s.sendMessage("§cSolo jugadores.");
                    return;
                }
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (hand == null || hand.getType().isAir()) {
                    Msg.to(p, "§cSostén el objeto que será la moneda.");
                    return;
                }
                plugin.currency.set(hand);
                Msg.to(p, "§aMoneda definida: §f" + plugin.currency.name() + "§a. Se compara el objeto exacto (nombre, modelo y datos).");
            }
            case "dar" -> {
                if (p == null || !plugin.currency.isSet()) {
                    s.sendMessage("§cDefine la moneda primero (y usa esto como jugador).");
                    return;
                }
                long n = num(s, args, 2);
                if (n <= 0) return;
                plugin.currency.give(p, n);
                Msg.to(p, "§aRecibiste §e" + plugin.currency.fmt(n) + "§a.");
            }
            default -> s.sendMessage(plugin.currency.isSet() ? "§7Moneda actual: §f" + plugin.currency.name() + " §7(" + plugin.currency.material() + ")"
                    : "§cNo hay moneda definida. Sostén el objeto y usa §f/gremioadmin moneda set§c.");
        }
    }

    private void give(CommandSender s, String[] args) {
        if (args.length < 3) {
            s.sendMessage("§cUso: /gremioadmin dar <jugador> <canon|catapulta|MUNICION> [cantidad]");
            return;
        }
        Player t = Bukkit.getPlayerExact(args[1]);
        if (t == null) {
            s.sendMessage("§cJugador no conectado.");
            return;
        }
        int n = 1;
        if (args.length > 3) {
            try {
                n = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
            } catch (NumberFormatException ignored) {
            }
        }
        String id = args[2].toUpperCase(Locale.ROOT);
        ItemStack it = null;
        if (id.equals("CANON") || id.equals("CAÑON") || id.equals("CAÑÓN")) it = SiegeItems.unitItem(plugin, SiegeItems.UnitType.CANON);
        else if (id.equals("CATAPULTA")) it = SiegeItems.unitItem(plugin, SiegeItems.UnitType.CATAPULTA);
        else {
            try {
                it = SiegeItems.ammoItem(plugin, SiegeItems.Ammo.valueOf(id), n);
                n = 1;
            } catch (IllegalArgumentException ex) {
                s.sendMessage("§cOpciones: canon, catapulta, " + Arrays.toString(SiegeItems.Ammo.values()));
                return;
            }
        }
        for (int i = 0; i < n; i++) for (ItemStack o : t.getInventory().addItem(it.clone()).values()) t.getWorld().dropItemNaturally(t.getLocation(), o);
        s.sendMessage("§aEntregado.");
    }

    private void raid(CommandSender s, String[] args) {
        String a = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (a) {
            case "forzar" -> {
                if (args.length < 4) {
                    s.sendMessage("§cUso: /gremioadmin raid forzar <gremioAtacante> <territorio>");
                    return;
                }
                Guild g = plugin.guilds.byName(args[2]);
                Territory t = plugin.territories.get(args[3]);
                if (g == null || t == null) {
                    s.sendMessage("§cGremio o territorio no encontrado.");
                    return;
                }
                String err = plugin.raids.forceStart(g, t);
                s.sendMessage(err == null ? "§aAsedio iniciado." : "§c" + err);
            }
            case "prueba" -> {
                if (args.length < 3) {
                    s.sendMessage("§cUso: /gremioadmin raid prueba <territorio> [minutos]  (territorio sin dueño)");
                    return;
                }
                Territory t = plugin.territories.get(args[2]);
                if (t == null) {
                    s.sendMessage("§cTerritorio no encontrado.");
                    return;
                }
                int min = 20;
                if (args.length > 3) {
                    try {
                        min = Math.max(1, Math.min(240, Integer.parseInt(args[3])));
                    } catch (NumberFormatException ex) {
                        s.sendMessage("§cMinutos inválidos.");
                        return;
                    }
                }
                String err = plugin.raids.startTest(t, min);
                s.sendMessage(err == null ? "§aRaid de prueba iniciada. Usa §f/gremioadmin dar <tú> canon§a, coloca el arma, apunta (clic derecho) y dispara (clic izquierdo)." : "§c" + err);
            }
            case "terminar" -> {
                if (args.length < 4) {
                    s.sendMessage("§cUso: /gremioadmin raid terminar <territorio> <atacante|defensor|saqueo|cancelar>");
                    return;
                }
                Territory t = plugin.territories.get(args[2]);
                if (t == null) {
                    s.sendMessage("§cTerritorio no encontrado.");
                    return;
                }
                String err = plugin.raids.forceEnd(t, args[3]);
                s.sendMessage(err == null ? "§aHecho." : "§c" + err);
            }
            case "historial" -> {
                var list = plugin.history.recent(args.length > 2 ? args[2] : null, 15);
                if (list.isEmpty()) s.sendMessage("§7Sin asedios en el historial.");
                for (var e : list) s.sendMessage(plugin.history.line(e));
            }
            case "lista" -> {
                if (plugin.raids.all().isEmpty()) s.sendMessage("§7No hay asedios.");
                for (var r : plugin.raids.all()) s.sendMessage("§e" + r.territoryId + " §7" + r.attackerName + " → " + r.defenderName + " §8| " + r.state);
            }
            default -> s.sendMessage("§c/gremioadmin raid <prueba|forzar|terminar|lista|historial> ...");
        }
    }

    private OfflinePlayer target(CommandSender s, String[] args, int idx) {
        if (args.length <= idx) {
            s.sendMessage("§cFalta el jugador.");
            return null;
        }
        Player on = Bukkit.getPlayerExact(args[idx]);
        if (on != null) return on;
        OfflinePlayer off = Bukkit.getOfflinePlayerIfCached(args[idx]);
        if (off == null) s.sendMessage("§cJugador desconocido (debe haber entrado al servidor).");
        return off;
    }

    private Guild guild(CommandSender s, String[] args, int idx) {
        if (args.length <= idx) {
            s.sendMessage("§cFalta el gremio.");
            return null;
        }
        Guild g = plugin.guilds.byName(args[idx]);
        if (g == null) s.sendMessage("§cGremio no encontrado.");
        return g;
    }

    private long num(CommandSender s, String[] args, int idx) {
        try {
            return Long.parseLong(args[idx]);
        } catch (Exception ex) {
            s.sendMessage("§cNúmero inválido.");
            return -1;
        }
    }

    private void ayuda(CommandSender s, String tema) {
        String[] l = switch (tema) {
            case "inicio", "empezar" -> new String[]{
                    "§6Puesta en marcha:",
                    "§e1§7 /gremioadmin moneda set §8(objeto en la mano)",
                    "§e2§7 /territorio varita → 2 esquinas → /territorio crear <id> <nombre>",
                    "§e3§7 precio, setspawn, setnodo, npc, setbandera, puertas, snapshot",
                    "§e4§7 Resourcepack en server.properties",
                    "§e5§7 /gremioadmin permitir <jugador>",
                    "§e6§7 Comprueba: /gremioadmin diagnostico y /gremioadmin npc estado"};
            case "territorios", "territorio" -> new String[]{
                    "§6/territorio §evarita|crear|borrar|lista|info|tp",
                    "§7precio <id> <base> [fortaleza] [reinado] §8| §7setspawn|setnodo|setbandera <id>",
                    "§7npc <id> <rol> §8| §7snapshot|reset|liberar <id>",
                    "§7puerta crear|borrar|lista|abrir|cerrar|vida|control ..."};
            case "zonas", "zona" -> new String[]{
                    "§6/gremioadmin zona §emenu|lista|info <id>",
                    "§7crear <id> <MINA|TALA|CANTERA|GRANJA|PESCA> <nombre> → area → npc → recurso agregar",
                    "§7cada <min> | capacidad <n> | captura <seg> | liberar | borrar"};
            case "eventos", "evento" -> new String[]{
                    "§6/gremioadmin evento §emenu|lista|info|crear <id> <koth|jefe> <nombre>",
                    "§7zona <id> (varita) | iniciar | parar | borrar",
                    "§7duracion|mantener|xp|coronas|cada|nivel|jefe|activo|gremios <id> <valor>"};
            case "misiones", "mision", "tablon" -> new String[]{
                    "§6/gremioadmin mision §ecrear <id> <entregar|matar|minar> <objetivo> <cant> <xp> <unica|diaria|semanal> <nombre>",
                    "§7coronas|activa|borrar|lista | §6tablon §ecrear <id>|borrar <id>|lista"};
            case "npc", "modelos", "modelo" -> new String[]{
                    "§6/gremioadmin npc §eestado|reparar §8| §6modelo §eprobar|giro <grados>|refrescar",
                    "§7Sin modelo o invisible = resourcepack sin aplicar o antiguo."};
            case "web", "mapaweb" -> new String[]{
                    "§6/gremioadmin web §e(estado)|reiniciar|refrescar|renderizar [radio]|limpiar|ocultar [jugador] §7- mapa web propio",
                    "§7Config: web.activo, web.puerto, web.url, web.clave, web.mostrar-jugadores"};
            case "discord" -> new String[]{
                    "§6/gremioadmin discord §e(menú)|webhook <url>|probar|estado|activar|apagar|anunciar <texto>"};
            case "justicia", "antiabuso" -> new String[]{
                    "§6Justicia (config justicia.*): §7ratio-maximo, ratio-penalizacion, botin-minimo-pct, defensores-pct, noche.*",
                    "§6Anti-abuso (config antiabuso.*): §7espera-tras-salir-horas, bloquear-retiro/salida/entrada-en-asedio, retiro-nuevo-miembro-horas",
                    "§7Registro: §e/gremioadmin auditoria [filtro] [n]"};
            case "vault" -> new String[]{
                    "§6Vault: §7instala Vault + un plugin de economía y pon §ecurrency.modo: vault §7en config.yml (reinicia).",
                    "§7Todo el banco, tiendas y costes usarán el dinero de Vault en vez del objeto físico."};
            case "resourcepack" -> new String[]{
                    "§6Resourcepack: §7sube el .zip con su SHA-1 y ponlo en server.properties (resource-pack, resource-pack-sha1, require-resource-pack).",
                    "§7Jugadores Bedrock: ven menús simples automáticamente (compat.bedrock.menus-simples)."};
            case "placeholders" -> new String[]{
                    "§6Placeholders: §7instala PlaceholderAPI; GremiosGUI registra §e%gremio_<nombre>%§7 solo."};
            case "asedios", "raid" -> new String[]{
                    "§6/gremioadmin raid §elista|prueba <terr> [min]|forzar <gremio> <terr>|terminar <terr> <atacante|defensor|saqueo|cancelar>|historial",
                    "§7dar <jugador> <canon|catapulta|MUNICION> [n]"};
            default -> new String[]{
                    "§6Ayuda de admin §7(guía completa en GUIA-ADMIN.md). Temas: §e/gremioadmin ayuda <tema>",
                    "§7inicio · territorios · zonas · eventos · misiones · npc · discord · asedios · justicia · vault · resourcepack · placeholders",
                    "§7§e/gremioadmin asistente §7(pasos guiados) · §e/gremioadmin auditoria §7(registro de acciones)",
                    "§7Además: moneda, permitir|quitar, bypass, xp|nivel|banco|disolver <gremio> <n>, reload, diagnostico, panel"};
        };
        for (String x : l) s.sendMessage(x);
    }

    private void help(CommandSender s) {
        s.sendMessage("§6/gremioadmin §emoneda set §7(objeto en la mano) | info | dar <n>");
        s.sendMessage("§6/gremioadmin §epermitir|quitar <jugador> §7- permiso para fundar gremio");
        s.sendMessage("§6/gremioadmin §ebypass §7- ignora protecciones");
        s.sendMessage("§6/gremioadmin §etemporada §7<estado|cerrarsemana|cerrartemporada|puntos|reset> §7| §ereliquia §7<lista|crear|efecto|colocar|devolver|borrar>");
        s.sendMessage("§6/gremioadmin §edar <jugador> <canon|catapulta|MUNICION> [n]");
        s.sendMessage("§6/gremioadmin §eraid prueba|forzar|terminar|lista ...");
        s.sendMessage("§6/gremioadmin §exp|nivel|banco <gremio> <n> §7| §edisolver <gremio> §7| §epanel §7| §ereload §7| §eayuda [tema]");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("gremios.admin")) return out;
        if (args.length == 1) {
            for (String x : List.of("panel", "moneda", "permitir", "quitar", "bypass", "dar", "raid", "xp", "nivel", "banco", "disolver", "reload", "evento", "zona", "mision", "tablon", "modelo", "npc", "discord", "web", "version", "ayuda", "diagnostico", "asistente", "auditoria", "temporada", "reliquia")) {
                if (x.startsWith(args[0].toLowerCase())) out.add(x);
            }
        } else if (args.length == 2) {
            String s = args[0].toLowerCase();
            if (s.equals("moneda")) out.addAll(List.of("set", "info", "dar"));
            else if (s.equals("evento")) out.addAll(EventoCommand.SUBS);
            else if (s.equals("zona")) out.addAll(ZonaCommand.SUBS);
            else if (s.equals("mision")) out.addAll(MisionCommand.SUBS);
            else if (s.equals("tablon")) out.addAll(TablonCommand.SUBS);
            else if (s.equals("modelo")) out.addAll(List.of("probar", "giro", "refrescar"));
            else if (s.equals("raid")) out.addAll(List.of("prueba", "forzar", "terminar", "lista", "historial"));
            else if (s.equals("permitir") || s.equals("quitar") || s.equals("dar")) {
                for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) out.add(p.getName());
            } else if (s.equals("xp") || s.equals("nivel") || s.equals("banco") || s.equals("disolver")) {
                for (Guild g : plugin.guilds.all()) if (g.name.toLowerCase().startsWith(args[1].toLowerCase())) out.add(g.name);
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("dar")) {
            out.addAll(List.of("canon", "catapulta"));
            for (SiegeItems.Ammo a : SiegeItems.Ammo.values()) out.add(a.name());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("raid")) {
            if (args[1].equalsIgnoreCase("forzar")) for (Guild g : plugin.guilds.all()) out.add(g.name);
            else for (Territory t : plugin.territories.all()) out.add(t.id);
        } else if (args.length == 4 && args[0].equalsIgnoreCase("raid")) {
            if (args[1].equalsIgnoreCase("forzar") || args[1].equalsIgnoreCase("prueba") || args[1].equalsIgnoreCase("terminar")) for (Territory t : plugin.territories.all()) out.add(t.id);
            else out.addAll(List.of("atacante", "defensor", "saqueo", "cancelar"));
        }
        return out;
    }

    // ------------------------------------------------------------------ tecnologías y diplomacia (admin)
    private void adminTech(org.bukkit.command.CommandSender s, String[] a) {
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "lista";
        if (sub.equals("lista")) {
            for (var b : com.etheria.gremios.tech.Tech.Branch.values()) {
                StringBuilder sb = new StringBuilder(b.color + b.display + "§7: ");
                for (var t : com.etheria.gremios.tech.TechTree.ALL.values()) if (t.branch == b) sb.append("§f").append(t.id).append("§8, ");
                s.sendMessage(sb.toString());
            }
            s.sendMessage("§7/gremioadmin tecnologia dar <gremio> <id|todas>  §8| §7quitar <gremio> <id>  §8| §7reset <gremio>  §8| §7info <gremio>");
            return;
        }
        if (a.length < 3) {
            s.sendMessage("§c/gremioadmin tecnologia <lista|dar|quitar|reset|info> <gremio> [id]");
            return;
        }
        Guild g = plugin.guilds.byName(a[2]);
        if (g == null) {
            s.sendMessage("§cNo existe ese gremio (si tiene espacios, usa guion bajo).");
            return;
        }
        switch (sub) {
            case "dar" -> {
                if (a.length < 4) {
                    s.sendMessage("§c/gremioadmin tecnologia dar <gremio> <id|todas>");
                    return;
                }
                if (a[3].equalsIgnoreCase("todas")) {
                    int n = 0;
                    for (var t : com.etheria.gremios.tech.TechTree.ALL.values()) if (plugin.tech.visible(t) && plugin.tech.grant(g, t.id)) n++;
                    s.sendMessage("§a" + n + " tecnologías concedidas a " + g.name + ".");
                } else s.sendMessage(plugin.tech.grant(g, a[3].toLowerCase(Locale.ROOT)) ? "§aConcedida." : "§cId desconocido.");
            }
            case "quitar" -> s.sendMessage(a.length > 3 && plugin.tech.revoke(g, a[3].toLowerCase(Locale.ROOT)) ? "§aQuitada." : "§cNo la tenía o falta el id.");
            case "reset" -> {
                plugin.tech.reset(g);
                s.sendMessage("§aTecnologías de " + g.name + " reiniciadas.");
            }
            default -> {
                s.sendMessage("§b" + g.name + " §7tiene §a" + plugin.tech.count(g) + "§7/§f" + plugin.tech.totalVisible() + " §7tecnologías. En curso: §f" + plugin.tech.doing(g).keySet());
                for (String k : new String[]{"miembros", "mantenimiento", "xp", "interes", "zona", "proteccion", "dmg-guerra", "def-terr", "cultivo", "minerales", "xp-orbes", "slots", "vasallos", "guerras", "tributo", "botin-guerra", "costo", "investigacion"}) {
                    double v = plugin.tech.bonus(g, k);
                    if (v != 0) s.sendMessage(" §8• §7" + k + ": §f" + v);
                }
            }
        }
    }

    private void adminDiplo(org.bukkit.command.CommandSender s, String[] a) {
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "lista";
        var d = plugin.diplo;
        switch (sub) {
            case "guerra", "paz", "vasallo" -> {
                if (a.length < 4) {
                    s.sendMessage("§c/gremioadmin diplomacia " + sub + " <gremioA> <gremioB>  §7(vasallo: <señor> <vasallo>)");
                    return;
                }
                Guild x = plugin.guilds.byName(a[2]), y = plugin.guilds.byName(a[3]);
                if (x == null || y == null) {
                    s.sendMessage("§cGremio no encontrado (usa guion bajo en vez de espacios).");
                    return;
                }
                String err = sub.equals("guerra") ? d.adminWar(x, y) : sub.equals("paz") ? d.adminPeace(x, y) : d.adminVassal(x, y);
                s.sendMessage(err == null ? "§aHecho." : "§c" + err);
            }
            case "liberar" -> {
                Guild v = a.length > 2 ? plugin.guilds.byName(a[2]) : null;
                s.sendMessage(v != null && d.adminRelease(v) ? "§aVasallo liberado." : "§cNo es vasallo o no existe.");
            }
            default -> {
                s.sendMessage("§cGuerras activas: §f" + d.allWars().size());
                for (var w : d.allWars()) {
                    Guild x = plugin.guilds.byId(w.a), y = plugin.guilds.byId(w.b);
                    s.sendMessage(" §c⚔ §f" + (x == null ? w.a : x.name) + " §7vs §f" + (y == null ? w.b : y.name) + " §8" + w.scoreA + "-" + w.scoreB);
                }
                s.sendMessage("§7/gremioadmin diplomacia guerra|paz|vasallo <A> <B>  §8| §7liberar <vasallo>");
            }
        }
    }
}
