package com.etheria.gremios.manager;

import com.etheria.gremios.GremiosPlugin;
import com.etheria.gremios.model.EventDef;
import com.etheria.gremios.model.Guild;
import com.etheria.gremios.model.Raid;
import com.etheria.gremios.model.Territory;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Integración con Dynmap (si está instalado), por reflexión: no hace falta compilar contra su API.
 * Capas: Territorios (color del gremio, rojo en asedio), Zonas de recursos, Eventos y Tablones, todos con icono.
 * También respeta /gremio ocultar: oculta al jugador en Dynmap.
 */
public class DynmapHook {
    private final GremiosPlugin plugin;
    private Plugin dm;
    private Object markerApi;
    private Class<?> cApi, cSet, cArea, cMarker, cIcon;
    private final Map<String, Object> sets = new HashMap<>();
    private final Map<String, Set<String>> drawn = new HashMap<>();
    private final Set<String> hiddenByUs = new HashSet<>();

    public DynmapHook(GremiosPlugin plugin) {
        this.plugin = plugin;
    }

    private Object set(String id, String label) throws Exception {
        Object s = sets.get(id);
        if (s != null) return s;
        s = cApi.getMethod("getMarkerSet", String.class).invoke(markerApi, id);
        if (s == null) s = cApi.getMethod("createMarkerSet", String.class, String.class, Set.class, boolean.class).invoke(markerApi, id, label, null, false);
        if (s != null) {
            try {
                cSet.getMethod("setMarkerSetLabel", String.class).invoke(s, label);
                cSet.getMethod("setHideByDefault", boolean.class).invoke(s, false);
            } catch (Throwable ignored) {
            }
            sets.put(id, s);
        }
        return s;
    }

