package com.etheria.gremios.manager;

import com.etheria.gremios.GremiosPlugin;
import com.etheria.gremios.model.Guild;
import com.etheria.gremios.model.Territory;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Rotatable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Bandera de gremio (la elige el líder) y su lugar visible en el territorio. */
public class BannerManager {
    private final GremiosPlugin plugin;
    private final File file;
    private final Map<String, ItemStack> banners = new HashMap<>();
    private final Map<String, double[]> positions = new HashMap<>(); // territorio -> x,y,z,yaw

    public BannerManager(GremiosPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "banderas.yml");
        load();
    }

    public ItemStack of(Guild g) {
        ItemStack it = g == null ? null : banners.get(g.id);
        return it == null ? null : it.clone();
    }

    public boolean isBanner(ItemStack it) {
        return it != null && Tag.ITEMS_BANNERS.isTagged(it.getType());
    }

    public void set(Guild g, ItemStack banner) {
        ItemStack one = banner.clone();
        one.setAmount(1);
        banners.put(g.id, one);
        save();
        refreshGuild(g);
    }

    public void remove(Guild g) {
        banners.remove(g.id);
        save();
        refreshGuild(g);
    }

    public void setPos(Territory t, Location l) {
        positions.put(t.id, new double[]{l.getBlockX(), l.getBlockY(), l.getBlockZ(), l.getYaw()});
        save();
        refresh(t);
    }

    public boolean hasPos(Territory t) {
        return positions.containsKey(t.id);
    }

    public void refreshGuild(Guild g) {
        if (g == null || g.territoryId == null) return;
        Territory t = plugin.territories.get(g.territoryId);
        if (t != null) refresh(t);
    }

    /** Coloca la bandera del dueño (o quita la que hubiera si no hay dueño). */
    public void refresh(Territory t) {
        double[] p = positions.get(t.id);
        if (p == null) return;
        World w = t.bukkitWorld();
        if (w == null) return;
        Block b = w.getBlockAt((int) p[0], (int) p[1], (int) p[2]);
        Guild o = plugin.guilds.byId(t.ownerGuildId);
        if (o == null) {
            if (Tag.BANNERS.isTagged(b.getType())) b.setType(Material.AIR, false);
            return;
        }
        ItemStack it = banners.get(o.id);
        Material mat = it == null ? Material.WHITE_BANNER : standing(it.getType());
        BlockData bd = mat.createBlockData();
        if (bd instanceof Rotatable rot) rot.setRotation(face(p[3]));
        b.setBlockData(bd, false);
        if (it != null && it.getItemMeta() instanceof BannerMeta bm && b.getState() instanceof Banner st) {
            st.setPatterns(bm.getPatterns());
            st.update(true, false);
        }
    }

    public void refreshLater(Territory t) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> refresh(t), 5L);
    }

    public void refreshAll() {
        for (Territory t : plugin.territories.all()) refresh(t);
    }

    private static Material standing(Material m) {
        // los items de banner (X_BANNER) coinciden con el bloque de pie
        return m;
    }

    private static BlockFace face(double yaw) {
        BlockFace[] f = {BlockFace.NORTH, BlockFace.NORTH_NORTH_EAST, BlockFace.NORTH_EAST, BlockFace.EAST_NORTH_EAST, BlockFace.EAST,
                BlockFace.EAST_SOUTH_EAST, BlockFace.SOUTH_EAST, BlockFace.SOUTH_SOUTH_EAST, BlockFace.SOUTH, BlockFace.SOUTH_SOUTH_WEST,
                BlockFace.SOUTH_WEST, BlockFace.WEST_SOUTH_WEST, BlockFace.WEST, BlockFace.WEST_NORTH_WEST, BlockFace.NORTH_WEST, BlockFace.NORTH_NORTH_WEST};
        double a = ((yaw % 360) + 360 + 180) % 360; // yaw 180 = norte
        return f[(int) Math.round(a / 22.5) % 16];
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        ConfigurationSection g = y.createSection("gremios");
        for (Map.Entry<String, ItemStack> e : banners.entrySet()) g.set(e.getKey(), e.getValue());
        ConfigurationSection pos = y.createSection("posiciones");
        for (Map.Entry<String, double[]> e : positions.entrySet()) pos.set(e.getKey(), List.of(e.getValue()[0], e.getValue()[1], e.getValue()[2], e.getValue()[3]));
        try {
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudo guardar banderas.yml: " + ex.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection g = y.getConfigurationSection("gremios");
        if (g != null) for (String k : g.getKeys(false)) {
            ItemStack it = g.getItemStack(k);
            if (it != null) banners.put(k, it);
        }
        ConfigurationSection pos = y.getConfigurationSection("posiciones");
        if (pos != null) for (String k : pos.getKeys(false)) {
            List<Double> l = pos.getDoubleList(k);
            if (l.size() == 4) positions.put(k, new double[]{l.get(0), l.get(1), l.get(2), l.get(3)});
        }
    }
}
