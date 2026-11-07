package com.etheria.gremios.util;

import com.etheria.gremios.GremiosPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Compatibilidad con Bedrock (Geyser/Floodgate). Todo por reflexión: no hace falta tener esos plugins. */
public final class Compat {
    private Compat() {
    }

    /** Jugadores que eligieron (o se les asignó) menús simples. */
    private static final Set<UUID> FORCED_SIMPLE = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> FORCED_PACK = ConcurrentHashMap.newKeySet();

    public static boolean isBedrock(Player p) {
        UUID u = p.getUniqueId();
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("floodgate")) {
                Class<?> c = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                Object api = c.getMethod("getInstance").invoke(null);
                Method m = c.getMethod("isFloodgatePlayer", UUID.class);
                if ((boolean) m.invoke(api, u)) return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("Geyser-Spigot")) {
                Class<?> c = Class.forName("org.geysermc.geyser.api.GeyserApi");
                Object api = c.getMethod("api").invoke(null);
                Method m = c.getMethod("isBedrockPlayer", UUID.class);
                if ((boolean) m.invoke(api, u)) return true;
            }
        } catch (Throwable ignored) {
        }
        // Floodgate genera UUID con la parte alta a 0
        return u.getMostSignificantBits() == 0L;
    }

    public static boolean simpleMenus(GremiosPlugin plugin, Player p) {
        UUID u = p.getUniqueId();
        if (FORCED_PACK.contains(u)) return false;
        if (FORCED_SIMPLE.contains(u)) return true;
        return plugin.getConfig().getBoolean("compat.bedrock.menus-simples", true) && isBedrock(p);
    }

    /** Alterna el modo para un jugador y devuelve true si ahora ve menús simples. */
    public static boolean toggleSimple(GremiosPlugin plugin, Player p) {
        UUID u = p.getUniqueId();
        boolean nowSimple = !simpleMenus(plugin, p);
        FORCED_SIMPLE.remove(u);
        FORCED_PACK.remove(u);
        (nowSimple ? FORCED_SIMPLE : FORCED_PACK).add(u);
        return nowSimple;
    }

    public static boolean has(String plugin) {
        return Bukkit.getPluginManager().isPluginEnabled(plugin);
    }
}
