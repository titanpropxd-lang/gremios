package com.etheria.gremios.manager;

import com.etheria.gremios.GremiosPlugin;
import com.etheria.gremios.model.Guild;
import com.etheria.gremios.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Flechas de las torres de ballesta (4 tipos). Se fabrican en el Herrero con materiales + dinero
 * y se cargan en cada torre; sin flechas la torre no dispara.
 */
public class TurretAmmo {
    public enum Kind {
        REFORZADA("reforzada", "Flecha Reforzada", "§f", Material.ARROW),
        VENENO("veneno", "Flecha Venenosa", "§2", Material.SPIDER_EYE),
        FUEGO("fuego", "Flecha Incendiaria", "§6", Material.BLAZE_POWDER),
        ESCARCHA("escarcha", "Flecha de Escarcha", "§b", Material.SNOWBALL);

        public final String cfg, display, color;
        public final Material icon;

        Kind(String cfg, String display, String color, Material icon) {
            this.cfg = cfg;
            this.display = display;
            this.color = color;
            this.icon = icon;
        }

        public static Kind of(String s) {
            if (s == null) return null;
            for (Kind k : values()) if (k.name().equalsIgnoreCase(s) || k.cfg.equalsIgnoreCase(s)) return k;
            return null;
        }
    }

    private final GremiosPlugin plugin;
    private final NamespacedKey kTag;

    public TurretAmmo(GremiosPlugin plugin) {
        this.plugin = plugin;
        this.kTag = plugin.key("torre_flecha");
    }

    // ------------------------------------------------------------------ config

    private ConfigurationSection sec(Kind k) {
        return plugin.getConfig().getConfigurationSection("torres.flechas." + k.cfg);
    }

    public int perCraft() {
        return Math.max(1, plugin.getConfig().getInt("torres.flechas.cantidad", 16));
    }

    public int capacity(int level, Guild g) {
        List<Integer> l = plugin.getConfig().getIntegerList("torres.flechas.capacidad");
        int base = l.isEmpty() ? 64 * level : l.get(Math.max(0, Math.min(l.size() - 1, level - 1)));
        int extra = g == null ? 0 : (int) plugin.reino.effect2(g, ReinoManager.Track.ARMERIA);
        return base + extra;
    }

    public double num(Kind k, String key, double def) {
        ConfigurationSection s = sec(k);
        return s == null ? def : s.getDouble(key, def);
    }

    public double extraDamage(Kind k) {
        return num(k, "dano-extra", switch (k) {
            case REFORZADA -> 3;
            case VENENO -> 1;
            case FUEGO -> 1.5;
            case ESCARCHA -> 1;
        });
    }

    public Map<Material, Integer> materials(Kind k) {
        Map<Material, Integer> out = new LinkedHashMap<>();
        ConfigurationSection s = sec(k);
        ConfigurationSection m = s == null ? null : s.getConfigurationSection("materiales");
        if (m != null) {
            for (String key : m.getKeys(false)) {
                Material mat = Material.matchMaterial(key);
                if (mat != null && m.getInt(key) > 0) out.put(mat, m.getInt(key));
            }
        }
        if (out.isEmpty()) {
            out.put(Material.STICK, 8);
            out.put(Material.FEATHER, 8);
            switch (k) {
                case REFORZADA -> out.put(Material.IRON_INGOT, 4);
                case VENENO -> out.put(Material.SPIDER_EYE, 6);
                case FUEGO -> out.put(Material.BLAZE_POWDER, 4);
                case ESCARCHA -> out.put(Material.PACKED_ICE, 3);
            }
        }
        return out;
    }

    /** Precio en dinero con los descuentos del gremio (Armería y Forja). */
    public long price(Kind k, Guild g) {
        long base = sec(k) == null ? 16 : sec(k).getLong("precio", 16);
        double disc = g == null ? 0 : plugin.reino.effect(g, ReinoManager.Track.ARMERIA) + plugin.reino.effect(g, ReinoManager.Track.FORJA);
        disc = Math.min(60, disc);
        return Math.max(1, Math.round(base * (1 - disc / 100.0)));
    }

    // ------------------------------------------------------------------ objetos

    public ItemStack item(Kind k, int amount) {
        return new ItemBuilder(Material.ARROW, amount)
                .name(k.color + k.display)
                .lore("§7Munición para §6Torres de Ballesta§7.", "§7Clic derecho a una torre para cargarla.", "",
                        "§8Tipo: " + k.display)
                .glow()
                .tag(kTag, k.name())
                .build();
    }

    public Kind kindOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return Kind.of(it.getItemMeta().getPersistentDataContainer().get(kTag, PersistentDataType.STRING));
    }

    // ------------------------------------------------------------------ inventario

    private static boolean plain(ItemStack it, Material m) {
        if (it == null || it.getType() != m) return false;
        if (!it.hasItemMeta()) return true;
        var meta = it.getItemMeta();
        return !meta.hasDisplayName() && !meta.hasEnchants();
    }

    public int count(Player p, Material m) {
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) if (plain(it, m)) n += it.getAmount();
        return n;
    }

    private void take(Player p, Material m, int n) {
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (n <= 0) return;
            if (!plain(it, m)) continue;
            int t = Math.min(n, it.getAmount());
            it.setAmount(it.getAmount() - t);
            n -= t;
        }
    }

    public List<String> recipeLore(Player p, Kind k) {
        Guild g = plugin.guilds.of(p);
        List<String> l = new ArrayList<>();
        l.add("§7Se fabrican §f" + perCraft() + " §7flechas.");
        l.add("");
        l.add("§7Materiales:");
        for (var en : materials(k).entrySet()) {
            int have = count(p, en.getKey());
            l.add((have >= en.getValue() ? "§a✔ " : "§c✘ ") + "§f" + en.getValue() + "x " + en.getKey().name().toLowerCase(Locale.ROOT).replace('_', ' ') + " §8(tienes " + have + ")");
        }
        long price = price(k, g);
        long have = plugin.currency.count(p);
        l.add((have >= price ? "§a✔ " : "§c✘ ") + "§e" + plugin.currency.fmt(price) + " §8(tienes " + have + ")");
        l.add("");
        l.add("§8Daño extra: §f+" + extraDamage(k) + " §8| " + describe(k));
        l.add("§aClic para fabricar");
        return l;
    }

    public String describe(Kind k) {
        return switch (k) {
            case REFORZADA -> "§7puntas de hierro";
            case VENENO -> "§7envenena al objetivo";
            case FUEGO -> "§7prende fuego";
            case ESCARCHA -> "§7ralentiza y congela";
        };
    }

    public String craft(Player p, Kind k) {
        Guild g = plugin.guilds.of(p);
        for (var en : materials(k).entrySet())
            if (count(p, en.getKey()) < en.getValue()) return "Te faltan materiales: " + en.getValue() + "x " + en.getKey().name().toLowerCase(Locale.ROOT).replace('_', ' ') + ".";
        long price = price(k, g);
        if (!plugin.currency.take(p, price)) return "Necesitas " + plugin.currency.fmt(price) + ".";
        for (var en : materials(k).entrySet()) take(p, en.getKey(), en.getValue());
        for (ItemStack o : p.getInventory().addItem(item(k, perCraft())).values()) p.getWorld().dropItemNaturally(p.getLocation(), o);
        return null;
    }
}
