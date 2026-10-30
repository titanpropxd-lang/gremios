package com.etheria.gremios.manager;

import com.etheria.gremios.GremiosPlugin;
import com.etheria.gremios.manager.SiegeItems.Ammo;
import com.etheria.gremios.manager.SiegeItems.UnitType;
import com.etheria.gremios.model.Guild;
import com.etheria.gremios.model.Raid;
import com.etheria.gremios.model.Rank;
import com.etheria.gremios.model.Territory;
import com.etheria.gremios.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Snowball;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Cañones, catapultas, munición, proyectiles y destrucción de bloques durante raids. */
public class SiegeManager {
    public static class Unit {
        public UUID slime;
        public UUID display;
        public UnitType type;
        public String guildId;
        public double hp;
        public Ammo ammo;
        public int ammoCount;
        public long lastFire;
    }

    private final GremiosPlugin plugin;
    private final Map<UUID, Unit> units = new HashMap<>();
    private final Set<UUID> flying = new HashSet<>();
    private final List<NamespacedKey> recipeKeys = new ArrayList<>();

    public final NamespacedKey kUnit, kGuild, kHp, kAmmo, kAmmoN, kDisplay, kProj, kProjGuild, kDisp;

    public SiegeManager(GremiosPlugin plugin) {
        this.plugin = plugin;
        kUnit = plugin.key("unit_type");
        kGuild = plugin.key("unit_guild");
        kHp = plugin.key("unit_hp");
        kAmmo = plugin.key("unit_ammo");
        kAmmoN = plugin.key("unit_ammo_n");
        kDisplay = plugin.key("unit_display");
        kProj = plugin.key("proj_ammo");
        kProjGuild = plugin.key("proj_guild");
        kDisp = plugin.key("unit_disp_tag");
    }

    // ------------------------------------------------------------------ registro

    public Unit unitOf(Entity e) {
        return e == null ? null : units.get(e.getUniqueId());
    }

    public int count(Guild g) {
        int n = 0;
        for (Unit u : units.values()) if (u.guildId.equals(g.id)) n++;
        return n;
    }

    public List<Unit> unitsOf(String guildId) {
        List<Unit> l = new ArrayList<>();
        for (Unit u : units.values()) if (u.guildId.equals(guildId)) l.add(u);
        return l;
    }

    public void register(Slime s) {
        var pdc = s.getPersistentDataContainer();
        String type = pdc.get(kUnit, PersistentDataType.STRING);
        if (type == null) return;
        try {
            Unit u = new Unit();
            u.slime = s.getUniqueId();
            u.type = UnitType.valueOf(type);
            u.guildId = pdc.get(kGuild, PersistentDataType.STRING);
            Double hp = pdc.get(kHp, PersistentDataType.DOUBLE);
            u.hp = hp == null ? maxHp(u.type) : hp;
            String a = pdc.get(kAmmo, PersistentDataType.STRING);
            u.ammo = a == null || a.isEmpty() ? null : Ammo.valueOf(a);
            Integer n = pdc.get(kAmmoN, PersistentDataType.INTEGER);
            u.ammoCount = n == null ? 0 : n;
            String d = pdc.get(kDisplay, PersistentDataType.STRING);
            u.display = d == null ? null : UUID.fromString(d);
            if (u.guildId == null || plugin.guilds.byId(u.guildId) == null) {
                removeEntities(u);
                return;
            }
            units.put(u.slime, u);
        } catch (IllegalArgumentException ignored) {
        }
    }

    public void rescan() {
        for (World w : Bukkit.getWorlds()) {
            for (Slime s : w.getEntitiesByClass(Slime.class)) register(s);
        }
        // pantallas huérfanas
        Set<UUID> keep = new HashSet<>();
        for (Unit u : units.values()) if (u.display != null) keep.add(u.display);
        for (World w : Bukkit.getWorlds()) {
            for (ItemDisplay d : w.getEntitiesByClass(ItemDisplay.class)) {
                if (d.getPersistentDataContainer().has(kDisp, PersistentDataType.STRING) && !keep.contains(d.getUniqueId())) d.remove();
            }
        }
    }

