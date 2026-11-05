package com.etheria.gremios.manager;

import com.etheria.gremios.GremiosPlugin;
import com.etheria.gremios.model.EventDef;
import com.etheria.gremios.model.Guild;
import com.etheria.gremios.model.Raid;
import com.etheria.gremios.model.Territory;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Mapa web propio (sin Dynmap): un mini servidor HTTP de solo lectura con una página que dibuja territorios,
 * zonas, eventos y tablones con iconos y colores de gremio. Los datos se preparan en el hilo principal y
 * el servidor HTTP solo entrega una copia en caché, así que no afecta al rendimiento del juego.
 */
public class WebMapManager {
    private final GremiosPlugin plugin;
    private HttpServer server;
    private ExecutorService pool;
    private BukkitTask task;
    private volatile String json = "{}";
    private volatile byte[] page;
    private int port;

    public WebMapManager(GremiosPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean running() {
        return server != null;
    }

    public int port() {
        return port;
    }

    private String key() {
        return plugin.getConfig().getString("web.clave", "").trim();
    }

    /** Enlace para dar a los jugadores. */
    public String link() {
        String u = plugin.getConfig().getString("web.url", "").trim();
        if (u.isEmpty()) {
            String ip = Bukkit.getIp();
            u = "http://" + (ip == null || ip.isBlank() ? "localhost" : ip) + ":" + port;
        }
        if (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return key().isEmpty() ? u : u + "/?k=" + key();
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("web.activo", true) || server != null) return;
        port = plugin.getConfig().getInt("web.puerto", 8123);
        try (InputStream in = plugin.getResource("web/index.html")) {
            page = in == null ? "<h1>Falta web/index.html</h1>".getBytes(StandardCharsets.UTF_8) : in.readAllBytes();
        } catch (IOException e) {
            page = "<h1>Error</h1>".getBytes(StandardCharsets.UTF_8);
        }
        try {
            String bind = plugin.getConfig().getString("web.bind", "0.0.0.0");
            server = HttpServer.create(new InetSocketAddress(bind, port), 0);
            pool = Executors.newFixedThreadPool(3, r -> {
                Thread t = new Thread(r, "GremiosGUI-Web");
                t.setDaemon(true);
                return t;
            });
            server.setExecutor(pool);
            server.createContext("/", this::handle);
            server.start();
        } catch (Exception e) {
            server = null;
            plugin.getLogger().warning("Mapa web: no se pudo abrir el puerto " + port + " (" + e.getMessage() + "). Cambia web.puerto o desactívalo con web.activo: false.");
            return;
        }
        refresh();
        long every = Math.max(20L, plugin.getConfig().getLong("web.refresco-segundos", 5) * 20L);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, every, every);
        plugin.getLogger().info("Mapa web en " + link());
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
        if (server != null) server.stop(0);
        server = null;
        if (pool != null) pool.shutdownNow();
        pool = null;
    }

