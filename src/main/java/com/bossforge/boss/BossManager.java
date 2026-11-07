package com.bossforge.boss;

import com.bossforge.habilidades.ContextoHabilidadBoss;
import com.bossforge.habilidades.HabilidadBossRegistry;
import com.bossforge.integration.MythicHook;
import com.bossforge.model.*;
import com.bossforge.obelisk.ObeliskManager;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.logging.Logger;

/**
 * Une el estado de configuracion (BossTemplate, Obelisco) con la
 * entidad de Bukkit real durante un combate. Las habilidades nunca
 * hablan directamente con Bukkit fuera de este flujo — todo pasa por
 * BossActivo + ContextoHabilidadBoss, igual que en ForjaSync las
 * habilidades de item pasan siempre por HabilidadEjecutor.
 */
public class BossManager {
    private final JavaPlugin plugin;
    private final Logger logger;
    private final HabilidadBossRegistry registry;
    private final BossEscudoManager escudos;
    private final MythicHook mythic;
    private final BossTemplateManager plantillas;

    private final Map<UUID, BossActivo> activosPorEntidad = new HashMap<>();
    private final Map<String, Encuentro> encuentros = new HashMap<>();
    private final Map<UUID, BossBar> barrasDeVida = new HashMap<>();

    private ObeliskManager obeliskManager; // se inyecta despues para evitar dependencia circular en el constructor

