package com.etheria.gremios.tech;

import org.bukkit.Material;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Una tecnología del árbol de un gremio. */
public final class Tech {
    public final String id;
    public final String name;
    public final Branch branch;
    public final int tier;           // 1..6
    public final Material icon;
    public final String desc;
    public final List<String> reqs;
    public final Map<String, Double> effects = new LinkedHashMap<>();

    public Tech(String id, String name, Branch branch, int tier, Material icon, String desc, List<String> reqs) {
        this.id = id;
        this.name = name;
        this.branch = branch;
        this.tier = tier;
        this.icon = icon;
        this.desc = desc;
        this.reqs = reqs;
    }

    /** Ramas del árbol. La de Slimefun solo se ve si la integración está activa. */
    public enum Branch {
        ECONOMIA("Economía", Material.GOLD_INGOT, "§6"),
        MILITAR("Militar", Material.IRON_SWORD, "§c"),
        DEFENSA("Defensa", Material.SHIELD, "§9"),
        AGRICULTURA("Agricultura", Material.WHEAT, "§a"),
        MINERIA("Minería", Material.IRON_PICKAXE, "§7"),
        MAGIA("Magia", Material.ENCHANTING_TABLE, "§d"),
        LOGISTICA("Logística", Material.COMPASS, "§e"),
        GOBIERNO("Gobierno", Material.BELL, "§b"),
        SLIMEFUN("Slimefun", Material.SLIME_BALL, "§2");

        public final String display;
        public final Material icon;
        public final String color;

        Branch(String display, Material icon, String color) {
            this.display = display;
            this.icon = icon;
            this.color = color;
        }
    }

    public double eff(String key) {
        return effects.getOrDefault(key, 0.0);
    }

    /** Texto legible de los efectos. */
    public List<String> effectLines() {
        List<String> out = new java.util.ArrayList<>();
        for (var e : effects.entrySet()) {
            String k = e.getKey();
            double v = e.getValue();
            String n = v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v);
            String line;
            if (k.startsWith("buff:")) line = "Efecto en tu territorio: " + niceBuff(k.substring(5)) + " " + roman((int) v);
            else line = switch (k) {
                case "miembros" -> "+" + n + " miembros máximos";
                case "mantenimiento" -> "-" + n + "% coste de mantenimiento";
                case "xp" -> "+" + n + "% XP de gremio";
                case "interes" -> "+" + n + "% de interés del banco";
                case "zona" -> "+" + n + "% producción de zonas";
                case "proteccion" -> "+" + n + "% protección tras un asedio";
                case "dmg-guerra" -> "+" + n + "% de daño a gremios en guerra";
                case "def-terr" -> "-" + n + "% de daño recibido en tu territorio";
                case "cultivo" -> n + "% de crecer extra en cultivos del territorio";
                case "minerales" -> n + "% de duplicar menas en el territorio";
                case "xp-orbes" -> "+" + n + "% de experiencia en tu territorio";
                case "slots" -> "+" + n + " investigación simultánea";
                case "vasallos" -> "+" + n + " vasallos máximos";
                case "guerras" -> "+" + n + " guerras simultáneas";
                case "tributo" -> "+" + n + "% de tributo de vasallos";
                case "botin-guerra" -> "+" + n + "% de botín de guerra";
                case "costo" -> "-" + n + "% coste de investigar";
                case "investigacion" -> "-" + n + "% tiempo de investigar";
                default -> k + " " + n;
            };
            out.add(line);
        }
        return out;
    }

    private static String roman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> String.valueOf(n);
        };
    }

    private static String niceBuff(String s) {
        String t = s.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }
}