    public void save(Unit u) {
        Entity e = Bukkit.getEntity(u.slime);
        if (e == null) return;
        var pdc = e.getPersistentDataContainer();
        pdc.set(kHp, PersistentDataType.DOUBLE, u.hp);
        pdc.set(kAmmo, PersistentDataType.STRING, u.ammo == null ? "" : u.ammo.name());
        pdc.set(kAmmoN, PersistentDataType.INTEGER, u.ammoCount);
    }

    public double maxHp(UnitType t) {
        return plugin.getConfig().getDouble("asedio." + t.cfg + ".vida", t == UnitType.CANON ? 120 : 200);
    }

    // ------------------------------------------------------------------ colocar / recoger

    public String place(Player p, UnitType type, Location loc) {
        Guild g = plugin.guilds.of(p);
        if (g == null) return "Necesitas un gremio para usar armas de asedio.";
        Rank r = g.rankOf(p.getUniqueId());
        if (r == null || r.id < Rank.MIEMBRO.id) return "Los reclutas no pueden colocar armas de asedio.";
        int max = plugin.getConfig().getInt("asedio.max-unidades-por-gremio", 6);
        if (count(g) >= max) return "Tu gremio ya tiene el máximo de armas de asedio (" + max + ").";
        Territory t = plugin.territories.at(loc);
        if (t != null && !g.id.equals(t.ownerGuildId)) return "No puedes colocar armas dentro de un territorio ajeno.";
        World w = loc.getWorld();
        final float yaw = p.getLocation().getYaw();
        loc.setYaw(yaw);

        Slime s = w.spawn(loc, Slime.class, e -> {
            e.setSize(type == UnitType.CANON ? 2 : 3);
            e.setAI(false);
            e.setInvisible(true);
            e.setSilent(true);
            e.setCollidable(false);
            e.setPersistent(true);
            e.setRemoveWhenFarAway(false);
            e.setCustomName("§6" + type.display);
            e.setCustomNameVisible(false);
            var pdc = e.getPersistentDataContainer();
            pdc.set(kUnit, PersistentDataType.STRING, type.name());
            pdc.set(kGuild, PersistentDataType.STRING, g.id);
            pdc.set(kHp, PersistentDataType.DOUBLE, maxHp(type));
            pdc.set(kAmmo, PersistentDataType.STRING, "");
            pdc.set(kAmmoN, PersistentDataType.INTEGER, 0);
        });
        ItemDisplay d = spawnDisplay(loc, type, yaw);
        s.getPersistentDataContainer().set(kDisplay, PersistentDataType.STRING, d.getUniqueId().toString());
        Unit u = new Unit();
        u.slime = s.getUniqueId();
        u.display = d.getUniqueId();
        u.type = type;
        u.guildId = g.id;
        u.hp = maxHp(type);
        units.put(u.slime, u);
        w.playSound(loc, Sound.BLOCK_ANVIL_PLACE, 1f, 0.8f);
        return null;
    }

    private ItemDisplay spawnDisplay(Location loc, UnitType type, float yaw) {
        final float scale = type == UnitType.CANON ? 1.5f : 2.2f;
        Location dl = loc.clone();
        dl.setYaw(yaw);
        dl.setPitch(0f);
        return loc.getWorld().spawn(dl, ItemDisplay.class, e -> {
            e.setItemStack(SiegeItems.displayItem(type.cmd));
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setTransformation(new Transformation(new Vector3f(0f, scale * 0.5f, 0f), new AxisAngle4f(0f, 0f, 0f, 1f),
                    new Vector3f(scale, scale, scale), new AxisAngle4f(0f, 0f, 0f, 1f)));
            e.setBrightness(new Display.Brightness(12, 12));
            e.setViewRange(1.5f);
            e.setPersistent(true);
            e.getPersistentDataContainer().set(kDisp, PersistentDataType.STRING, "1");
        });
    }

