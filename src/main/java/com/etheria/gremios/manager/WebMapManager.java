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

import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.GameMode;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
    private volatile String playersJson = "{\"jugadores\":[]}";
    private BukkitTask playerTask;
    private int port;
    private final Map<String, CompletableFuture<byte[]>> inflight = new ConcurrentHashMap<>();
    private final Semaphore renders = new Semaphore(2);
    private File tileDir;
    private static final byte[] EMPTY = emptyPng();
    private final org.bukkit.NamespacedKey kHidden;

    public WebMapManager(GremiosPlugin plugin) {
        this.plugin = plugin;
        this.kHidden = plugin.key("mapa_oculto");
        this.textures = new TextureManager(plugin);
    }

    private static byte[] emptyPng() {
        try {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), "png", o);
            return o.toByteArray();
        } catch (IOException e) {
            return new byte[0];
        }
    }

    // ------------------------------------------------------------------ jugadores ocultos

    public boolean isHidden(Player p) {
        if (p.getPersistentDataContainer().has(kHidden, PersistentDataType.BYTE)) return true;
        if (p.hasMetadata("vanished")) return true;
        return plugin.getConfig().getBoolean("web.ocultar-espectadores", true) && p.getGameMode() == GameMode.SPECTATOR;
    }

    /** Alterna si el jugador sale en el mapa. Devuelve true si ahora está oculto. */
    public boolean toggleHidden(Player p) {
        boolean now = !p.getPersistentDataContainer().has(kHidden, PersistentDataType.BYTE);
        if (now) p.getPersistentDataContainer().set(kHidden, PersistentDataType.BYTE, (byte) 1);
        else p.getPersistentDataContainer().remove(kHidden);
        refreshPlayers();
        if (plugin.dynmap != null) plugin.dynmap.syncHidden();
        return now;
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
        port = plugin.getConfig().getInt("web.puerto", 8124);
        try (InputStream in = plugin.getResource("web/index.html")) {
            page = in == null ? "<h1>Falta web/index.html</h1>".getBytes(StandardCharsets.UTF_8) : in.readAllBytes();
        } catch (IOException e) {
            page = "<h1>Error</h1>".getBytes(StandardCharsets.UTF_8);
        }
        try {
            String bind = plugin.getConfig().getString("web.bind", "0.0.0.0");
            server = HttpServer.create(new InetSocketAddress(bind, port), 0);
            pool = Executors.newFixedThreadPool(12, r -> {
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
        tileDir = new File(plugin.getDataFolder(), "webmap");
        tileDir.mkdirs();
        refresh();
        refreshPlayers();
        playerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshPlayers, 20L, Math.max(10L, plugin.getConfig().getLong("web.refresco-jugadores-ticks", 20)));
        if (plugin.getConfig().getBoolean("web.mapa-terreno", true)) {
            textures.load(() -> {
                File mark = new File(tileDir, ".texturas");
                if (!mark.exists()) { // lo dibujado antes sin texturas se rehace con ellas (solo la primera vez)
                    clearTiles();
                    try {
                        mark.createNewFile();
                    } catch (IOException ignored) {
                    }
                }
                if (plugin.getConfig().getBoolean("web.prerender-al-iniciar", true)) prerender(0);
            });
        }
        long every = Math.max(20L, plugin.getConfig().getLong("web.refresco-segundos", 5) * 20L);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, every, every);
        plugin.getLogger().info("Mapa web en " + link());
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
        if (playerTask != null) playerTask.cancel();
        playerTask = null;
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
            } else if (path.equals("/api/jugadores")) {
                send(ex, 200, "application/json; charset=utf-8", playersJson.getBytes(StandardCharsets.UTF_8));
            } else if (path.startsWith("/tiles/")) {
                tile(ex, path);
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
            b.append("],\"terreno\":").append(plugin.getConfig().getBoolean("web.mapa-terreno", true));
            b.append(",\"cabezas\":").append(plugin.getConfig().getBoolean("web.cabezas", true));
            b.append("}");
            json = b.toString();
        } catch (Exception e) {
            plugin.getLogger().warning("Mapa web: error al preparar datos: " + e.getMessage());
        }
    }

    /** Jugadores en línea (se actualiza más a menudo que el resto de datos). */
    public void refreshPlayers() {
        try {
            StringBuilder b = new StringBuilder("{\"jugadores\":[");
            if (plugin.getConfig().getBoolean("web.mostrar-jugadores", true)) {
                boolean f = true;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (isHidden(p)) continue;
                    Guild g = plugin.guilds.of(p);
                    b.append(f ? "" : ",").append("{\"nombre\":").append(q(p.getName())).append(",\"world\":").append(q(p.getWorld().getName()))
                            .append(",\"x\":").append(p.getLocation().getBlockX()).append(",\"z\":").append(p.getLocation().getBlockZ())
                            .append(",\"yaw\":").append((int) p.getLocation().getYaw())
                            .append(",\"gremio\":").append(g == null ? "null" : q(clean(g.name))).append(",\"color\":").append(q(g == null ? "#FFFFFF" : colorOf(g.id))).append("}");
                    f = false;
                }
            }
            playersJson = b.append("]}").toString();
        } catch (Exception e) {
            plugin.getLogger().warning("Mapa web: error al preparar jugadores: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ terreno (teselas)
    // Zoom z: 1 bloque = 2^z píxeles. z 0..4 se dibuja directo desde los chunks (z=4: texturas a 16 px por bloque);
    // z -1 y -2 se montan juntando 4 teselas del nivel siguiente. Cada tesela mide 256x256 píxeles.

    private static final int T = 256;
    private static final int ZMIN = -6, ZMAX = 4;
    private final TextureManager textures;

    public TextureManager textures() {
        return textures;
    }

    private static double span(int z) {
        return T / Math.pow(2, z);
    }

    private long ttlMs() {
        return Math.max(1, plugin.getConfig().getLong("web.terreno.ttl-minutos", 15)) * 60_000L;
    }

    /** Límites del mapa de un mundo: minX, minZ, maxX, maxZ. Por defecto el borde del mundo (web.terreno.radio > 0 lo recorta). */
    private double[] bounds(World w) {
        double half = 29_999_984;
        double cx = 0, cz = 0;
        try {
            var b = w.getWorldBorder();
            half = b.getSize() / 2 + 32;
            cx = b.getCenter().getX();
            cz = b.getCenter().getZ();
        } catch (Throwable ignored) {
        }
        int cap = plugin.getConfig().getInt("web.terreno.radio", 0);
        if (cap > 0) half = Math.min(half, cap);
        return new double[]{cx - half, cz - half, cx + half, cz + half};
    }

    /** ¿Hay archivos de región (chunks guardados) en la zona de esta tesela? Sin ellos no hay nada que dibujar. */
    private boolean hasRegion(World w, int tx, int tz, int z) {
        double sp = span(z);
        int rx0 = (int) Math.floor(tx * sp / 512.0), rx1 = (int) Math.floor((tx * sp + sp - 1) / 512.0);
        int rz0 = (int) Math.floor(tz * sp / 512.0), rz1 = (int) Math.floor((tz * sp + sp - 1) / 512.0);
        File base = w.getWorldFolder();
        File[] dirs = {new File(base, "region"), new File(base, "DIM-1/region"), new File(base, "DIM1/region")};
        for (int rx = rx0; rx <= rx1; rx++) {
            for (int rz = rz0; rz <= rz1; rz++) {
                for (File d : dirs) if (new File(d, "r." + rx + "." + rz + ".mca").isFile()) return true;
            }
        }
        return false;
    }

    /** Tesela que se sabe vacía sin dibujarla (fuera del borde o sin chunks generados). */
    private boolean trivialEmpty(String world, int z, int tx, int tz) {
        World w = Bukkit.getWorld(world);
        if (w == null) return true;
        double sp = span(z);
        double cx = tx * sp, cz = tz * sp;
        double[] b = bounds(w);
        if (cx > b[2] || cz > b[3] || cx + sp < b[0] || cz + sp < b[1]) return true;
        if (z >= 0 && !plugin.getConfig().getBoolean("web.terreno.generar-chunks", false)) return !hasRegion(w, tx, tz, z);
        return false;
    }

    /** GET /tiles/<mundo>/<zoom>/<tx>/<tz>.png */
    private void tile(HttpExchange ex, String path) throws IOException {
        if (!plugin.getConfig().getBoolean("web.mapa-terreno", true)) {
            send(ex, 200, "image/png", EMPTY);
            return;
        }
        String[] p = path.substring(7).split("/");
        try {
            if (p.length != 4) throw new IllegalArgumentException();
            String w = java.net.URLDecoder.decode(p[0], StandardCharsets.UTF_8);
            int z = Integer.parseInt(p[1]);
            int tx = Integer.parseInt(p[2]);
            int tz = Integer.parseInt(p[3].replace(".png", ""));
            if (z < ZMIN || z > ZMAX || Bukkit.getWorld(w) == null) throw new IllegalArgumentException();
            byte[] png = tileBytes(w, z, tx, tz, 25);
            // null = aún se está dibujando: 503 para que el navegador reintente (no se queda en blanco)
            if (png == null) send(ex, 503, "text/plain", "dibujando".getBytes(StandardCharsets.UTF_8));
            else send(ex, 200, "image/png", png);
        } catch (IllegalArgumentException e) {
            send(ex, 400, "text/plain", "tesela inválida".getBytes(StandardCharsets.UTF_8));
        }
    }

    private File tileFile(String world, int z, int tx, int tz) {
        return new File(tileDir, world.replaceAll("[^A-Za-z0-9_.-]", "_") + "/" + z + "/" + tx + "_" + tz + ".png");
    }

    /** Devuelve la tesela de la caché (aunque esté vieja) y la regenera en segundo plano; si no existe, espera a generarla. */
    private byte[] tileBytes(String world, int z, int tx, int tz, int waitSec) {
        if (trivialEmpty(world, z, tx, tz)) return EMPTY;
        File f = tileFile(world, z, tx, tz);
        boolean exists = f.isFile();
        boolean fresh = exists && System.currentTimeMillis() - f.lastModified() < ttlMs();
        if (exists) {
            try {
                byte[] b = Files.readAllBytes(f.toPath());
                if (!fresh) request(world, z, tx, tz);
                return b;
            } catch (IOException ignored) {
            }
        }
        try {
            return request(world, z, tx, tz).get(waitSec, TimeUnit.SECONDS);
        } catch (Exception e) {
            return null;
        }
    }

    private CompletableFuture<byte[]> request(String world, int z, int tx, int tz) {
        String key = world + "|" + z + "|" + tx + "|" + tz;
        CompletableFuture<byte[]> mine = new CompletableFuture<>();
        CompletableFuture<byte[]> cur = inflight.putIfAbsent(key, mine);
        if (cur != null) return cur;
        mine.whenComplete((r, t) -> inflight.remove(key));
        if (z >= 0) renderDirect(world, z, tx, tz, mine);
        else renderComposite(world, z, tx, tz, mine);
        return mine;
    }

    private void store(String world, int z, int tx, int tz, BufferedImage img, CompletableFuture<byte[]> done) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ByteArrayOutputStream o = new ByteArrayOutputStream();
                ImageIO.write(img, "png", o);
                byte[] b = o.toByteArray();
                File f = tileFile(world, z, tx, tz);
                f.getParentFile().mkdirs();
                File tmp = new File(f.getPath() + ".tmp");
                Files.write(tmp.toPath(), b);
                Files.move(tmp.toPath(), f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                done.complete(b);
            } catch (Exception e) {
                done.complete(EMPTY);
            }
        });
    }

    /** Zoom menor: junta 4 teselas del zoom siguiente reducidas a la mitad. */
    private void renderComposite(String world, int z, int tx, int tz, CompletableFuture<byte[]> done) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                BufferedImage out = new BufferedImage(T, T, BufferedImage.TYPE_INT_ARGB);
                int half = T / 2;
                int missing = 0;
                byte[][] kids = new byte[4][];
                for (int i = 0; i < 4; i++) {
                    int cx = tx * 2 + (i & 1), cz = tz * 2 + (i >> 1);
                    if (trivialEmpty(world, z + 1, cx, cz)) continue;
                    File cf = tileFile(world, z + 1, cx, cz);
                    if (cf.isFile()) {
                        try {
                            kids[i] = Files.readAllBytes(cf.toPath());
                            continue;
                        } catch (IOException ignored) {
                        }
                    }
                    request(world, z + 1, cx, cz); // se dibuja en segundo plano; este nivel se reintenta
                    missing++;
                }
                if (missing > 0) {
                    done.complete(null);
                    return;
                }
                for (int i = 0; i < 4; i++) {
                    int cx = tx * 2 + (i & 1), cz = tz * 2 + (i >> 1);
                    byte[] b = kids[i];
                    if (b == null || b.length < 100) continue;
                    BufferedImage src = ImageIO.read(new java.io.ByteArrayInputStream(b));
                    if (src == null || src.getWidth() < T) continue;
                    int ox = (i & 1) * half, oz = (i >> 1) * half;
                    for (int y = 0; y < half; y++) {
                        for (int x = 0; x < half; x++) {
                            int a = src.getRGB(x * 2, y * 2), bb = src.getRGB(x * 2 + 1, y * 2), c = src.getRGB(x * 2, y * 2 + 1), d = src.getRGB(x * 2 + 1, y * 2 + 1);
                            out.setRGB(ox + x, oz + y, avg(a, bb, c, d));
                        }
                    }
                }
                store(world, z, tx, tz, out, done);
            } catch (Exception e) {
                done.complete(EMPTY);
            }
        });
    }

    private static int avg(int... c) {
        int a = 0, r = 0, g = 0, b = 0, n = 0;
        for (int v : c) {
            int al = v >>> 24;
            if (al == 0) continue;
            a += al; r += (v >> 16) & 255; g += (v >> 8) & 255; b += v & 255; n++;
        }
        if (n == 0) return 0;
        return ((a / n) << 24) | ((r / n) << 16) | ((g / n) << 8) | (b / n);
    }

    /** Datos por bloque de una tesela, antes del post-proceso (sombras, mezcla de biomas y texturas). */
    private static final class TileData {
        final int n;
        final boolean[] has;
        final byte[] kind; // 0 normal, 1 hierba, 2 hojas, 3 agua
        final Material[] top, floor;
        final int[] tg, tf, tw;
        final short[] hm;
        final byte[] dep;

        TileData(int n) {
            this.n = n;
            int s = n * n;
            has = new boolean[s]; kind = new byte[s]; top = new Material[s]; floor = new Material[s];
            tg = new int[s]; tf = new int[s]; tw = new int[s]; hm = new short[s]; dep = new byte[s];
        }
    }

    /** Dibuja una tesela directamente desde los chunks: 256>>z bloques de lado. */
    private void renderDirect(String wname, int z, int tx, int tz, CompletableFuture<byte[]> done) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            World w = Bukkit.getWorld(wname);
            if (w == null) {
                done.complete(EMPTY);
                return;
            }
            if (!renders.tryAcquire()) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> renderDirect(wname, z, tx, tz, done), 10L);
                return;
            }
            int n = T >> z;                 // bloques por lado
            int chunks = Math.max(1, n / 16);
            TileData td = new TileData(n);
            AtomicInteger left = new AtomicInteger(chunks * chunks);
            Runnable fin = () -> {
                if (left.decrementAndGet() > 0) return;
                renders.release();
                finishDirect(wname, z, tx, tz, td, done);
            };
            for (int dz = 0; dz < chunks; dz++) {
                for (int dx = 0; dx < chunks; dx++) {
                    int cx = tx * chunks + dx, cz = tz * chunks + dz, ox = dx * 16, oz = dz * 16;
                    try {
                        w.getChunkAtAsync(cx, cz, plugin.getConfig().getBoolean("web.terreno.generar-chunks", false)).whenComplete((ch, err) -> {
                            Runnable work = () -> {
                                try {
                                    if (ch != null && err == null) paint(w, ch.getChunkSnapshot(true, true, false), td, ox, oz);
                                } catch (Throwable ignored) {
                                }
                                fin.run();
                            };
                            if (Bukkit.isPrimaryThread()) work.run();
                            else Bukkit.getScheduler().runTask(plugin, work);
                        });
                    } catch (Throwable t) {
                        fin.run();
                    }
                }
            }
        });
    }

    private static int shade(int rgb, double f) {
        int r = Math.min(255, (int) (((rgb >> 16) & 255) * f)), g = Math.min(255, (int) (((rgb >> 8) & 255) * f)), b = Math.min(255, (int) ((rgb & 255) * f));
        return (r << 16) | (g << 8) | b;
    }

    private static int mix(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t), g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t), bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static int multiply(int a, int b) {
        return ((((a >> 16) & 255) * ((b >> 16) & 255) / 255) << 16) | ((((a >> 8) & 255) * ((b >> 8) & 255) / 255) << 8) | ((a & 255) * (b & 255) / 255);
    }

    private final Map<Material, Integer> colorCache = new ConcurrentHashMap<>();
    private static final Map<String, Integer> FIX = new java.util.HashMap<>();

    static {
        Object[][] t = {
                {"STONE", 0x7D7D7D}, {"COBBLESTONE", 0x7F7F7F}, {"ANDESITE", 0x888888}, {"DIORITE", 0xBDBDBD}, {"GRANITE", 0x956756},
                {"DIRT", 0x866043}, {"COARSE_DIRT", 0x77553B}, {"PODZOL", 0x5B4020}, {"MYCELIUM", 0x6F6265}, {"ROOTED_DIRT", 0x8D6A4F},
                {"SAND", 0xDBCFA3}, {"RED_SAND", 0xBE6821}, {"GRAVEL", 0x837F7E}, {"CLAY", 0xA0A6B6}, {"SANDSTONE", 0xE0D6A8}, {"RED_SANDSTONE", 0xBA631C},
                {"SNOW", 0xF9FEFE}, {"SNOW_BLOCK", 0xF9FEFE}, {"POWDER_SNOW", 0xF9FEFE}, {"ICE", 0x7DA9FF}, {"PACKED_ICE", 0x8DB4F5}, {"BLUE_ICE", 0x74A8FD},
                {"BEDROCK", 0x565656}, {"DEEPSLATE", 0x4D4D51}, {"TUFF", 0x6C6D67}, {"CALCITE", 0xDFE0DC}, {"DRIPSTONE_BLOCK", 0x866B5B},
                {"NETHERRACK", 0x6F3635}, {"SOUL_SAND", 0x514034}, {"SOUL_SOIL", 0x4B3B30}, {"BASALT", 0x4F4F54}, {"BLACKSTONE", 0x2C2831},
                {"CRIMSON_NYLIUM", 0x851E1E}, {"WARPED_NYLIUM", 0x167E86}, {"NETHER_BRICKS", 0x2C1519}, {"MAGMA_BLOCK", 0x8E3B14}, {"GLOWSTONE", 0xAB8654},
                {"END_STONE", 0xDDDFA5}, {"OBSIDIAN", 0x0F0B19}, {"LAVA", 0xE56E0F}, {"WATER", 0x3F76E4},
                {"OAK_LOG", 0x6B5330}, {"SPRUCE_LOG", 0x3B2610}, {"BIRCH_LOG", 0xD7D3C6}, {"JUNGLE_LOG", 0x584419}, {"ACACIA_LOG", 0x6E6A60}, {"DARK_OAK_LOG", 0x3E2912},
                {"OAK_PLANKS", 0xA2824C}, {"SPRUCE_PLANKS", 0x6B502F}, {"BIRCH_PLANKS", 0xC7B77B}, {"JUNGLE_PLANKS", 0xA8794F}, {"ACACIA_PLANKS", 0xA95A32}, {"DARK_OAK_PLANKS", 0x42301A},
                {"SPRUCE_LEAVES", 0x619961}, {"BIRCH_LEAVES", 0x80A755}, {"CHERRY_LEAVES", 0xE8B3D0}, {"AZALEA_LEAVES", 0x5E7F2E}, {"FLOWERING_AZALEA_LEAVES", 0x78823F},
                {"PUMPKIN", 0xC57614}, {"MELON", 0x8DA125}, {"HAY_BLOCK", 0xC0A31D}, {"MOSS_BLOCK", 0x596E2D}, {"TERRACOTTA", 0x985E43},
                {"MUD", 0x3C3A3E}, {"FARMLAND", 0x6B4A2B}, {"DIRT_PATH", 0x947B43}, {"CACTUS", 0x0F6F23},
        };
        for (Object[] r : t) FIX.put((String) r[0], (Integer) r[1]);
    }

    /** Color plano de un bloque (se usa si no hay texturas). */
    private int colorOfMat(Material m) {
        Integer c = colorCache.get(m);
        if (c != null) return c;
        Integer f = FIX.get(m.name());
        int v = 0x808080;
        if (f != null) v = f;
        else {
            try {
                org.bukkit.Color col = m.createBlockData().getMapColor();
                v = (col.getRed() << 16) | (col.getGreen() << 8) | col.getBlue();
            } catch (Throwable ignored) {
            }
        }
        colorCache.put(m, v);
        return v;
    }

    /** Píxeles de la cara superior: textura oficial si hay, o color plano. */
    private int[] facePx(Material m, int ppb, byte kind) {
        int[] t = textures.pixels(m, ppb);
        if (t != null) return t;
        int c = 0xFF000000 | (kind == 1 ? 0xDADADA : kind == 2 ? 0xE8E8E8 : m == Material.WATER ? 0xFFFFFF : colorOfMat(m));
        int[] o = new int[ppb * ppb];
        java.util.Arrays.fill(o, c);
        return o;
    }

    private static int[] biomeTint(String key) {
        // {hierba, hojas, agua}
        return switch (key) {
            case "desert", "badlands", "eroded_badlands", "wooded_badlands" -> new int[]{key.contains("badlands") ? 0x90814D : 0xBFB755, key.contains("badlands") ? 0x9E814D : 0xAEA42A, 0x3F76E4};
            case "savanna", "savanna_plateau", "windswept_savanna" -> new int[]{0xBFB755, 0xAEA42A, 0x3F76E4};
            case "jungle", "sparse_jungle", "bamboo_jungle" -> new int[]{0x59C93C, 0x30BB0B, 0x3F76E4};
            case "swamp", "mangrove_swamp" -> new int[]{0x6A7039, 0x6A7039, 0x617B64};
            case "taiga", "old_growth_pine_taiga", "old_growth_spruce_taiga" -> new int[]{0x86B783, 0x68A464, 0x3F76E4};
            case "snowy_plains", "snowy_taiga", "snowy_slopes", "grove", "frozen_peaks", "jagged_peaks", "ice_spikes" -> new int[]{0x80B497, 0x60A17B, 0x3D57D6};
            case "frozen_ocean", "deep_frozen_ocean", "frozen_river" -> new int[]{0x80B497, 0x60A17B, 0x3938C9};
            case "cold_ocean", "deep_cold_ocean" -> new int[]{0x80B497, 0x60A17B, 0x3D57D6};
            case "lukewarm_ocean", "deep_lukewarm_ocean" -> new int[]{0x8EB971, 0x71A74D, 0x45ADF2};
            case "warm_ocean" -> new int[]{0x8EB971, 0x71A74D, 0x43D5EE};
            case "forest", "flower_forest", "birch_forest", "old_growth_birch_forest" -> new int[]{0x79C05A, 0x59AE30, 0x3F76E4};
            case "dark_forest" -> new int[]{0x507A32, 0x59AE30, 0x3F76E4};
            case "cherry_grove" -> new int[]{0xB6DB61, 0x59AE30, 0x5DB7EF};
            case "meadow" -> new int[]{0x83BB6D, 0x63A948, 0x0E4ECF};
            case "stony_shore", "windswept_hills", "windswept_gravelly_hills", "windswept_forest", "stony_peaks" -> new int[]{0x8AB689, 0x6DA36B, 0x3F76E4};
            case "mushroom_fields" -> new int[]{0x55C93F, 0x2BBB0F, 0x3F76E4};
            default -> new int[]{0x91BD59, 0x77AB2F, 0x3F76E4};
        };
    }

    private static boolean isGrassy(String n) {
        return n.equals("GRASS_BLOCK") || n.equals("SHORT_GRASS") || n.equals("TALL_GRASS") || n.equals("FERN") || n.equals("LARGE_FERN") || n.equals("SUGAR_CANE") || n.equals("GRASS");
    }

    private static boolean isFoliage(String n) {
        return n.equals("OAK_LEAVES") || n.equals("JUNGLE_LEAVES") || n.equals("ACACIA_LEAVES") || n.equals("DARK_OAK_LEAVES") || n.equals("MANGROVE_LEAVES") || n.equals("VINE");
    }

    private static boolean isWater(Material m) {
        return m == Material.WATER || m == Material.BUBBLE_COLUMN;
    }

    /** Bloques que se "atraviesan" mirando desde arriba: plantas, antorchas, raíles… (no agua, lava ni nieve). */
    private static boolean passthrough(Material m) {
        if (m.isAir()) return true;
        if (isWater(m) || m == Material.LAVA || m == Material.SNOW) return false;
        return !m.isSolid();
    }

    private String biomeKey(ChunkSnapshot sn, int x, int y, int z) {
        try {
            return sn.getBiome(x, y, z).getKey().getKey();
        } catch (Throwable t) {
            return "plains";
        }
    }

    private int top(World w, ChunkSnapshot sn, int x, int z) {
        int y;
        if (w.getEnvironment() == World.Environment.NETHER) {
            y = Math.min(120, w.getMaxHeight() - 1);
            while (y > w.getMinHeight() && (sn.getBlockType(x, y, z).isAir() || !sn.getBlockType(x, y + 1, z).isAir())) y--;
            return y;
        }
        return sn.getHighestBlockYAt(x, z);
    }

    private void paint(World w, ChunkSnapshot sn, TileData td, int ox, int oz) {
        int min = w.getMinHeight();
        boolean overworld = w.getEnvironment() == World.Environment.NORMAL;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int y = top(w, sn, x, z);
                if (y <= min) continue;
                int i = (oz + z) * td.n + ox + x;
                Material m = sn.getBlockType(x, y, z);
                int yy = y;
                while (yy > min && passthrough(m)) {
                    yy--;
                    m = sn.getBlockType(x, yy, z);
                }
                if (yy <= min || m.isAir()) continue;
                int[] tint = overworld ? biomeTint(biomeKey(sn, x, y, z)) : new int[]{0x91BD59, 0x77AB2F, 0x3F76E4};
                td.has[i] = true;
                td.tg[i] = tint[0];
                td.tf[i] = tint[1];
                td.tw[i] = tint[2];
                String nm = m.name();
                if (isWater(m)) {
                    int d = 0, y2 = yy;
                    Material fm = m;
                    while (y2 > min) {
                        fm = sn.getBlockType(x, y2, z);
                        if (isWater(fm)) d++;
                        else if (!passthrough(fm)) break;
                        y2--;
                    }
                    td.hm[i] = (short) yy;
                    td.top[i] = Material.WATER;
                    td.floor[i] = fm;
                    td.dep[i] = (byte) Math.min(60, d);
                    td.kind[i] = 3;
                } else {
                    td.hm[i] = (short) yy;
                    td.top[i] = m;
                    td.kind[i] = (byte) (overworld && isGrassy(nm) ? 1 : overworld && isFoliage(nm) ? 2 : 0);
                }
            }
        }
    }

    private static int blur(int[] a, boolean[] has, int n, int x, int z) {
        int r = 0, g = 0, b = 0, c = 0;
        for (int dz = -2; dz <= 2; dz++) {
            int zz = z + dz;
            if (zz < 0 || zz >= n) continue;
            for (int dx = -2; dx <= 2; dx++) {
                int xx = x + dx;
                if (xx < 0 || xx >= n) continue;
                int j = zz * n + xx;
                if (!has[j]) continue;
                int v = a[j];
                r += (v >> 16) & 255; g += (v >> 8) & 255; b += v & 255; c++;
            }
        }
        return c == 0 ? a[z * n + x] : ((r / c) << 16) | ((g / c) << 8) | (b / c);
    }

    /** Post-proceso (hilo aparte): texturas, mezcla de biomas, relieve y sombras con el sol al noroeste. */
    private void finishDirect(String wname, int z, int tx, int tz, TileData td, CompletableFuture<byte[]> done) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                int n = td.n, ppb = 1 << z;
                BufferedImage img = new BufferedImage(T, T, BufferedImage.TYPE_INT_ARGB);
                int[] out = new int[T * T];
                double boost = plugin.getConfig().getDouble("web.terreno.brillo", 1.12);
                for (int bz = 0; bz < n; bz++) {
                    for (int bx = 0; bx < n; bx++) {
                        int i = bz * n + bx;
                        if (!td.has[i]) continue;
                        int h = td.hm[i];
                        byte k = td.kind[i];
                        int[] face = facePx(td.top[i], ppb, k);
                        int[] under = k == 3 ? facePx(td.floor[i], ppb, (byte) (isGrassy(td.floor[i].name()) ? 1 : 0)) : null;
                        int tintA = k == 1 ? blur(td.tg, td.has, n, bx, bz) : k == 2 ? blur(td.tf, td.has, n, bx, bz) : 0;
                        int underTint = (under != null && isGrassy(td.floor[i].name())) ? td.tg[i] : 0xFFFFFF;
                        int wc = k == 3 ? blur(td.tw, td.has, n, bx, bz) : 0;
                        double alpha = k == 3 ? Math.min(0.93, 0.50 + td.dep[i] * 0.03) : 0;
                        double dark = k == 3 ? 1.0 - Math.min(0.32, td.dep[i] * 0.012) : 1.0;
                        double f = boost;
                        if (k != 3) {
                            int hn = (bz > 0 && td.has[i - n]) ? td.hm[i - n] : h;
                            int hw = (bx > 0 && td.has[i - 1]) ? td.hm[i - 1] : h;
                            int hnw = (bz > 0 && bx > 0 && td.has[i - n - 1]) ? td.hm[i - n - 1] : h;
                            f += Math.max(-0.32, Math.min(0.32, ((h - hn) + (h - hw) + (h - hnw) * 0.5) * 0.055));
                        }
                        for (int s2 = 1; s2 <= 12; s2++) {
                            int xx = bx - s2, zz = bz - s2;
                            if (xx < 0 || zz < 0) break;
                            int j = zz * n + xx;
                            if (td.has[j] && td.hm[j] - h > s2 * 1.2 + 1.5) {
                                f *= 0.84;
                                break;
                            }
                        }
                        for (int py = 0; py < ppb; py++) {
                            for (int px = 0; px < ppb; px++) {
                                int pi = py * ppb + px, c;
                                int tp = face[pi];
                                int al = tp >>> 24;
                                int rgb = tp & 0xFFFFFF;
                                if (k == 3) {
                                    int fl = under[pi];
                                    int frgb = (fl >>> 24) < 128 ? colorOfMat(td.floor[i]) : multiply(fl & 0xFFFFFF, underTint);
                                    int wat = multiply(rgb, wc);
                                    c = shade(mix(frgb, shade(wat, 0.95), alpha), dark);
                                } else {
                                    if (k == 1) rgb = multiply(rgb, tintA);
                                    else if (k == 2) rgb = multiply(rgb, tintA);
                                    if (al < 128) rgb = shade(k == 2 ? multiply(0xCCCCCC, tintA) : colorOfMat(td.top[i]), 0.55);
                                    c = rgb;
                                }
                                out[(bz * ppb + py) * T + bx * ppb + px] = 0xFF000000 | shade(c, f);
                            }
                        }
                    }
                }
                img.setRGB(0, 0, T, T, out, 0, T);
                store(wname, z, tx, tz, img, done);
            } catch (Exception e) {
                done.complete(EMPTY);
            }
        });
    }

    /** Genera en segundo plano las teselas (zoom 0) alrededor de territorios, zonas, eventos y el spawn. */
    public int prerender(int extraRadius) {
        if (!running() || !plugin.getConfig().getBoolean("web.mapa-terreno", true)) return 0;
        java.util.Set<String> keys = new java.util.LinkedHashSet<>();
        int pad = Math.max(0, extraRadius);
        java.util.function.BiConsumer<String, int[]> add = (wn, r) -> {
            for (int tx = Math.floorDiv(r[0] - pad, T); tx <= Math.floorDiv(r[2] + pad, T); tx++)
                for (int tz = Math.floorDiv(r[1] - pad, T); tz <= Math.floorDiv(r[3] + pad, T); tz++) keys.add(wn + "|" + tx + "|" + tz);
        };
        for (Territory t : plugin.territories.all()) if (t.world != null) add.accept(t.world, new int[]{t.minX, t.minZ, t.maxX, t.maxZ});
        for (ZoneManager.Zone zn : plugin.zones.all()) if (zn.hasArea && zn.world != null) add.accept(zn.world, new int[]{zn.minX, zn.minZ, zn.maxX, zn.maxZ});
        for (EventDef d : plugin.events.all()) if (d.hasZone && d.world != null) add.accept(d.world, new int[]{d.minX, d.minZ, d.maxX, d.maxZ});
        for (World w : Bukkit.getWorlds()) {
            int sx = w.getSpawnLocation().getBlockX(), sz = w.getSpawnLocation().getBlockZ();
            add.accept(w.getName(), new int[]{sx - 128, sz - 128, sx + 128, sz + 128});
        }
        int max = Math.max(4, plugin.getConfig().getInt("web.terreno.prerender-maximo", 40));
        List<String> list = new java.util.ArrayList<>(keys);
        if (list.size() > max) list = list.subList(0, max);
        final List<String> todo = list;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (String k : todo) {
                String[] a = k.split("\\|");
                try {
                    int ax = Integer.parseInt(a[1]), az = Integer.parseInt(a[2]);
                    File f = tileFile(a[0], 0, ax, az);
                    if (f.isFile() && System.currentTimeMillis() - f.lastModified() < ttlMs()) continue;
                    request(a[0], 0, ax, az).get(120, TimeUnit.SECONDS);
                } catch (Exception ignored) {
                }
            }
            plugin.getLogger().info("Mapa web: terreno listo (" + todo.size() + " teselas revisadas).");
        });
        return todo.size();
    }

    // ------------------------------------------------------------------ dibujar el mundo entero

    private volatile boolean allRunning, allStop;
    private volatile int allDone, allTotal;
    private volatile String allWorld = "";

    public String allStatus() {
        return allRunning ? "dibujando " + allWorld + ": " + allDone + "/" + allTotal + " teselas" : "parado";
    }

    public void stopAll() {
        allStop = true;
    }

    /** Dibuja en segundo plano todo el mundo hasta su borde (nivel 0) y luego los niveles de zoom alejados. null = ok, si no el motivo. */
    public String renderAll(String worldName) {
        if (!running()) return "El mapa web está apagado.";
        if (allRunning) return "Ya hay un dibujado en curso (" + allStatus() + ").";
        World w = Bukkit.getWorld(worldName);
        if (w == null) return "Ese mundo no existe.";
        double[] b = bounds(w);
        int tx0 = (int) Math.floor(b[0] / T), tx1 = (int) Math.floor(b[2] / T), tz0 = (int) Math.floor(b[1] / T), tz1 = (int) Math.floor(b[3] / T);
        long count = (long) (tx1 - tx0 + 1) * (tz1 - tz0 + 1);
        long max = plugin.getConfig().getLong("web.terreno.dibujar-todo-maximo", 25000);
        if (count > max) return "El mundo es enorme (" + count + " teselas, máximo " + max + "). Pon un borde con /worldborder set <tamaño> o web.terreno.radio en config.yml.";
        final String wn = w.getName();
        final int sx = w.getSpawnLocation().getBlockX() >> 8, sz = w.getSpawnLocation().getBlockZ() >> 8;
        allRunning = true;
        allStop = false;
        allDone = 0;
        allWorld = wn;
        List<int[]> list = new java.util.ArrayList<>();
        for (int x = tx0; x <= tx1; x++) for (int zz = tz0; zz <= tz1; zz++) if (!trivialEmpty(wn, 0, x, zz)) list.add(new int[]{x, zz});
        list.sort(java.util.Comparator.comparingLong(a -> (long) (a[0] - sx) * (a[0] - sx) + (long) (a[1] - sz) * (a[1] - sz)));
        allTotal = list.size();
        plugin.getLogger().info("Mapa web: dibujando " + list.size() + " teselas de " + wn + " (de " + count + " dentro del borde; el resto no tiene chunks).");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                for (int i = 0; i < list.size() && !allStop; i += 4) {
                    List<CompletableFuture<byte[]>> batch = new java.util.ArrayList<>();
                    for (int k = i; k < Math.min(list.size(), i + 4); k++) {
                        int[] t = list.get(k);
                        File f = tileFile(wn, 0, t[0], t[1]);
                        if (f.isFile() && System.currentTimeMillis() - f.lastModified() < ttlMs()) {
                            allDone++;
                            continue;
                        }
                        batch.add(request(wn, 0, t[0], t[1]));
                    }
                    for (CompletableFuture<byte[]> fu : batch) {
                        try {
                            fu.get(300, TimeUnit.SECONDS);
                        } catch (Exception ignored) {
                        }
                        allDone++;
                    }
                    if (allDone % 100 < 4) plugin.getLogger().info("Mapa web: " + allDone + "/" + allTotal + " teselas dibujadas.");
                }
                // niveles alejados: se montan a partir de los ya dibujados
                for (int z = -1; z >= ZMIN && !allStop; z--) {
                    double sp = span(z);
                    int a0 = (int) Math.floor(b[0] / sp), a1 = (int) Math.floor(b[2] / sp), c0 = (int) Math.floor(b[1] / sp), c1 = (int) Math.floor(b[3] / sp);
                    for (int x = a0; x <= a1 && !allStop; x++) {
                        for (int zz = c0; zz <= c1 && !allStop; zz++) {
                            if (trivialEmpty(wn, z, x, zz)) continue;
                            try {
                                request(wn, z, x, zz).get(120, TimeUnit.SECONDS);
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }
                plugin.getLogger().info("Mapa web: " + (allStop ? "dibujado detenido" : "mundo " + wn + " completo") + " (" + allDone + "/" + allTotal + ").");
            } finally {
                allRunning = false;
            }
        });
        return null;
    }

    /** Borra la caché de terreno para volver a dibujarlo. */
    public void clearTiles() {
        deleteTree(tileDir);
        if (tileDir != null) tileDir.mkdirs();
    }

    private void deleteTree(File d) {
        File[] fs = d == null ? null : d.listFiles();
        if (fs != null) for (File f : fs) {
            if (f.isDirectory()) deleteTree(f);
            f.delete();
        }
    }
}