    public boolean init() {
        try {
            dm = Bukkit.getPluginManager().getPlugin("dynmap");
            if (dm == null || !dm.isEnabled()) return false;
            ClassLoader cl = dm.getClass().getClassLoader();
            cApi = Class.forName("org.dynmap.markers.MarkerAPI", true, cl);
            cSet = Class.forName("org.dynmap.markers.MarkerSet", true, cl);
            cArea = Class.forName("org.dynmap.markers.AreaMarker", true, cl);
            cMarker = Class.forName("org.dynmap.markers.Marker", true, cl);
            cIcon = Class.forName("org.dynmap.markers.MarkerIcon", true, cl);
            markerApi = dm.getClass().getMethod("getMarkerAPI").invoke(dm);
            if (markerApi == null) return false;
            if (set("gremios.territorios", "Territorios") == null) return false;
            long period = Math.max(5, plugin.getConfig().getLong("dynmap.refrescar-segundos", 30)) * 20L;
            Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, 100L, period);
            plugin.getLogger().info("Dynmap detectado: territorios, zonas, eventos y tablones aparecerán en su mapa.");
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("No se pudo conectar con Dynmap: " + t);
            return false;
        }
    }

    private static int rgb(String hex) {
        try {
            return Integer.parseInt(hex.replace("#", ""), 16);
        } catch (Exception e) {
            return 0x808080;
        }
    }

    private Object icon(String id) {
        try {
            Object i = cApi.getMethod("getMarkerIcon", String.class).invoke(markerApi, id);
            return i != null ? i : cApi.getMethod("getMarkerIcon", String.class).invoke(markerApi, "default");
        } catch (Throwable t) {
            return null;
        }
    }

    private void area(String setId, String id, String label, String world, double x1, double z1, double x2, double z2, int color, double fill, int weight, Set<String> now) throws Exception {
        Object s = sets.get(setId);
        double[] xs = {Math.min(x1, x2), Math.max(x1, x2) + 1}, zs = {Math.min(z1, z2), Math.max(z1, z2) + 1};
        Object m = cSet.getMethod("findAreaMarker", String.class).invoke(s, id);
        if (m == null) m = cSet.getMethod("createAreaMarker", String.class, String.class, boolean.class, String.class, double[].class, double[].class, boolean.class).invoke(s, id, label, true, world, xs, zs, false);
        if (m == null) return;
        cArea.getMethod("setLabel", String.class, boolean.class).invoke(m, label, true);
        cArea.getMethod("setCornerLocations", double[].class, double[].class).invoke(m, xs, zs);
        cArea.getMethod("setFillStyle", double.class, int.class).invoke(m, fill, color);
        cArea.getMethod("setLineStyle", int.class, double.class, int.class).invoke(m, weight, 0.9, color);
        now.add(id);
    }

    private void point(String setId, String id, String label, String world, double x, double z, String iconId, Set<String> now) throws Exception {
        Object s = sets.get(setId);
        World w = Bukkit.getWorld(world);
        double y = w == null ? 64 : w.getHighestBlockYAt((int) x, (int) z) + 1;
        Object ic = icon(iconId);
        Object m = cSet.getMethod("findMarker", String.class).invoke(s, id);
        if (m == null) m = cSet.getMethod("createMarker", String.class, String.class, boolean.class, String.class, double.class, double.class, double.class, cIcon, boolean.class).invoke(s, id, label, true, world, x, y, z, ic, false);
        if (m == null) return;
        cMarker.getMethod("setLabel", String.class, boolean.class).invoke(m, label, true);
        cMarker.getMethod("setLocation", String.class, double.class, double.class, double.class).invoke(m, world, x, y, z);
        if (ic != null) cMarker.getMethod("setMarkerIcon", cIcon).invoke(m, ic);
        now.add(id);
    }

    private void sweep(String setId, Set<String> now, boolean areas) throws Exception {
        Object s = sets.get(setId);
        Set<String> old = drawn.getOrDefault(setId, new HashSet<>());
        for (String id : new HashSet<>(old)) {
            if (now.contains(id)) continue;
            Object m = cSet.getMethod(areas ? "findAreaMarker" : "findMarker", String.class).invoke(s, id);
            if (m != null) (areas ? cArea : cMarker).getMethod("deleteMarker").invoke(m);
        }
        drawn.put(setId, new HashSet<>(now));
    }

    public void refresh() {
        try {
            set("gremios.territorios", "Territorios");
            set("gremios.zonas", "Zonas de recursos");
            set("gremios.eventos", "Eventos");
            set("gremios.tablones", "Tablones de misiones");
            set("gremios.iconos", "Iconos de territorios");

            Set<String> a = new HashSet<>(), ic = new HashSet<>();
            for (Territory t : plugin.territories.all()) {
                if (t.world == null) continue;
                Guild o = t.ownerGuildId == null ? null : plugin.guilds.byId(t.ownerGuildId);
                Raid r = plugin.raids.get(t.id);
                boolean war = r != null && r.state == Raid.State.ACTIVE;
                String label = "<b>" + esc(t.name) + "</b><br>" + (o == null ? "Libre, a la venta" : "Gremio: <b>" + esc(o.name) + "</b><br>" + Guild.levelName(o.territoryLevel)
                        + "<br>Miembros: " + o.members.size())
                        + (war ? "<br><b style='color:red'>¡EN ASEDIO! " + esc(r.attackerName) + " ataca a " + esc(r.defenderName) + "</b>" : "");
                int color = war ? 0xFF2020 : (o == null ? 0x30C030 : rgb(WebMapManager.colorOf(o.id)));
                area("gremios.territorios", "terr_" + t.id, label, t.world, t.minX, t.minZ, t.maxX, t.maxZ, color, 0.30, war ? 4 : 2, a);
                point("gremios.iconos", "ico_" + t.id, esc(t.name) + (o == null ? "" : " · " + esc(o.name)), t.world, (t.minX + t.maxX) / 2.0, (t.minZ + t.maxZ) / 2.0,
                        war ? "skull" : (o == null ? "greenflag" : "tower"), ic);
            }
            sweep("gremios.territorios", a, true);
            sweep("gremios.iconos", ic, false);

            Set<String> z = new HashSet<>(), zi = new HashSet<>();
            for (ZoneManager.Zone zn : plugin.zones.all()) {
                if (!zn.hasArea || zn.world == null) continue;
                String oid = zn.owner != null && zn.owner.startsWith("G:") ? zn.owner.substring(2) : null;
                int color = oid != null ? rgb(WebMapManager.colorOf(oid)) : 0x9E9E9E;
                String label = "<b>" + esc(zn.name) + "</b><br>Tipo: " + esc(zn.kind.toLowerCase()) + "<br>" + (zn.ownerName == null ? "Zona libre: ¡conquístala!" : "Controla: " + esc(zn.ownerName))
                        + "<br>Produce cada " + zn.intervalMin + " min";
                area("gremios.zonas", "zona_" + zn.id, label, zn.world, zn.minX, zn.minZ, zn.maxX, zn.maxZ, color, 0.22, 2, z);
                point("gremios.zonas", "zico_" + zn.id, esc(zn.name), zn.world, (zn.minX + zn.maxX) / 2.0, (zn.minZ + zn.maxZ) / 2.0, zoneIcon(zn.kind), zi);
            }
            sweep("gremios.zonas", z, true);
            sweepPoints("gremios.zonas", zi);

            Set<String> e = new HashSet<>(), ei = new HashSet<>();
            for (EventDef d : plugin.events.all()) {
                if (!d.hasZone || d.world == null) continue;
                boolean on = plugin.events.running(d) != null;
                String label = "<b>" + esc(d.name) + "</b><br>" + (d.type == EventDef.Type.JEFE ? "Evento de jefe" : "Rey de la colina") + "<br>" + (on ? "<b style='color:orange'>¡EN MARCHA!</b>" : "Inactivo")
                        + "<br>Recompensa: " + d.rewardXp + " XP";
                area("gremios.eventos", "ev_" + d.id, label, d.world, d.minX, d.minZ, d.maxX, d.maxZ, on ? 0xFFC107 : 0x8A7A3A, on ? 0.35 : 0.15, 2, e);
                point("gremios.eventos", "evico_" + d.id, esc(d.name), d.world, (d.minX + d.maxX) / 2.0, (d.minZ + d.maxZ) / 2.0, d.type == EventDef.Type.JEFE ? "skull" : "king", ei);
            }
            sweep("gremios.eventos", e, true);
            sweepPoints("gremios.eventos", ei);

            Set<String> b = new HashSet<>();
            for (BoardManager.Board bd : plugin.board.all()) {
                if (bd.world == null) continue;
                point("gremios.tablones", "tab_" + bd.id, "Tablón de misiones", bd.world, bd.x, bd.z, "sign", b);
            }
            sweepPoints("gremios.tablones", b);

            syncHidden();
        } catch (Throwable t) {
            plugin.getLogger().warning("Error actualizando Dynmap: " + t);
        }
    }

    private final Map<String, Set<String>> drawnPoints = new HashMap<>();

    private void sweepPoints(String setId, Set<String> now) throws Exception {
        Object s = sets.get(setId);
        Set<String> old = drawnPoints.getOrDefault(setId, new HashSet<>());
        for (String id : new HashSet<>(old)) {
            if (now.contains(id)) continue;
            Object m = cSet.getMethod("findMarker", String.class).invoke(s, id);
            if (m != null) cMarker.getMethod("deleteMarker").invoke(m);
        }
        drawnPoints.put(setId, new HashSet<>(now));
    }

    private static String zoneIcon(String kind) {
        return switch (kind == null ? "" : kind) {
            case "TALA" -> "tree";
            case "CANTERA" -> "bricks";
            case "GRANJA" -> "basket";
            case "PESCA" -> "anchor";
            default -> "hammer";
        };
    }

    /** Aplica /gremio ocultar a Dynmap (solo toca a quien se ocultó con nuestro comando). */
    public void syncHidden() {
        if (dm == null || plugin.web == null) return;
        try {
            Method m = dm.getClass().getMethod("setPlayerVisiblity", String.class, boolean.class);
            for (Player p : Bukkit.getOnlinePlayers()) {
                boolean hide = p.getPersistentDataContainer().has(plugin.key("mapa_oculto"), org.bukkit.persistence.PersistentDataType.BYTE);
                if (hide) {
                    m.invoke(dm, p.getName(), false);
                    hiddenByUs.add(p.getName());
                } else if (hiddenByUs.remove(p.getName())) {
                    m.invoke(dm, p.getName(), true);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
