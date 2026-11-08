package com.etheria.gremios.manager;

import com.etheria.gremios.GremiosPlugin;
import com.etheria.gremios.model.NpcRole;
import com.etheria.gremios.model.Territory;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Plantillas de territorio: guarda un territorio ya configurado (bloques + NPC + puntos + puertas + bandera
 * + precios) y lo pega en otro sitio como territorio nuevo. Los cofres se guardan VACÍOS.
 * Archivos: plantillas/&lt;nombre&gt;.snap (bloques) y plantillas/&lt;nombre&gt;.yml (configuración relativa).
 */
public class TemplateManager {
    private final GremiosPlugin plugin;
    private final File dir;

    public TemplateManager(GremiosPlugin plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "plantillas");
        dir.mkdirs();
    }

    private File snap(String n) {
        return new File(dir, n + ".snap");
    }

    private File yml(String n) {
        return new File(dir, n + ".yml");
    }

    public static boolean validName(String n) {
        return n != null && n.matches("[a-z0-9_\\-]{2,24}");
    }

    public boolean exists(String n) {
        return snap(n).exists() && yml(n).exists();
    }

    public List<String> list() {
        List<String> out = new ArrayList<>();
        File[] fs = dir.listFiles((d, nm) -> nm.endsWith(".yml"));
        if (fs != null) for (File f : fs) {
            String n = f.getName().substring(0, f.getName().length() - 4);
            if (snap(n).exists()) out.add(n);
        }
        java.util.Collections.sort(out);
        return out;
    }

    public YamlConfiguration info(String n) {
        return YamlConfiguration.loadConfiguration(yml(n));
    }

    public boolean delete(String n) {
        boolean a = snap(n).delete();
        boolean b = yml(n).delete();
        return a || b;
    }

    private static List<Double> rel(double[] p, Territory t) {
        List<Double> l = new ArrayList<>();
        l.add(p[0] - t.minX);
        l.add(p[1] - t.minY);
        l.add(p[2] - t.minZ);
        l.add(p.length > 3 ? p[3] : 0.0);
        return l;
    }

    private static double[] abs(List<?> l, int ox, int oy, int oz) {
        double[] r = new double[4];
        for (int i = 0; i < 4; i++) r[i] = l.size() > i && l.get(i) instanceof Number n ? n.doubleValue() : 0;
        r[0] += ox;
        r[1] += oy;
        r[2] += oz;
        return r;
    }

    /** Guarda el territorio como plantilla. done: null = bien, texto = error. */
    public void save(Territory t, String name, Player saver, Consumer<String> done) {
        name = name == null ? "" : name.toLowerCase();
        if (!validName(name)) {
            done.accept("El nombre debe tener 2-24 caracteres (a-z, 0-9, _ o -).");
            return;
        }
        if (plugin.territories.busy(t) || t.ownerGuildId != null) {
            done.accept("Solo se puede guardar un territorio libre y sin asedio.");
            return;
        }
        final String n = name;
        // ancla: dónde estaba el admin respecto a la esquina mínima (al pegar, tu posición ocupa ese punto)
        final int[] anchor = {0, 0, 0};
        if (saver != null) {
            Location l = saver.getLocation();
            anchor[0] = l.getBlockX() - t.minX;
            anchor[1] = l.getBlockY() - t.minY;
            anchor[2] = l.getBlockZ() - t.minZ;
        }
        plugin.snapshots.capture(t, snap(n), err -> {
            if (err != null) {
                done.accept(err);
                return;
            }
            YamlConfiguration y = new YamlConfiguration();
            y.set("nombre", t.name);
            y.set("tam", List.of(t.maxX - t.minX + 1, t.maxY - t.minY + 1, t.maxZ - t.minZ + 1));
            y.set("ancla", List.of(anchor[0], anchor[1], anchor[2]));
            y.set("creada", System.currentTimeMillis());
            y.set("origen", t.id);
            y.set("precio", t.price);
            y.set("precio-fortaleza", t.priceFort);
            y.set("precio-reino", t.priceRein);
            if (t.spawn != null) y.set("spawn", rel(t.spawn, t));
            if (t.node != null) y.set("nucleo", rel(t.node, t));
            double[] bp = plugin.banners.pos(t);
            if (bp != null) y.set("bandera", rel(bp, t));
            for (var e : t.npcPos.entrySet()) y.set("npcs." + e.getKey(), rel(e.getValue(), t));
            int gi = 0;
            for (GateManager.Gate g : plugin.gates.of(t)) {
                String k = "puertas." + (gi++) + ".";
                y.set(k + "id", g.id);
                y.set(k + "tipo", g.type);
                y.set(k + "min", List.of(g.minX - t.minX, g.minY - t.minY, g.minZ - t.minZ));
                y.set(k + "max", List.of(g.maxX - t.minX, g.maxY - t.minY, g.maxZ - t.minZ));
                if (g.control != null) y.set(k + "control", List.of(g.control[0] - t.minX, g.control[1] - t.minY, g.control[2] - t.minZ));
                y.set(k + "vida", g.maxHp);
            }
            try {
                y.save(yml(n));
                done.accept(null);
            } catch (IOException ex) {
                done.accept("No se pudo guardar la configuración: " + ex.getMessage());
            }
        });
    }

    /**
     * Pega la plantilla como territorio nuevo. El punto 'at' ocupa el ancla guardada.
     * done: null = bien, texto = error.
     */
    public void paste(String name, String id, String terrName, Location at, Consumer<String> done) {
        if (!exists(name)) {
            done.accept("La plantilla '" + name + "' no existe.");
            return;
        }
        YamlConfiguration y = info(name);
        List<?> tam = y.getList("tam"), anc = y.getList("ancla");
        if (tam == null || tam.size() < 3) {
            done.accept("La plantilla está dañada (sin tamaño).");
            return;
        }
        int sx = ((Number) tam.get(0)).intValue(), sy = ((Number) tam.get(1)).intValue(), sz = ((Number) tam.get(2)).intValue();
        int ax = anc != null && anc.size() > 2 ? ((Number) anc.get(0)).intValue() : 0;
        int ay = anc != null && anc.size() > 2 ? ((Number) anc.get(1)).intValue() : 0;
        int az = anc != null && anc.size() > 2 ? ((Number) anc.get(2)).intValue() : 0;
        World w = at.getWorld();
        int minX = at.getBlockX() - ax, minY = at.getBlockY() - ay, minZ = at.getBlockZ() - az;
        int maxX = minX + sx - 1, maxY = minY + sy - 1, maxZ = minZ + sz - 1;
        if (minY < w.getMinHeight() || maxY >= w.getMaxHeight()) {
            done.accept("La plantilla no cabe en altura aquí (Y " + minY + " a " + maxY + "). Colócate a otra altura.");
            return;
        }
        String tn = terrName == null || terrName.isBlank() ? y.getString("nombre", id) : terrName;
        String err = plugin.territories.create(id, tn, new Location(w, minX, minY, minZ), new Location(w, maxX, maxY, maxZ), false);
        if (err != null) {
            done.accept(err);
            return;
        }
        final Territory t = plugin.territories.get(id);
        t.resetting = true;
        t.price = y.getLong("precio", t.price);
        t.priceFort = y.getLong("precio-fortaleza", -1);
        t.priceRein = y.getLong("precio-reino", -1);
        List<?> l;
        if ((l = y.getList("spawn")) != null) t.spawn = abs(l, minX, minY, minZ);
        if ((l = y.getList("nucleo")) != null) {
            double[] a = abs(l, minX, minY, minZ);
            t.node = new double[]{a[0], a[1], a[2]};
        }
        t.npcPos.clear();
        ConfigurationSection ns = y.getConfigurationSection("npcs");
        if (ns != null) for (String k : ns.getKeys(false)) {
            if (NpcRole.parse(k) != null && ns.getList(k) != null) t.npcPos.put(k, abs(ns.getList(k), minX, minY, minZ));
        }
        plugin.territories.save();
        plugin.snapshots.restoreFrom(t, snap(name), true, () -> {
            // 1) el estado pegado pasa a ser el snapshot del territorio nuevo (para asedios y reinicios)
            plugin.snapshots.copyAsSnapshot(snap(name), t, e2 -> {
                // 2) puertas, bandera y NPC
                ConfigurationSection gs = y.getConfigurationSection("puertas");
                if (gs != null) for (String k : gs.getKeys(false)) {
                    ConfigurationSection g = gs.getConfigurationSection(k);
                    if (g == null) continue;
                    List<?> mn = g.getList("min"), mx = g.getList("max");
                    if (mn == null || mx == null || mn.size() < 3 || mx.size() < 3) continue;
                    Location a = new Location(w, minX + ((Number) mn.get(0)).intValue(), minY + ((Number) mn.get(1)).intValue(), minZ + ((Number) mn.get(2)).intValue());
                    Location b = new Location(w, minX + ((Number) mx.get(0)).intValue(), minY + ((Number) mx.get(1)).intValue(), minZ + ((Number) mx.get(2)).intValue());
                    String ge = plugin.gates.create(t, g.getString("id", "puerta" + k), g.getString("tipo", "PUERTA"), a, b);
                    if (ge != null) {
                        plugin.getLogger().warning("Plantilla " + name + ": puerta " + k + " no creada: " + ge);
                        continue;
                    }
                    GateManager.Gate gate = plugin.gates.get(t.id, g.getString("id", "puerta" + k).toLowerCase());
                    List<?> c = g.getList("control");
                    if (gate != null && c != null && c.size() >= 3)
                        gate.control = new int[]{minX + ((Number) c.get(0)).intValue(), minY + ((Number) c.get(1)).intValue(), minZ + ((Number) c.get(2)).intValue()};
                    if (gate != null && g.contains("vida")) {
                        gate.maxHp = g.getDouble("vida", gate.maxHp);
                        gate.hp = gate.maxHp;
                    }
                }
                plugin.gates.save();
                if (y.getList("bandera") != null) {
                    double[] b = abs(y.getList("bandera"), minX, minY, minZ);
                    plugin.banners.setPosRaw(t, b);
                }
                t.resetting = false;
                if (plugin.getConfig().getBoolean("npcs.auto-al-crear", true)) plugin.npcs.autoPlace(t, false);
                plugin.territories.save();
                plugin.npcs.sync(t);
                done.accept(e2);
            });
        });
    }
}