    private void removeEntities(Unit u) {
        Entity s = Bukkit.getEntity(u.slime);
        if (s != null) s.remove();
        if (u.display != null) {
            Entity d = Bukkit.getEntity(u.display);
            if (d != null) d.remove();
        }
    }

    public void destroy(Unit u, boolean effects) {
        Entity s = Bukkit.getEntity(u.slime);
        if (effects && s != null) {
            Location l = s.getLocation().add(0, 0.8, 0);
            l.getWorld().spawnParticle(Particle.EXPLOSION, l, 3, 0.4, 0.4, 0.4, 0);
            l.getWorld().spawnParticle(Particle.LARGE_SMOKE, l, 20, 0.4, 0.4, 0.4, 0.02);
            l.getWorld().playSound(l, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
        }
        removeEntities(u);
        units.remove(u.slime);
    }

    public void removeUnitsIn(Territory t) {
        for (Unit u : new ArrayList<>(units.values())) {
            Entity e = Bukkit.getEntity(u.slime);
            if (e != null && t.contains(e.getLocation())) destroy(u, false);
        }
    }

    public void removeUnitsOf(Guild g) {
        for (Unit u : new ArrayList<>(units.values())) if (u.guildId.equals(g.id)) destroy(u, false);
    }

    public void pickup(Player p, Unit u) {
        Guild g = plugin.guilds.of(p);
        if (g == null || !g.id.equals(u.guildId)) {
            Msg.to(p, "§cEsa arma no es de tu gremio.");
            return;
        }
        giveOrDrop(p, SiegeItems.unitItem(plugin, u.type));
        if (u.ammo != null && u.ammoCount > 0) giveOrDrop(p, SiegeItems.ammoItem(plugin, u.ammo, u.ammoCount));
        destroy(u, false);
        Msg.to(p, "§aHas recogido el arma.");
    }

    private void giveOrDrop(Player p, ItemStack it) {
        for (ItemStack o : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), o);
    }

    // ------------------------------------------------------------------ cargar / disparar

    public void load(Player p, Unit u, ItemStack hand, Ammo a) {
        Guild g = plugin.guilds.of(p);
        if (g == null || !g.id.equals(u.guildId)) {
            Msg.to(p, "§cEsa arma no es de tu gremio.");
            return;
        }
        if (a.unit != u.type) {
            Msg.to(p, "§cEsa munición no sirve para " + u.type.display.toLowerCase() + ".");
            return;
        }
        if (u.ammoCount > 0 && u.ammo != a) {
            Msg.to(p, "§cYa tiene otra munición cargada (§f" + u.ammo.display + "§c). Dispárala primero.");
            return;
        }
        int cap = plugin.getConfig().getInt("asedio.capacidad-municion", 4);
        int add = Math.min(hand.getAmount(), cap - u.ammoCount);
        if (add <= 0) {
            Msg.to(p, "§cEstá llena (" + cap + ").");
            return;
        }
        hand.setAmount(hand.getAmount() - add);
        u.ammo = a;
        u.ammoCount += add;
        save(u);
        p.playSound(p.getLocation(), Sound.BLOCK_PISTON_EXTEND, 1f, 1.5f);
        Msg.actionbar(p, "§e" + a.display + " §7cargada: §f" + u.ammoCount + "/" + cap);
    }

    public void status(Player p, Unit u) {
        int cap = plugin.getConfig().getInt("asedio.capacidad-municion", 4);
        Msg.to(p, "§6" + u.type.display + " §7| Vida: §c" + (int) u.hp + "/" + (int) maxHp(u.type)
                + " §7| Munición: §f" + (u.ammo == null || u.ammoCount == 0 ? "vacía" : u.ammo.display + " " + u.ammoCount + "/" + cap));
    }