    public BossManager(JavaPlugin plugin, HabilidadBossRegistry registry, BossEscudoManager escudos,
                       MythicHook mythic, BossTemplateManager plantillas) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.registry = registry;
        this.escudos = escudos;
        this.mythic = mythic;
        this.plantillas = plantillas;
    }

    public void setObeliskManager(ObeliskManager obeliskManager) {
        this.obeliskManager = obeliskManager;
    }

    public boolean hayBossActivoEn(Obelisco obelisco) {
        return encuentros.containsKey(obelisco.getId());
    }

    public Optional<BossActivo> bossActivoDe(LivingEntity entidad) {
        return Optional.ofNullable(activosPorEntidad.get(entidad.getUniqueId()));
    }

    /**
     * Inicia el combate del obelisco con el jefe de la opcion elegida.
     * Devuelve false si el jefe no pudo aparecer (en ese caso no se ha tocado nada).
     */
    public boolean invocar(Obelisco obelisco, OpcionJefe opcion, BossTemplate plantilla, List<Player> jugadores) {
        Location base = obelisco.getArenaSpawn() != null ? obelisco.getArenaSpawn() : obelisco.getUbicacion();
        if (base == null || base.getWorld() == null) {
            logger.warning("[BossForge] El obelisco '" + obelisco.getNombre() + "' no tiene una ubicacion de aparicion valida.");
            return false;
        }

        if (plugin.getConfig().getBoolean("arena.limpiar_mobs_al_invocar", true)) limpiarMobsHostilesDeLaArena(obelisco);

        Encuentro encuentro = new Encuentro(obelisco, opcion, jugadores);
        if (aparecer(encuentro, plantilla, base) == null) return false;

        encuentros.put(obelisco.getId(), encuentro);
        teletransportarJugadoresAZonaDeAparicion(obelisco, jugadores);
        obelisco.setEstado(EstadoObelisco.EN_COMBATE);
        return true;
    }

    /** Quita los mobs hostiles "sueltos" de la arena (sin nombre y que no son de un combate) para que solo esten el jefe y sus esbirros. */
    private void limpiarMobsHostilesDeLaArena(Obelisco obelisco) {
        var caja = obelisco.cajaArena();
        if (caja == null) return;
        for (org.bukkit.entity.Entity entidad : obelisco.getArenaPos1().getWorld().getNearbyEntities(caja)) {
            if (entidad instanceof org.bukkit.entity.Enemy && entidad.getCustomName() == null
                && !activosPorEntidad.containsKey(entidad.getUniqueId())) {
                entidad.remove();
            }
        }
    }

    private Location dispersar(Location base, double radio) {
        Location destino = base.clone();
        if (radio > 0) {
            double angulo = Math.random() * Math.PI * 2;
            double distancia = Math.random() * radio;
            destino.add(Math.cos(angulo) * distancia, 0, Math.sin(angulo) * distancia);
        }
        return destino;
    }

    /** Hace aparecer UN jefe (propio o de MythicMobs) y lo registra en el encuentro. Null si fallo. */
    private BossActivo aparecer(Encuentro encuentro, BossTemplate plantilla, Location donde) {
        LivingEntity entidad;
        if (plantilla.esMythic()) {
            entidad = mythic.spawn(plantilla.getMythicMobId(), donde);
            if (entidad == null) {
                logger.warning("[BossForge] No se pudo invocar el mob de MythicMobs '" + plantilla.getMythicMobId() + "'.");
                return null;
            }
        } else {
            entidad = (LivingEntity) donde.getWorld().spawnEntity(donde, plantilla.getTipoBase());
            entidad.setCustomName(net.md_5.bungee.api.ChatColor.RED + "☠ " + plantilla.getNombre());
            entidad.setCustomNameVisible(true);

            var atributoVida = entidad.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (atributoVida != null) atributoVida.setBaseValue(plantilla.getVidaMaxima());
            entidad.setHealth(Math.min(plantilla.getVidaMaxima(), entidad.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue()));

            var atributoDano = entidad.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
            if (atributoDano != null) atributoDano.setBaseValue(plantilla.getDanoAtaque());
        }
        entidad.setRemoveWhenFarAway(false);
        entidad.setPersistent(true);

        BossActivo activo = new BossActivo(entidad, plantilla, encuentro.getObelisco(), encuentro.getJugadores());
        activosPorEntidad.put(entidad.getUniqueId(), activo);
        encuentro.getActivos().add(activo);

        String titulo = net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', plantilla.getNombre());
        BossBar barra = plugin.getServer().createBossBar(titulo, BarColor.RED, BarStyle.SOLID);
        barra.setProgress(1.0);
        for (Player jugador : encuentro.getJugadores()) barra.addPlayer(jugador);
        barrasDeVida.put(entidad.getUniqueId(), barra);
        return activo;
    }

    /** Teletransporta a los jugadores a la zona de aparicion (o, si no hay, al punto de aparicion de la arena), repartiendolos al azar dentro del radio. */
    private void teletransportarJugadoresAZonaDeAparicion(Obelisco obelisco, List<Player> jugadores) {
        Location centro = obelisco.getJugadorSpawn() != null ? obelisco.getJugadorSpawn() : obelisco.getArenaSpawn();
        if (centro == null || centro.getWorld() == null) return;

        double radio = obelisco.getJugadorSpawnRadio();
        for (Player jugador : jugadores) {
            jugador.teleport(dispersar(centro, radio));
            jugador.sendMessage(net.md_5.bungee.api.ChatColor.DARK_PURPLE + "El obelisco te ha llevado a la arena.");
        }
    }

    /** Se llama periodicamente (ver BossTickTask) para disparar habilidades y refrescar la barra de vida. */
    public void tick() {
        for (BossActivo activo : List.copyOf(activosPorEntidad.values())) {
            if (!activo.estaViva()) {
                if (activo.getEntidad().isDead()) continue; // la muerte la gestiona manejarMuerte
                // Si el chunk esta descargado la entidad parece invalida pero sigue existiendo: no la tocamos.
                Location l = activo.getEntidad().getLocation();
                if (l.getWorld() != null && !l.getWorld().isChunkLoaded(l.getBlockX() >> 4, l.getBlockZ() >> 4)) continue;
                // Desaparecio sin morir (ej. removida por un admin): se cuenta como terminada, sin victoria.
                jefeTerminado(activo, false);
                continue;
            }
            actualizarBarraDeVida(activo);
            ejecutarHabilidadesSiCorresponde(activo);
        }
    }

    private void actualizarBarraDeVida(BossActivo activo) {
        BossBar barra = barrasDeVida.get(activo.getEntidad().getUniqueId());
        if (barra == null) return;
        var atributo = activo.getEntidad().getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double vidaMaxima = atributo != null ? atributo.getValue() : activo.getTemplate().getVidaMaxima();
        double progreso = vidaMaxima <= 0 ? 0 : Math.max(0, Math.min(1, activo.getEntidad().getHealth() / vidaMaxima));
        barra.setProgress(progreso);
    }

    private void ejecutarHabilidadesSiCorresponde(BossActivo activo) {
        // Los jefes de MythicMobs llevan sus propias fases y skills: BossForge no se mete.
        if (activo.getTemplate().esMythic()) return;
        double vidaMaxima = activo.getEntidad().getAttribute(Attribute.GENERIC_MAX_HEALTH) != null
            ? activo.getEntidad().getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() : activo.getTemplate().getVidaMaxima();
        double porcentajeVidaActual = vidaMaxima <= 0 ? 0 : (activo.getEntidad().getHealth() / vidaMaxima) * 100;

        List<Player> jugadoresConectados = activo.getJugadoresEnCombate().stream().filter(Player::isOnline).toList();
        ContextoHabilidadBoss contexto = new ContextoHabilidadBoss(plugin, activo.getEntidad(), jugadoresConectados, logger);

        comprobarCambioDeFase(activo, porcentajeVidaActual, jugadoresConectados);

        for (HabilidadBossInstancia habilidad : activo.getTemplate().getHabilidades()) {
            if (!habilidad.activaEnFase(activo.getFaseActual())) continue;
            try {
                if (habilidad.getTipoDisparo() == TipoDisparoHabilidad.PERIODICA) {
                    if (activo.puedeEjecutarPeriodica(habilidad)) {
                        ejecutar(habilidad, contexto);
                        activo.marcarEjecutada(habilidad);
                    }
                } else if (habilidad.getTipoDisparo() == TipoDisparoHabilidad.AL_BAJAR_DE_VIDA) {
                    if (!activo.yaSeDisparoLaHabilidadDeVidaBaja(habilidad) && porcentajeVidaActual <= habilidad.getPorcentajeVidaUmbral()) {
                        ejecutar(habilidad, contexto);
                        activo.marcarDisparadaPorVidaBaja(habilidad);
                    }
                }
            } catch (Exception e) {
                // Una habilidad fallando nunca debe tumbar el resto del combate.
                logger.warning("[BossForge] Error ejecutando la habilidad '" + habilidad.getHabilidadId()
                    + "' del jefe '" + activo.getTemplate().getNombre() + "': " + e.getMessage());
            }
        }
    }

    /** Si el jefe tiene 2 fases y ya bajo del umbral configurado, pasa a la fase 2 una unica vez. */
    private void comprobarCambioDeFase(BossActivo activo, double porcentajeVidaActual, List<Player> jugadoresConectados) {
        BossTemplate template = activo.getTemplate();
        if (template.getNumeroFases() < 2 || activo.getFaseActual() >= 2) return;
        if (porcentajeVidaActual > template.getPorcentajeVidaCambioFase()) return;

        activo.avanzarFase();
        String mensaje = net.md_5.bungee.api.ChatColor.RED + "☠ " + template.getNombre()
            + net.md_5.bungee.api.ChatColor.GOLD + " entra en su segunda fase!";
        for (Player jugador : jugadoresConectados) jugador.sendMessage(mensaje);
        activo.getEntidad().getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION, activo.getEntidad().getLocation(), 3);
    }

    private void ejecutar(HabilidadBossInstancia habilidad, ContextoHabilidadBoss contexto) {
        registry.obtener(habilidad.getHabilidadId()).ifPresentOrElse(
            entrada -> entrada.ejecutor().ejecutar(habilidad, contexto),
            () -> logger.warning("[BossForge] Habilidad desconocida en la configuracion: " + habilidad.getHabilidadId())
        );
    }

    public double reduccionEscudoActiva(LivingEntity entidad) {
        return escudos.porcentajeActivo(entidad.getUniqueId());
    }

    /** Llamado desde el listener de EntityDeathEvent. */
    public void manejarMuerte(LivingEntity entidad) {
        BossActivo activo = activosPorEntidad.get(entidad.getUniqueId());
        if (activo == null) return;
        jefeTerminado(activo, true);
    }

    /** Un jefe ya no esta en combate (murio o desaparecio): si era el ultimo, se cierra el encuentro. */
    private void jefeTerminado(BossActivo activo, boolean derrotado) {
        Encuentro encuentro = encuentros.get(activo.getObelisco().getId());
        Location donde = activo.getEntidad().getLocation();
        quitarActivo(activo);
        if (encuentro == null) return;

        encuentro.getActivos().remove(activo);
        if (derrotado) encuentro.registrarDerrota(donde);
        if (encuentro.getActivos().isEmpty()) finalizar(encuentro);
    }

    /** Cierra el combate: con victoria reparte el loot de la opcion elegida y pone el cooldown; sin victoria deja el obelisco listo otra vez. */
    private void finalizar(Encuentro encuentro) {
        if (encuentro.isFinalizado()) return;
        encuentro.setFinalizado(true);
        encuentros.remove(encuentro.getObelisco().getId());

        Obelisco obelisco = encuentro.getObelisco();
        if (encuentro.getJefesDerrotados() > 0) {
            repartirLoot(obelisco, encuentro.getOpcion(), encuentro.getUltimaMuerte());
            obelisco.setEstado(EstadoObelisco.EN_COOLDOWN);
            obelisco.setUltimaVezCompletadoEpochMillis(System.currentTimeMillis());
        } else {
            obelisco.setEstado(EstadoObelisco.ESPERANDO);
        }
        if (obeliskManager != null) obeliskManager.guardarTodo();
    }

    private void repartirLoot(Obelisco obelisco, OpcionJefe opcion, Location dondeSoltar) {
        if (dondeSoltar == null || dondeSoltar.getWorld() == null) dondeSoltar = obelisco.getArenaSpawn() != null ? obelisco.getArenaSpawn() : obelisco.getUbicacion();
        if (dondeSoltar == null || dondeSoltar.getWorld() == null) return;
        for (LootEntry entrada : opcion.getLoot()) {
            if (Math.random() * 100.0 <= entrada.getProbabilidad()) {
                dondeSoltar.getWorld().dropItemNaturally(dondeSoltar, entrada.getItem().clone());
            }
        }
    }

    private void quitarActivo(BossActivo activo) {
        UUID uuid = activo.getEntidad().getUniqueId();
        activosPorEntidad.remove(uuid);
        escudos.limpiar(uuid);
        BossBar barra = barrasDeVida.remove(uuid);
        if (barra != null) barra.removeAll();
    }

    /**
     * Un participante sale del combate (murio o se desconecto). Si era el ultimo, el combate se cancela:
     * los jefes se eliminan, no hay loot ni cooldown y el obelisco queda listo para volver a activarse.
     */
    public void jugadorFuera(Player jugador, boolean murio) {
        for (Encuentro encuentro : List.copyOf(encuentros.values())) {
            if (!encuentro.getJugadores().remove(jugador)) continue;

            for (BossActivo activo : encuentro.getActivos()) {
                activo.getJugadoresEnCombate().remove(jugador);
                BossBar barra = barrasDeVida.get(activo.getEntidad().getUniqueId());
                if (barra != null) barra.removePlayer(jugador);
            }
            if (!encuentro.getJugadores().isEmpty()) continue;

            String clave = murio ? "obeliscos.cancelar_si_todos_mueren" : "obeliscos.cancelar_si_todos_desconectan";
            if (plugin.getConfig().getBoolean(clave, true)) {
                cancelarEncuentro(encuentro);
                logger.info("[BossForge] Combate de '" + encuentro.getObelisco().getNombre() + "' cancelado: "
                    + (murio ? "todos los participantes murieron." : "todos los participantes se desconectaron."));
            }
        }
    }

    /** Termina un combate sin victoria: quita los jefes y resetea el obelisco (sin loot, sin cooldown). */
    private void cancelarEncuentro(Encuentro encuentro) {
        if (encuentro.isFinalizado()) return;
        encuentro.setFinalizado(true);
        encuentros.remove(encuentro.getObelisco().getId());
        for (BossActivo activo : List.copyOf(encuentro.getActivos())) {
            if (activo.getEntidad().isValid()) activo.getEntidad().remove();
            quitarActivo(activo);
        }
        encuentro.getActivos().clear();
        encuentro.getObelisco().setEstado(EstadoObelisco.ESPERANDO);
        if (obeliskManager != null) obeliskManager.guardarTodo();
    }

    /** Se llama en onDisable: quita los jefes en combate (no quedan huerfanos) y deja los obeliscos listos. */
    public void cancelarTodosLosCombates() {
        for (Encuentro encuentro : List.copyOf(encuentros.values())) cancelarEncuentro(encuentro);
        encuentros.clear();
        activosPorEntidad.clear();
        escudos.limpiarTodo();
    }
}
