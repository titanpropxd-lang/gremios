package com.etheria.gremios.manager;

import com.etheria.gremios.GremiosPlugin;
import com.etheria.gremios.model.Guild;
import com.etheria.gremios.model.NpcRole;
import com.etheria.gremios.model.Territory;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;

/** NPCs del territorio (aldeanos sin IA con rol). No necesita Citizens. */
public class NpcManager {
    private final GremiosPlugin plugin;
    private final NamespacedKey kRole;
    private final NamespacedKey kTerr;

    public NpcManager(GremiosPlugin plugin) {
        this.plugin = plugin;
        this.kRole = plugin.key("npc_role");
        this.kTerr = plugin.key("npc_terr");
    }

    public NpcRole roleOf(Entity e) {
        if (e == null) return null;
        String s = e.getPersistentDataContainer().get(kRole, PersistentDataType.STRING);
        return NpcRole.parse(s);
    }

    public Territory territoryOf(Entity e) {
        String s = e.getPersistentDataContainer().get(kTerr, PersistentDataType.STRING);
        return s == null ? null : plugin.territories.get(s);
    }

    public Location loc(Territory t, NpcRole r) {
        World w = t.bukkitWorld();
        if (w == null) return null;
        double[] p = t.npcPos.get(r.key());
        if (p != null && p.length >= 4) return new Location(w, p[0], p[1], p[2], (float) p[3], 0f);
        Location c = t.center();
        int idx = r.ordinal();
        double x = c.getX() + (idx - 2.5) * 2.0;
        double z = c.getZ() + 3.0;
        x = Math.max(t.minX + 0.5, Math.min(t.maxX + 0.5, x));
        z = Math.max(t.minZ + 0.5, Math.min(t.maxZ + 0.5, z));
        return safe(new Location(w, x, w.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z)) + 1, z, 180f, 0f));
    }

    private boolean free(org.bukkit.block.Block b) {
        return b.isPassable() && !b.isLiquid();
    }

    /** Busca un sitio donde el NPC no quede dentro de un bloque ni en el vacío. */
    public Location safe(Location base) {
        World w = base.getWorld();
        base.getChunk().load();
        int bx = base.getBlockX(), bz = base.getBlockZ();
        for (int r = 0; r <= 6; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int x = bx + dx, z = bz + dz;
                    int y = w.getHighestBlockYAt(x, z);
                    org.bukkit.block.Block floor = w.getBlockAt(x, y, z);
                    if (!floor.getType().isSolid() || floor.isLiquid()) continue;
                    if (free(w.getBlockAt(x, y + 1, z)) && free(w.getBlockAt(x, y + 2, z)))
                        return new Location(w, x + 0.5, y + 1, z + 0.5, base.getYaw(), 0f);
                }
            }
        }
        return base;
    }

    /** Nivel mínimo de territorio para que aparezca este NPC (configurable). */
    public int minLevel(NpcRole r) {
        return plugin.getConfig().getInt("npcs.nivel-minimo." + r.key(), r == NpcRole.HERRERO ? 1 : r.minLevel);
    }

    private Villager find(Territory t, NpcRole r) {
        Location l = loc(t, r);
        if (l == null) return null;
        l.getChunk().load();
        for (Entity e : l.getWorld().getNearbyEntities(l, 8, 8, 8)) {
            if (e instanceof Villager v && roleOf(v) == r) {
                String tid = v.getPersistentDataContainer().get(kTerr, PersistentDataType.STRING);
                if (t.id.equals(tid)) return v;
            }
        }
        return null;
    }

    private void ensure(Territory t, NpcRole r) {
        Location l = loc(t, r);
        if (l == null) return;
        Villager v = find(t, r);
        if (v != null) return;
        final String tid = t.id;
        l.getChunk().load();
        Villager nv;
        try {
            nv = l.getWorld().spawn(l, Villager.class, e -> {
            e.setAI(false);
            e.setInvulnerable(true);
            e.setSilent(true);
            e.setPersistent(true);
            e.setRemoveWhenFarAway(false);
            e.setCollidable(false);
            e.setInvisible(plugin.models != null && plugin.models.enabled());
            e.setProfession(r.profession);
            e.setCustomName(r.display);
            e.setCustomNameVisible(true);
            e.setVillagerLevel(5);
            e.getPersistentDataContainer().set(kRole, PersistentDataType.STRING, r.name());
            e.getPersistentDataContainer().set(kTerr, PersistentDataType.STRING, tid);
            });
        } catch (Exception ex) {
            plugin.getLogger().warning("NPC " + r.key() + " no pudo aparecer en " + t.id + ": " + ex.getMessage());
            return;
        }
        t.npcUuid.put(r.key(), nv.getUniqueId().toString());
    }

    private void remove(Territory t, NpcRole r) {
        Villager v = find(t, r);
        while (v != null) {
            v.remove();
            v = find(t, r);
        }
        t.npcUuid.remove(r.key());
    }

    /** Hace coincidir los NPCs del territorio con el nivel de su dueño. */
    public void sync(Territory t) {
        if (t.bukkitWorld() == null) return;
        Guild g = plugin.guilds.byId(t.ownerGuildId);
        int level = g == null ? 0 : g.territoryLevel;
        for (NpcRole r : NpcRole.values()) {
            if (level > 0 && minLevel(r) <= level) ensure(t, r);
            else remove(t, r);
        }
        plugin.territories.save();
    }

    public void removeAll(Territory t) {
        if (t.bukkitWorld() == null) return;
        for (NpcRole r : NpcRole.values()) remove(t, r);
    }

    public void syncAll() {
        for (Territory t : plugin.territories.all()) {
            if (t.ownerGuildId != null && !t.resetting) sync(t);
        }
    }

    /** Línea por NPC esperado: estado para /gremioadmin npc estado. */
    public java.util.List<String> report() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (Territory t : plugin.territories.all()) {
            if (t.ownerGuildId == null) continue;
            Guild g = plugin.guilds.byId(t.ownerGuildId);
            int level = g == null ? 0 : g.territoryLevel;
            for (NpcRole r : NpcRole.values()) {
                String st;
                if (level <= 0 || minLevel(r) > level) st = "§8no toca (nivel " + level + " < " + minLevel(r) + ")";
                else {
                    Villager v = find(t, r);
                    st = v == null ? "§cNO EXISTE" : (v.isInvisible() && !hasModel(v) ? "§eInvisible sin modelo" : "§aOK");
                }
                out.add("§7" + t.id + " §f" + r.key() + " §7→ " + st);
            }
        }
        return out;
    }

    private boolean hasModel(Villager v) {
        for (Entity e : v.getNearbyEntities(1.5, 2, 1.5)) {
            if (e instanceof org.bukkit.entity.ItemDisplay d && v.getUniqueId().toString().equals(
                    d.getPersistentDataContainer().get(plugin.key("modelo_de"), PersistentDataType.STRING))) return true;
        }
        return false;
    }

    /** Borra todos los NPC y los vuelve a crear en sitios seguros. */
    public void repairAll() {
        for (Territory t : plugin.territories.all()) {
            if (t.ownerGuildId == null || t.bukkitWorld() == null) continue;
            removeAll(t);
        }
        syncAll();
        if (plugin.models != null) plugin.models.tick();
    }

    public int countLoaded() {
        int n = 0;
        for (World w : Bukkit.getWorlds()) for (Villager v : w.getEntitiesByClass(Villager.class)) if (roleOf(v) != null) n++;
        return n;
    }
}