    private Vector aim(Player p, UnitType type) {
        if (type == UnitType.CANON) {
            return p.getLocation().getDirection().normalize();
        }
        double yaw = Math.toRadians(p.getLocation().getYaw());
        double a = Math.toRadians(plugin.getConfig().getDouble("asedio.catapulta.angulo", 50));
        return new Vector(-Math.sin(yaw) * Math.cos(a), Math.sin(a), Math.cos(yaw) * Math.cos(a)).normalize();
    }

    public void fire(Player p, Unit u) {
        Guild g = plugin.guilds.of(p);
        if (g == null || !g.id.equals(u.guildId)) {
            Msg.to(p, "§cEsa arma no es de tu gremio.");
            return;
        }
        Entity ent = Bukkit.getEntity(u.slime);
        if (ent == null) return;
        long now = System.currentTimeMillis();
        double cd = plugin.getConfig().getDouble("asedio." + u.type.cfg + ".espera-segundos", 6);
        long wait = (long) (cd * 1000) - (now - u.lastFire);
        if (wait > 0) {
            Msg.actionbar(p, "§cRecargando... " + String.format("%.1f", wait / 1000.0) + "s");
            return;
        }
        if (u.ammo == null || u.ammoCount <= 0) {
            Msg.actionbar(p, "§cSin munición. Cárgala con clic derecho sosteniendo munición.");
            return;
        }
        final Ammo a = u.ammo;
        final String gid = g.id;
        Vector dir = aim(p, u.type);
        double speed = plugin.getConfig().getDouble("asedio." + u.type.cfg + ".velocidad", 2.0);
        Location muzzle = ent.getLocation().add(0, 1.2, 0).add(dir.clone().multiply(1.4));
        World w = muzzle.getWorld();
        Snowball sb = w.spawn(muzzle, Snowball.class, e -> {
            e.setItem(SiegeItems.ammoItem(plugin, a, 1));
            e.setShooter(p);
            e.getPersistentDataContainer().set(kProj, PersistentDataType.STRING, a.name());
            e.getPersistentDataContainer().set(kProjGuild, PersistentDataType.STRING, gid);
        });
        sb.setVelocity(dir.clone().multiply(speed));
        flying.add(sb.getUniqueId());
        w.spawnParticle(Particle.LARGE_SMOKE, muzzle, 12, 0.2, 0.2, 0.2, 0.02);
        w.spawnParticle(Particle.FLAME, muzzle, 8, 0.1, 0.1, 0.1, 0.05);
        w.playSound(muzzle, u.type == UnitType.CANON ? Sound.ENTITY_GENERIC_EXPLODE : Sound.ENTITY_IRON_GOLEM_ATTACK, 1.2f, u.type == UnitType.CANON ? 1.4f : 0.6f);
        u.ammoCount--;
        if (u.ammoCount <= 0) u.ammo = null;
        u.lastFire = now;
        if (u.display != null) {
            Entity d = Bukkit.getEntity(u.display);
            if (d != null) d.setRotation(p.getLocation().getYaw(), 0f);
        }
        save(u);
    }

    // ------------------------------------------------------------------ daño a unidades

    public void damageUnit(Unit u, double dmg, Player src) {
        u.hp -= dmg;
        Entity e = Bukkit.getEntity(u.slime);
        if (e != null) e.getWorld().playSound(e.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.6f, 1.6f);
        if (u.hp <= 0) {
            Guild g = plugin.guilds.byId(u.guildId);
            if (g != null) plugin.guilds.broadcast(g, "§c¡Han destruido tu " + u.type.display.toLowerCase() + "!");
            destroy(u, true);
        } else {
            save(u);
        }
    }

    // ------------------------------------------------------------------ proyectiles

    public boolean isSiegeProjectile(Entity e) {
        return e.getPersistentDataContainer().has(kProj, PersistentDataType.STRING);
    }

    public void tickProjectiles() {
        for (UUID id : new ArrayList<>(flying)) {
            Entity e = Bukkit.getEntity(id);
            if (e == null || e.isDead()) {
                flying.remove(id);
                continue;
            }
            e.getWorld().spawnParticle(Particle.SMOKE, e.getLocation(), 2, 0.05, 0.05, 0.05, 0.0);
        }
    }

