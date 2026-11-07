package com.bossforge.integration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Puente con MythicMobs usando reflexion: BossForge compila y carga sin tener
 * MythicMobs instalado, y no depende de una version concreta de su API.
 * Todo lo que falle aqui se registra en consola y devuelve "no disponible"
 * en vez de tumbar el plugin.
 */
public class MythicHook {
    public record MobInfo(String id, String nombre, String tipoEntidad) { }

    private final Logger logger;

    public MythicHook(JavaPlugin plugin) {
        this.logger = plugin.getLogger();
    }

    public boolean disponible() {
        Plugin mm = Bukkit.getPluginManager().getPlugin("MythicMobs");
        return mm != null && mm.isEnabled();
    }

    /** Todos los mobs configurados en MythicMobs, ordenados por nombre interno. */
    public List<MobInfo> listar() {
        List<MobInfo> resultado = new ArrayList<>();
        if (!disponible()) return resultado;
        try {
            Object manager = mobManager();
            Object tipos = llamar(manager, "getMobTypes");
            if (tipos instanceof Collection<?> coleccion) {
                for (Object tipo : coleccion) {
                    String id = texto(llamar(tipo, "getInternalName"));
                    if (id == null || id.isBlank()) continue;
                    String nombre = texto(llamar(tipo, "getDisplayName"));
                    String entidad = texto(llamar(tipo, "getEntityType"));
                    resultado.add(new MobInfo(id, nombre == null || nombre.isBlank() ? id : nombre, entidad));
                }
            } else {
                Object nombres = llamar(manager, "getMobNames");
                if (nombres instanceof Collection<?> coleccion) {
                    for (Object n : coleccion) resultado.add(new MobInfo(String.valueOf(n), String.valueOf(n), null));
                }
            }
        } catch (Throwable e) {
            logger.warning("[BossForge] No se pudo leer la lista de mobs de MythicMobs: " + e);
        }
        resultado.sort((a, b) -> a.id().compareToIgnoreCase(b.id()));
        return resultado;
    }

    /** ¿Existe (ahora mismo) un mob de MythicMobs con ese nombre interno? */
    public boolean existe(String id) {
        if (!disponible() || id == null) return false;
        try {
            Object manager = mobManager();
            Object mob = llamar(manager, "getMythicMob", id);
            if (mob instanceof Optional<?> opt) return opt.isPresent();
            return mob != null;
        } catch (Throwable e) {
            logger.warning("[BossForge] No se pudo comprobar el mob '" + id + "' en MythicMobs: " + e);
            return false;
        }
    }

    /** Hace aparecer el mob de MythicMobs en esa ubicacion. Devuelve la entidad de Bukkit, o null si fallo. */
    public LivingEntity spawn(String id, Location ubicacion) {
        if (!disponible()) return null;
        try {
            Object manager = mobManager();
            Object activo = null;
            try {
                Method m = manager.getClass().getMethod("spawnMob", String.class, Location.class);
                m.setAccessible(true);
                activo = m.invoke(manager, id, ubicacion);
            } catch (NoSuchMethodException e) {
                activo = spawnPorTipo(manager, id, ubicacion);
            }
            if (activo == null) return null;

            Object entidadAbstracta = llamar(activo, "getEntity");
            Object bukkit = llamar(entidadAbstracta, "getBukkitEntity");
            return bukkit instanceof LivingEntity vivo ? vivo : null;
        } catch (Throwable e) {
            logger.warning("[BossForge] No se pudo invocar el mob '" + id + "' de MythicMobs: " + e);
            return null;
        }
    }

    /** Camino documentado por MythicMobs: getMythicMob(id) -> mob.spawn(AbstractLocation, nivel). */
    private Object spawnPorTipo(Object manager, String id, Location ubicacion) throws Exception {
        Object mob = llamar(manager, "getMythicMob", id);
        if (mob instanceof Optional<?> opt) mob = opt.orElse(null);
        if (mob == null) return null;

        Class<?> adaptador = claseMythic("io.lumine.mythic.bukkit.BukkitAdapter");
        Object ubicacionMythic = null;
        for (Method m : adaptador.getMethods()) {
            if (m.getName().equals("adapt") && m.getParameterCount() == 1 && m.getParameterTypes()[0] == Location.class) {
                ubicacionMythic = m.invoke(null, ubicacion);
                break;
            }
        }
        if (ubicacionMythic == null) return null;

        for (Method m : mob.getClass().getMethods()) {
            if (!m.getName().equals("spawn") || m.getParameterCount() != 2) continue;
            if (!m.getParameterTypes()[0].isInstance(ubicacionMythic)) continue;
            m.setAccessible(true);
            Object nivel = m.getParameterTypes()[1] == int.class ? (Object) 1 : (Object) 1.0d;
            return m.invoke(mob, ubicacionMythic, nivel);
        }
        return null;
    }

    private Object mobManager() throws Exception {
        Class<?> bukkit = claseMythic("io.lumine.mythic.bukkit.MythicBukkit");
        Object instancia = bukkit.getMethod("inst").invoke(null);
        return llamar(instancia, "getMobManager");
    }

    private Class<?> claseMythic(String nombre) throws ClassNotFoundException {
        Plugin mm = Bukkit.getPluginManager().getPlugin("MythicMobs");
        ClassLoader cargador = mm != null ? mm.getClass().getClassLoader() : getClass().getClassLoader();
        return Class.forName(nombre, true, cargador);
    }

    /** Llama a un metodo publico por nombre (0 o 1 argumento String). Devuelve null si el metodo no existe. */
    private Object llamar(Object objeto, String metodo, Object... argumentos) throws Exception {
        if (objeto == null) return null;
        for (Method m : objeto.getClass().getMethods()) {
            if (!m.getName().equals(metodo) || m.getParameterCount() != argumentos.length) continue;
            boolean encaja = true;
            for (int i = 0; i < argumentos.length; i++) {
                if (!m.getParameterTypes()[i].isInstance(argumentos[i])) { encaja = false; break; }
            }
            if (!encaja) continue;
            m.setAccessible(true);
            return m.invoke(objeto, argumentos);
        }
        return null;
    }

    /** Convierte el resultado a texto; si es un PlaceholderString de Mythic usa su metodo get(). */
    private String texto(Object valor) {
        if (valor == null) return null;
        if (valor instanceof String s) return s;
        try {
            Object interno = llamar(valor, "get");
            if (interno instanceof String s) return s;
        } catch (Exception ignored) { }
        return String.valueOf(valor);
    }
}