    private void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            String q = ex.getRequestURI().getRawQuery();
            String k = key();
            if (!k.isEmpty() && (q == null || !q.contains("k=" + k))) {
                send(ex, 403, "text/plain; charset=utf-8", "Acceso denegado. Pide el enlace completo.".getBytes(StandardCharsets.UTF_8));
                return;
            }
            if (!"GET".equals(ex.getRequestMethod())) {
                send(ex, 405, "text/plain", new byte[0]);
            } else if (path.equals("/api/mapa")) {
                send(ex, 200, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
            } else if (path.equals("/") || path.equals("/index.html")) {
                send(ex, 200, "text/html; charset=utf-8", page);
            } else {
                send(ex, 404, "text/plain", "No encontrado".getBytes(StandardCharsets.UTF_8));
            }
        } finally {
            ex.close();
        }
    }

    private void send(HttpExchange ex, int code, String type, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        ex.sendResponseHeaders(code, body.length == 0 ? -1 : body.length);
        if (body.length > 0) try (OutputStream o = ex.getResponseBody()) {
            o.write(body);
        }
    }

    // ------------------------------------------------------------------ datos

    /** Color estable por gremio. */
    public static String colorOf(String id) {
        if (id == null) return "#888888";
        int h = Math.abs(id.hashCode()) % 360;
        double s = 0.65, l = 0.55;
        double c = (1 - Math.abs(2 * l - 1)) * s, x = c * (1 - Math.abs((h / 60.0) % 2 - 1)), m = l - c / 2;
        double r, g, b;
        if (h < 60) { r = c; g = x; b = 0; }
        else if (h < 120) { r = x; g = c; b = 0; }
        else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; }
        else if (h < 300) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        return String.format(Locale.ROOT, "#%02X%02X%02X", (int) ((r + m) * 255), (int) ((g + m) * 255), (int) ((b + m) * 255));
    }

    private static String q(String s) {
        if (s == null) return "null";
        StringBuilder b = new StringBuilder("\"");
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r', '\t' -> b.append(' ');
                default -> {
                    if (ch < 32) b.append(' ');
                    else b.append(ch);
                }
            }
        }
        return b.append('"').toString();
    }

    private static String clean(String s) {
        return s == null ? null : s.replaceAll("§[0-9a-fk-orA-FK-OR]", "");
    }

    private String rect(String world, int x1, int z1, int x2, int z2) {
        return "\"world\":" + q(world) + ",\"x1\":" + Math.min(x1, x2) + ",\"z1\":" + Math.min(z1, z2) + ",\"x2\":" + Math.max(x1, x2) + ",\"z2\":" + Math.max(z1, z2);
    }

    /** Se ejecuta en el hilo principal: copia el estado a un texto que luego sirve el hilo HTTP. */
    public void refresh() {
        try {
            StringBuilder b = new StringBuilder(4096);
            b.append("{\"servidor\":").append(q(plugin.getConfig().getString("web.titulo", "Crónicas de la Corona Rota")));
            b.append(",\"hora\":").append(System.currentTimeMillis());
            b.append(",\"mundos\":[");
            boolean f = true;
            for (World w : Bukkit.getWorlds()) {
                b.append(f ? "" : ",").append(q(w.getName()));
                f = false;
            }
            b.append("],\"gremios\":[");
            f = true;
            for (Guild g : plugin.guilds.all()) {
                b.append(f ? "" : ",").append("{\"id\":").append(q(g.id)).append(",\"nombre\":").append(q(clean(g.name)))
                        .append(",\"color\":").append(q(colorOf(g.id))).append(",\"nivel\":").append(g.level)
                        .append(",\"miembros\":").append(g.members.size()).append(",\"enLinea\":").append(plugin.guilds.online(g))
                        .append(",\"territorio\":").append(q(g.territoryId)).append("}");
                f = false;
            }
            b.append("],\"territorios\":[");
            f = true;
            for (Territory t : plugin.territories.all()) {
                Guild g = t.ownerGuildId == null ? null : plugin.guilds.byId(t.ownerGuildId);
                Raid raid = null;
                for (Raid r : plugin.raids.all()) if (t.id.equals(r.territoryId) && r.state == Raid.State.ACTIVE) raid = r;
                b.append(f ? "" : ",").append("{\"id\":").append(q(t.id)).append(",\"nombre\":").append(q(clean(t.name))).append(",")
                        .append(rect(t.world, t.minX, t.minZ, t.maxX, t.maxZ));
                b.append(",\"dueno\":").append(g == null ? "null" : q(clean(g.name)));
                b.append(",\"duenoId\":").append(g == null ? "null" : q(g.id));
                b.append(",\"color\":").append(q(g == null ? "#4CAF50" : colorOf(g.id)));
                b.append(",\"nivel\":").append(g == null ? 0 : g.territoryLevel);
                if (t.node != null && t.node.length >= 3) b.append(",\"nodo\":[").append((int) t.node[0]).append(",").append((int) t.node[2]).append("]");
                b.append(",\"asedio\":").append(raid == null ? "null" : "{\"atacante\":" + q(clean(raid.attackerName)) + ",\"defensor\":" + q(clean(raid.defenderName)) + "}");
                b.append("}");
                f = false;
            }
            b.append("],\"zonas\":[");
            f = true;
            for (ZoneManager.Zone z : plugin.zones.all()) {
                if (!z.hasArea) continue;
                String oid = z.owner != null && z.owner.startsWith("G:") ? z.owner.substring(2) : null;
                b.append(f ? "" : ",").append("{\"id\":").append(q(z.id)).append(",\"nombre\":").append(q(clean(z.name))).append(",\"tipo\":").append(q(z.kind))
                        .append(",").append(rect(z.world, z.minX, z.minZ, z.maxX, z.maxZ))
                        .append(",\"dueno\":").append(q(clean(z.ownerName))).append(",\"color\":").append(q(oid == null ? (z.owner == null ? "#9E9E9E" : "#B0B0B0") : colorOf(oid)))
                        .append(",\"cada\":").append(z.intervalMin).append("}");
                f = false;
            }
            b.append("],\"eventos\":[");
            f = true;
            for (EventDef d : plugin.events.all()) {
                if (!d.hasZone) continue;
                b.append(f ? "" : ",").append("{\"id\":").append(q(d.id)).append(",\"nombre\":").append(q(clean(d.name))).append(",\"tipo\":").append(q(d.type.name()))
                        .append(",").append(rect(d.world, d.minX, d.minZ, d.maxX, d.maxZ))
                        .append(",\"activo\":").append(plugin.events.running(d) != null).append(",\"xp\":").append(d.rewardXp).append("}");
                f = false;
            }
            b.append("],\"tablones\":[");
            f = true;
            for (BoardManager.Board bd : plugin.board.all()) {
                b.append(f ? "" : ",").append("{\"id\":").append(q(bd.id)).append(",\"world\":").append(q(bd.world)).append(",\"x\":").append((int) bd.x).append(",\"z\":").append((int) bd.z).append("}");
                f = false;
            }
            b.append("],\"jugadores\":[");
            if (plugin.getConfig().getBoolean("web.mostrar-jugadores", false)) {
                f = true;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    Guild g = plugin.guilds.of(p);
                    b.append(f ? "" : ",").append("{\"nombre\":").append(q(p.getName())).append(",\"world\":").append(q(p.getWorld().getName()))
                            .append(",\"x\":").append(p.getLocation().getBlockX()).append(",\"z\":").append(p.getLocation().getBlockZ())
                            .append(",\"gremio\":").append(g == null ? "null" : q(clean(g.name))).append(",\"color\":").append(q(g == null ? "#FFFFFF" : colorOf(g.id))).append("}");
                    f = false;
                }
            }
            b.append("]}");
            json = b.toString();
        } catch (Exception e) {
            plugin.getLogger().warning("Mapa web: error al preparar datos: " + e.getMessage());
        }
    }
}