    public void impact(Projectile pr) {
        String aid = pr.getPersistentDataContainer().get(kProj, PersistentDataType.STRING);
        String gid = pr.getPersistentDataContainer().get(kProjGuild, PersistentDataType.STRING);
        if (aid == null) return;
        flying.remove(pr.getUniqueId());
        Ammo a;
        try {
            a = Ammo.valueOf(aid);
        } catch (IllegalArgumentException e) {
            pr.remove();
            return;
        }
        Location loc = pr.getLocation();
        World w = loc.getWorld();
        pr.remove();
        var cfg = plugin.getConfig();
        double R = a.radius(cfg);
        w.spawnParticle(Particle.EXPLOSION, loc, 2, 0.3, 0.3, 0.3, 0);
        w.spawnParticle(Particle.LARGE_SMOKE, loc, 15, R / 3, R / 3, R / 3, 0.02);
        if (a.fire) w.spawnParticle(Particle.FLAME, loc, 25, R / 3, R / 3, R / 3, 0.04);
        w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.9f);

        Guild sg = plugin.guilds.byId(gid);
        if (sg == null) return;
        Raid raid = plugin.raids.activeRaidOf(sg);
        if (raid == null) return; // fuera de un asedio es solo espectáculo
        boolean attacker = raid.attackerId.equals(sg.id);
        String oppId = attacker ? raid.defenderId : raid.attackerId;
        Player shooter = pr.getShooter() instanceof Player sp ? sp : null;

