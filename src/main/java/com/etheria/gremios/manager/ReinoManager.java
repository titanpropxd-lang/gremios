package com.etheria.gremios.manager;

import com.etheria.gremios.GremiosPlugin;
import com.etheria.gremios.model.Guild;
import com.etheria.gremios.model.Rank;
import com.etheria.gremios.model.Territory;
import com.etheria.gremios.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Mejoras de reino: niveles que se pagan con el banco del gremio y dan ventajas concretas. */
public class ReinoManager {
    public enum Track {
        ARMERIA("armeria", "Armería", Material.SMITHING_TABLE, "§7Flechas de torre más baratas y más capacidad de munición.", 1, 5, 8, 16, new long[]{150, 300, 600, 1200, 2400}),
        ATALAYA("atalaya", "Atalaya", Material.SPYGLASS, "§7Las torres de ballesta alcanzan más lejos y hacen más daño.", 2, 5, 8, 2, new long[]{200, 400, 800, 1600, 3200}),
        MURALLA("muralla", "Muralla", Material.STONE_BRICKS, "§7Las puertas y puentes resisten más daño en un asedio.", 2, 5, 15, 0, new long[]{250, 500, 1000, 2000, 4000}),
        TESORO("tesoro", "Tesoro", Material.GOLD_BLOCK, "§7Las zonas de recursos que controles producen más.", 1, 5, 8, 0, new long[]{200, 400, 800, 1600, 3200}),
        HOSPITAL("hospital", "Hospital", Material.GOLDEN_APPLE, "§7Tus miembros se regeneran dentro de tu territorio.", 2, 2, 1, 0, new long[]{500, 1500}),
        FORJA("forja", "Forja Real", Material.BLAST_FURNACE, "§7Descuento en el Herrero (armas, munición y flechas).", 1, 5, 6, 0, new long[]{150, 300, 600, 1200, 2400});

        public final String cfg, display, desc;
        public final Material icon;
        public final int minTerritory, max;
        public final double effect, effect2;
        public final long[] cost;

        Track(String cfg, String display, Material icon, String desc, int minTerritory, int max, double effect, double effect2, long[] cost) {
            this.cfg = cfg;
            this.display = display;
            this.icon = icon;
            this.desc = desc;
            this.minTerritory = minTerritory;
            this.max = max;
            this.effect = effect;
            this.effect2 = effect2;
            this.cost = cost;
        }
    }

    private final GremiosPlugin plugin;
    private final File file;
    private final Map<String, Map<Track, Integer>> levels = new HashMap<>();

    public ReinoManager(GremiosPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "reino.yml");
        load();
    }

    private ConfigurationSection sec(Track t) {
        return plugin.getConfig().getConfigurationSection("reino." + t.cfg);
    }

    public int max(Track t) {
        ConfigurationSection s = sec(t);
        return s == null ? t.max : s.getInt("max", t.max);
    }

    public int minTerritory(Track t) {
        ConfigurationSection s = sec(t);
        return s == null ? t.minTerritory : s.getInt("nivel-territorio", t.minTerritory);
    }

    public double perLevel(Track t) {
        ConfigurationSection s = sec(t);
        return s == null ? t.effect : s.getDouble("efecto", t.effect);
    }

    public double perLevel2(Track t) {
        ConfigurationSection s = sec(t);
        return s == null ? t.effect2 : s.getDouble("efecto2", t.effect2);
    }

    public long cost(Track t, int currentLevel) {
        ConfigurationSection s = sec(t);
        List<Long> l = s == null ? List.of() : s.getLongList("costo");
        if (!l.isEmpty()) return l.get(Math.min(l.size() - 1, currentLevel));
        return t.cost[Math.min(t.cost.length - 1, currentLevel)];
    }

    public int level(Guild g, Track t) {
        if (g == null) return 0;
        return levels.getOrDefault(g.id, Map.of()).getOrDefault(t, 0);
    }

    /** Efecto principal acumulado (porcentaje u otro valor según la mejora). */
    public double effect(Guild g, Track t) {
        return level(g, t) * perLevel(t);
    }

    public double effect2(Guild g, Track t) {
        return level(g, t) * perLevel2(t);
    }

    public String buy(Player p, Track t) {
        Guild g = plugin.guilds.of(p);
        if (g == null) return "Necesitas un gremio.";
        Rank r = g.rankOf(p.getUniqueId());
        if (r == null || !r.atLeast(Rank.MARISCAL)) return "Solo Mariscal o superior puede mejorar el reino.";
        if (!g.hasTerritory()) return "Tu gremio necesita un territorio.";
        if (g.territoryLevel < minTerritory(t)) return "Requiere territorio nivel " + minTerritory(t) + " (" + Guild.levelName(minTerritory(t)) + ").";
        int cur = level(g, t);
        if (cur >= max(t)) return "Ya está al nivel máximo.";
        long cost = cost(t, cur);
        if (g.bank < cost) return "El banco necesita " + plugin.currency.fmt(cost) + ".";
        g.bank -= cost;
        g.log("§6-" + Msg.num(cost) + " §7" + t.display + " nv" + (cur + 1));
        levels.computeIfAbsent(g.id, k -> new EnumMap<>(Track.class)).put(t, cur + 1);
        save();
        plugin.guilds.save();
        plugin.guilds.broadcast(g, "§6⚒ §e" + t.display + " §7sube al §enivel " + (cur + 1) + "§7.");
        return null;
    }

    public void remove(Guild g) {
        if (levels.remove(g.id) != null) save();
    }

    /** Hospital: regeneración a miembros dentro de su territorio (cada 5 s). */
    public void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Guild g = plugin.guilds.of(p);
            if (g == null) continue;
            int lv = level(g, Track.HOSPITAL);
            if (lv <= 0) continue;
            Territory t = plugin.territories.at(p.getLocation());
            if (t == null || !g.id.equals(t.ownerGuildId)) continue;
            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 140, lv - 1, true, false, true));
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (var en : levels.entrySet())
            for (var t : en.getValue().entrySet()) y.set("gremios." + en.getKey() + "." + t.getKey().name(), t.getValue());
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("No se pudo guardar reino.yml: " + e.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) return;
        ConfigurationSection root = YamlConfiguration.loadConfiguration(file).getConfigurationSection("gremios");
        if (root == null) return;
        for (String gid : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(gid);
            if (s == null) continue;
            Map<Track, Integer> m = new EnumMap<>(Track.class);
            for (String k : s.getKeys(false)) {
                try {
                    m.put(Track.valueOf(k), s.getInt(k));
                } catch (IllegalArgumentException ignored) {
                }
            }
            levels.put(gid, m);
        }
    }
}
