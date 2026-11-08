package com.etheria.gremios.manager;

import com.etheria.gremios.GremiosPlugin;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.Material;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Texturas oficiales de los bloques para dibujar el mapa web como el juego (igual que hace Dynmap).
 * Se leen del jar del cliente de Minecraft de la misma versión: si no existe, se descarga una vez de los servidores
 * de Mojang a plugins/GremiosGUI/cache/. No se incluye ningún recurso de Mojang dentro del plugin.
 */
public class TextureManager {
    private final GremiosPlugin plugin;
    private final Map<String, int[]> tex = new HashMap<>(); // clave -> 16x16 ARGB (primer fotograma)
    private final Map<String, int[]> mips = new ConcurrentHashMap<>();
    private final Map<Material, String> keys = new ConcurrentHashMap<>();
    private volatile boolean ready = false;
    private volatile String status = "sin cargar";

    public TextureManager(GremiosPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean ready() {
        return ready;
    }

    public String status() {
        return status;
    }

    private File jarFile() {
        String custom = plugin.getConfig().getString("web.terreno.cliente-jar", "").trim();
        if (!custom.isEmpty()) return new File(custom);
        return new File(new File(plugin.getDataFolder(), "cache"), "minecraft-client-" + Bukkit.getMinecraftVersion() + ".jar");
    }

    /** Carga las texturas; si falta el jar lo descarga en segundo plano y avisa con onReady. */
    public void load(Runnable onReady) {
        if (!plugin.getConfig().getBoolean("web.terreno.texturas", true)) {
            status = "desactivadas en config (web.terreno.texturas: false)";
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                File jar = jarFile();
                if (!jar.isFile()) {
                    if (!plugin.getConfig().getString("web.terreno.cliente-jar", "").trim().isEmpty()) {
                        status = "no existe el jar indicado: " + jar;
                        plugin.getLogger().warning("Mapa web: " + status);
                        return;
                    }
                    if (!plugin.getConfig().getBoolean("web.terreno.descargar-texturas", true)) {
                        status = "falta " + jar.getName() + " y la descarga está desactivada";
                        return;
                    }
                    status = "descargando texturas de Mojang…";
                    plugin.getLogger().info("Mapa web: descargando el cliente de Minecraft " + Bukkit.getMinecraftVersion() + " para sacar las texturas (solo una vez)…");
                    download(jar);
                }
                int n = read(jar);
                if (n < 50) {
                    status = "el jar no contiene texturas de bloques";
                    plugin.getLogger().warning("Mapa web: " + status);
                    return;
                }
                ready = true;
                status = n + " texturas cargadas de " + jar.getName();
                plugin.getLogger().info("Mapa web: " + status);
                Bukkit.getScheduler().runTask(plugin, onReady);
            } catch (Exception e) {
                status = "error: " + e.getMessage();
                plugin.getLogger().warning("Mapa web: no se pudieron cargar las texturas (" + e.getMessage() + "). Se usa el dibujo por colores. "
                        + "Alternativa: copia el jar del cliente (.minecraft/versions/" + Bukkit.getMinecraftVersion() + "/" + Bukkit.getMinecraftVersion() + ".jar) a "
                        + jarFile().getPath());
            }
        });
    }

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", "GremiosGUI");
        try (InputStream in = c.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void download(File dest) throws Exception {
        String ver = Bukkit.getMinecraftVersion();
        JsonObject man = JsonParser.parseString(get("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")).getAsJsonObject();
        String vurl = null;
        JsonArray vs = man.getAsJsonArray("versions");
        for (JsonElement e : vs) {
            JsonObject o = e.getAsJsonObject();
            if (ver.equals(o.get("id").getAsString())) vurl = o.get("url").getAsString();
        }
        if (vurl == null) throw new IllegalStateException("Mojang no lista la versión " + ver);
        JsonObject vj = JsonParser.parseString(get(vurl)).getAsJsonObject();
        String jurl = vj.getAsJsonObject("downloads").getAsJsonObject("client").get("url").getAsString();
        dest.getParentFile().mkdirs();
        File tmp = new File(dest.getPath() + ".part");
        HttpURLConnection c = (HttpURLConnection) URI.create(jurl).toURL().openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(60000);
        try (InputStream in = c.getInputStream()) {
            Files.copy(in, tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.move(tmp.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private int read(File jar) throws Exception {
        Map<String, int[]> t = new HashMap<>();
        try (ZipFile z = new ZipFile(jar)) {
            var en = z.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                String n = e.getName();
                if (!n.startsWith("assets/minecraft/textures/block/") || !n.endsWith(".png")) continue;
                String key = n.substring("assets/minecraft/textures/block/".length(), n.length() - 4);
                if (key.contains("/")) continue;
                try (InputStream in = z.getInputStream(e)) {
                    BufferedImage img = ImageIO.read(in);
                    if (img == null || img.getWidth() < 4) continue;
                    int w = img.getWidth();
                    int[] px = new int[16 * 16];
                    for (int y = 0; y < 16; y++) {
                        for (int x = 0; x < 16; x++) {
                            // primer fotograma (las animadas son tiras verticales) y reescalado simple a 16x16
                            px[y * 16 + x] = img.getRGB(x * w / 16, Math.min(img.getHeight() - 1, y * w / 16));
                        }
                    }
                    t.put(key, px);
                } catch (Exception ignored) {
                }
            }
        }
        synchronized (this) {
            tex.clear();
            tex.putAll(t);
            mips.clear();
            keys.clear();
        }
        return t.size();
    }

    private boolean has(String k) {
        return tex.containsKey(k);
    }

    private String pick(String... c) {
        for (String k : c) if (k != null && has(k)) return k;
        return null;
    }

    private static final String[] SUFFIX = {"_slab", "_stairs", "_wall", "_fence_gate", "_fence", "_pressure_plate", "_button", "_wall_sign", "_hanging_sign", "_sign", "_trapdoor", "_door"};

    /** Nombre de la textura de la cara superior del bloque, o null si no se encuentra. */
    private String resolve(Material m) {
        String n = m.name().toLowerCase(Locale.ROOT);
        switch (n) {
            case "grass_block": return pick("grass_block_top");
            case "water": case "bubble_column": return pick("water_still");
            case "lava": return pick("lava_still");
            case "snow": case "snow_block": return pick("snow");
            case "magma_block": return pick("magma");
            case "bookshelf": case "chiseled_bookshelf": return pick("oak_planks");
            case "tnt": return pick("tnt_top");
            case "carved_pumpkin": case "jack_o_lantern": case "pumpkin": return pick("pumpkin_top");
            case "sandstone": case "smooth_sandstone": return pick("sandstone_top");
            case "red_sandstone": case "smooth_red_sandstone": return pick("red_sandstone_top");
            case "quartz_block": return pick("quartz_block_top");
            case "smooth_quartz": return pick("quartz_block_bottom");
            case "deepslate": return pick("deepslate_top");
            case "bee_nest": return pick("bee_nest_top");
            case "beehive": return pick("beehive_end");
            case "moss_carpet": return pick("moss_block");
            case "petrified_oak_slab": return pick("oak_planks");
            case "dirt_path": return pick("dirt_path_top");
            case "farmland": return pick("farmland_top");
            default:
        }
        List<String> c = new ArrayList<>();
        if (n.endsWith("_carpet")) c.add(n.replace("_carpet", "_wool"));
        if (n.endsWith("_log") || n.endsWith("_stem") || n.equals("bamboo_block")) c.add(n + "_top");
        if (n.endsWith("_wood")) c.add(n.replace("_wood", "_log"));
        if (n.endsWith("_hyphae")) c.add(n.replace("_hyphae", "_stem"));
        c.add(n);
        c.add(n + "_top");
        c.add(n + "_block");
        c.add(n + "_block_top");
        for (String s : SUFFIX) {
            if (n.endsWith(s)) {
                String b = n.substring(0, n.length() - s.length());
                c.add(b);
                c.add(b + "_planks");
                c.add(b + "s");
                c.add(b + "_top");
                c.add(b + "_block");
                c.add(b + "_block_top");
                break;
            }
        }
        return pick(c.toArray(new String[0]));
    }

    /** Píxeles ARGB (ppb×ppb) de la cara superior del bloque, o null si no hay textura. */
    public int[] pixels(Material m, int ppb) {
        if (!ready) return null;
        String k = keys.computeIfAbsent(m, x -> {
            String r = resolve(x);
            return r == null ? "" : r;
        });
        if (k.isEmpty()) return null;
        return mips.computeIfAbsent(k + "|" + ppb, kk -> scale(tex.get(k), ppb));
    }

    private static int[] scale(int[] src, int ppb) {
        if (ppb >= 16) {
            int[] o = new int[ppb * ppb];
            int f = ppb / 16;
            for (int y = 0; y < ppb; y++) for (int x = 0; x < ppb; x++) o[y * ppb + x] = src[(y / f) * 16 + (x / f)];
            return o;
        }
        int f = 16 / ppb;
        int[] o = new int[ppb * ppb];
        for (int y = 0; y < ppb; y++) {
            for (int x = 0; x < ppb; x++) {
                long a = 0, r = 0, g = 0, b = 0;
                for (int dy = 0; dy < f; dy++) {
                    for (int dx = 0; dx < f; dx++) {
                        int c = src[(y * f + dy) * 16 + x * f + dx];
                        int al = c >>> 24;
                        a += al;
                        r += ((c >> 16) & 255) * al;
                        g += ((c >> 8) & 255) * al;
                        b += (c & 255) * al;
                    }
                }
                int n = f * f;
                if (a == 0) {
                    o[y * ppb + x] = 0;
                } else {
                    o[y * ppb + x] = ((int) (a / n) << 24) | ((int) (r / a) << 16) | ((int) (g / a) << 8) | (int) (b / a);
                }
            }
        }
        return o;
    }
}