        if (attacker) {
            Location nl = plugin.raids.nodeLocation(raid);
            if (nl != null && nl.getWorld() == w && nl.distance(loc) <= R + 1.8) {
                plugin.raids.damageNode(raid, a.node(cfg), shooter);
            }
        }
        for (Unit u : new ArrayList<>(units.values())) {
            if (!u.guildId.equals(oppId)) continue;
            Entity e = Bukkit.getEntity(u.slime);
            if (e != null && e.getWorld() == w && e.getLocation().distance(loc) <= R + 1.2) damageUnit(u, a.node(cfg) * 1.2, shooter);
        }
        for (Player pl : w.getPlayers()) {
            if (pl.getGameMode() != GameMode.SURVIVAL && pl.getGameMode() != GameMode.ADVENTURE) continue;
            Guild pg = plugin.guilds.of(pl);
            if (pg == null || !pg.id.equals(oppId)) continue;
            if (pl.getLocation().distance(loc) <= R + 0.6) {
                if (shooter != null) pl.damage(a.player(cfg), shooter);
                else pl.damage(a.player(cfg));
                if (a.fire) pl.setFireTicks(100);
            }
        }
        if (attacker) {
            for (Entity e : w.getNearbyEntities(loc, R + 0.6, R + 0.6, R + 0.6)) {
                if (plugin.raids.isGuard(e) && e instanceof LivingEntity le) le.damage(a.player(cfg) * 2.0);
            }
            blockDamage(raid, loc, a);
        }
    }

    private void blockDamage(Raid raid, Location loc, Ammo a) {
        Territory t = plugin.territories.get(raid.territoryId);
        if (t == null) return;
        var cfg = plugin.getConfig();
        double R = a.blocks(cfg);
        int ir = (int) Math.ceil(R);
        int max = cfg.getInt("asedio.max-bloques-por-impacto", 90);
        double maxBlast = cfg.getDouble("asedio.resistencia-maxima-bloque", 100);
        int cap = cfg.getInt("asedio.max-bloques-danados-por-territorio", 250000);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        World w = loc.getWorld();
        int bx = loc.getBlockX(), by = loc.getBlockY(), bz = loc.getBlockZ();
        int n = 0;
        outer:
        for (int dx = -ir; dx <= ir; dx++) {
            for (int dy = -ir; dy <= ir; dy++) {
                for (int dz = -ir; dz <= ir; dz++) {
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (d > R) continue;
                    int x = bx + dx, y = by + dy, z = bz + dz;
                    if (!t.contains(x, y, z)) continue;
                    Block b = w.getBlockAt(x, y, z);
                    Material m = b.getType();
                    if (m.isAir() || b.isLiquid()) continue;
                    if (m.getBlastResistance() > maxBlast) continue;
                    if (b.getState(false) instanceof Container) continue;
                    if (d > R * 0.6 && rnd.nextDouble() > 0.6) continue;
                    if (n >= max || plugin.territories.damageCount(t) >= cap) break outer;
                    plugin.territories.recordDamage(t, x, y, z, b.getBlockData().getAsString());
                    b.setType(Material.AIR, false);
                    n++;
                }
            }
        }
    }

    // ------------------------------------------------------------------ recetas

    public void registerRecipes() {
        if (!plugin.getConfig().getBoolean("asedio.crafteo", true)) return;
        shapeless("bala_hierro", SiegeItems.ammoItem(plugin, Ammo.BALA_HIERRO, 4), Material.IRON_INGOT, Material.IRON_INGOT, Material.GUNPOWDER);
        shapeless("bala_explosiva", SiegeItems.ammoItem(plugin, Ammo.BALA_EXPLOSIVA, 4), Material.IRON_INGOT, Material.GUNPOWDER, Material.GUNPOWDER, Material.TNT);
        shapeless("bala_incendiaria", SiegeItems.ammoItem(plugin, Ammo.BALA_INCENDIARIA, 4), Material.IRON_INGOT, Material.GUNPOWDER, Material.BLAZE_POWDER, Material.COAL);
        shapeless("piedra_catapulta", SiegeItems.ammoItem(plugin, Ammo.PIEDRA_CATAPULTA, 2), Material.STONE, Material.STONE, Material.COBBLESTONE, Material.COBBLESTONE);
        shapeless("piedra_fuego", SiegeItems.ammoItem(plugin, Ammo.PIEDRA_FUEGO, 2), Material.STONE, Material.STONE, Material.BLAZE_POWDER, Material.COAL);

        NamespacedKey k1 = plugin.key("recipe_canon");
        ShapedRecipe canon = new ShapedRecipe(k1, SiegeItems.unitItem(plugin, UnitType.CANON));
        canon.shape("III", "PBP", "WWW");
        canon.setIngredient('I', Material.IRON_INGOT);
        canon.setIngredient('P', Material.OAK_PLANKS);
        canon.setIngredient('B', Material.IRON_BLOCK);
        canon.setIngredient('W', Material.OAK_LOG);
        Bukkit.addRecipe(canon);
        recipeKeys.add(k1);

        NamespacedKey k2 = plugin.key("recipe_catapulta");
        ShapedRecipe cat = new ShapedRecipe(k2, SiegeItems.unitItem(plugin, UnitType.CATAPULTA));
        cat.shape("SPS", "PLP", "WWW");
        cat.setIngredient('S', Material.STICK);
        cat.setIngredient('P', Material.OAK_PLANKS);
        cat.setIngredient('L', Material.STRING);
        cat.setIngredient('W', Material.OAK_LOG);
        Bukkit.addRecipe(cat);
        recipeKeys.add(k2);
    }

    private void shapeless(String id, ItemStack result, Material... mats) {
        NamespacedKey k = plugin.key("recipe_" + id);
        ShapelessRecipe r = new ShapelessRecipe(k, result);
        for (Material m : mats) r.addIngredient(m);
        Bukkit.addRecipe(r);
        recipeKeys.add(k);
    }

    public void unregisterRecipes() {
        for (NamespacedKey k : recipeKeys) Bukkit.removeRecipe(k);
        recipeKeys.clear();
    }

    public void shutdown() {
        for (Unit u : units.values()) save(u);
        unregisterRecipes();
    }
}
