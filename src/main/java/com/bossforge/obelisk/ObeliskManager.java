package com.bossforge.obelisk;

import com.bossforge.boss.BossManager;
import com.bossforge.boss.BossTemplateManager;
import com.bossforge.integration.MythicHook;
import com.bossforge.model.BossTemplate;
import com.bossforge.model.EstadoObelisco;
import com.bossforge.model.Obelisco;
import com.bossforge.model.OpcionJefe;
import com.bossforge.storage.ObeliskStorage;
import com.bossforge.util.ItemMatchUtil;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

public class ObeliskManager {
    private final JavaPlugin plugin;
    private final Logger logger;
    private final ObeliskStorage storage;
    private final BossTemplateManager bossTemplateManager;
    private final BossManager bossManager;
    private final MythicHook mythic;
    private final Map<String, Obelisco> obeliscos;

    private final long cooldownReinicioSegundos;
    private final int cuentaAtrasSegundos;
    private final Map<String, Invocacion> invocaciones = new HashMap<>();

    public ObeliskManager(JavaPlugin plugin, ObeliskStorage storage, BossTemplateManager bossTemplateManager, BossManager bossManager, MythicHook mythic) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.storage = storage;
        this.bossTemplateManager = bossTemplateManager;
        this.bossManager = bossManager;
        this.mythic = mythic;
        this.obeliscos = new LinkedHashMap<>(storage.cargar());

        FileConfiguration cfg = plugin.getConfig();
        this.cooldownReinicioSegundos = cfg.getLong("obeliscos.cooldown_reinicio_segundos", 300);
        this.cuentaAtrasSegundos = Math.max(0, cfg.getInt("invocacion.cuenta_atras_segundos", 10));
    }

    public List<Obelisco> todos() {
        return List.copyOf(obeliscos.values());
    }

    public Optional<Obelisco> obtener(String id) {
        return Optional.ofNullable(obeliscos.get(id));
    }

    /** Busca el obelisco vinculado a un bloque concreto (por coordenadas+mundo), si hay alguno. */
    public Optional<Obelisco> obtenerPorUbicacion(Location ubicacion) {
        for (Obelisco o : obeliscos.values()) {
            if (o.getUbicacion() != null && mismoBloque(o.getUbicacion(), ubicacion)) return Optional.of(o);
        }
        return Optional.empty();
    }

    private boolean mismoBloque(Location a, Location b) {
        return a.getWorld() != null && a.getWorld().equals(b.getWorld())
            && a.getBlockX() == b.getBlockX() && a.getBlockY() == b.getBlockY() && a.getBlockZ() == b.getBlockZ();
    }

    public Obelisco crear(String nombre) {
        Obelisco nuevo = new Obelisco();
        nuevo.setNombre(nombre);
        obeliscos.put(nuevo.getId(), nuevo);
        guardarTodo();
        return nuevo;
    }

    public void eliminar(String id) {
        obeliscos.remove(id);
        guardarTodo();
    }

    public void actualizar(Obelisco obelisco) {
        obeliscos.put(obelisco.getId(), obelisco);
        guardarTodo();
    }

    public void guardarTodo() {
        storage.guardar(obeliscos);
    }

    /**
     * ¿Puede activarse ahora mismo? Devuelve el motivo por el que NO
     * puede (para mostrarselo al jugador), o null si si puede.
     */
    public String razonPorLaQueNoPuedeActivarse(Obelisco obelisco) {
        if (!obelisco.isActivo()) return "Este obelisco esta desactivado.";
        if (!obelisco.listoParaActivarse()) return "Este obelisco todavia no esta completamente configurado.";
        if (bossManager.hayBossActivoEn(obelisco)) return "Ya hay un combate en curso en este obelisco.";
        if (invocaciones.containsKey(obelisco.getId())) return "Ya se esta invocando un jefe en este obelisco.";
        if (obelisco.getEstado() == EstadoObelisco.EN_COOLDOWN) {
            long restante = segundosDeCooldownRestantes(obelisco);
            if (restante > 0) return "Este obelisco se esta recargando (" + restante + "s restantes).";
        }
        return null;
    }

    public long segundosDeCooldownRestantes(Obelisco obelisco) {
        long transcurrido = (System.currentTimeMillis() - obelisco.getUltimaVezCompletadoEpochMillis()) / 1000;
        return Math.max(0, cooldownReinicioSegundos - transcurrido);
    }

    /** ¿Se puede invocar ESTA opcion ahora mismo (jefe existente, MythicMobs activo...)? Devuelve el motivo si no, o null. */
    public String razonPorLaQueLaOpcionNoSePuede(OpcionJefe opcion) {
        if (!opcion.estaCompleta()) return "Esta opcion no esta configurada del todo (falta el jefe o los requisitos).";
        Optional<BossTemplate> template = bossTemplateManager.obtener(opcion.getBossTemplateId());
        if (template.isEmpty()) return "El jefe de esta opcion ya no existe.";
        if (template.get().esMythic()) {
            if (!mythic.disponible()) return "MythicMobs no esta activo en el servidor.";
            if (!mythic.existe(template.get().getMythicMobId())) {
                return "El mob '" + template.get().getMythicMobId() + "' ya no existe en MythicMobs.";
            }
        }
        return null;
    }

    /**
     * Intenta activar el obelisco con la opcion de jefe elegida y los items que el jugador deposito.
     * Devuelve un mensaje de error, o null si se activo con exito (y ya invoco al jefe).
     */
    public String intentarActivar(Obelisco obelisco, OpcionJefe opcion, Player jugador, ItemStack[] contenidoDepositado, Inventory inventarioDeposito) {
        String razonBloqueo = razonPorLaQueNoPuedeActivarse(obelisco);
        if (razonBloqueo != null) return razonBloqueo;
        String razonOpcion = razonPorLaQueLaOpcionNoSePuede(opcion);
        if (razonOpcion != null) return razonOpcion;

        List<ItemStack> faltantes = ItemMatchUtil.requisitosFaltantes(contenidoDepositado, opcion.getRequisitos());
        if (!faltantes.isEmpty()) {
            return "Faltan " + faltantes.size() + " item(s) requerido(s). Revisa la lista de requisitos de este jefe.";
        }

        // Los requisitos se consumen al empezar la cuenta atras; si la invocacion falla se devuelven.
        ItemMatchUtil.consumirRequisitos(inventarioDeposito, opcion.getRequisitos());
        iniciarInvocacion(obelisco, opcion, jugador);
        return null;
    }

    private void iniciarInvocacion(Obelisco obelisco, OpcionJefe opcion, Player activador) {
        Invocacion invocacion = new Invocacion(obelisco, opcion, activador);
        invocaciones.put(obelisco.getId(), invocacion);
        if (cuentaAtrasSegundos <= 0) {
            invocacion.terminar();
        } else {
            invocacion.runTaskTimer(plugin, 0L, 5L);
        }
    }

    /** Se llama en onDisable: cancela las cuentas atras en curso y devuelve los requisitos. */
    public void cancelarInvocaciones() {
        for (Invocacion invocacion : List.copyOf(invocaciones.values())) {
            try { invocacion.cancel(); } catch (IllegalStateException ignored) { }
            invocacion.reembolsar();
        }
        invocaciones.clear();
    }

    /**
     * Cuenta atras de la invocacion: una esfera de particulas (del color del faro) con el radio de
     * convocatoria; al terminar, los jugadores DENTRO de la esfera son llevados a la arena y aparece el jefe.
     */
    private final class Invocacion extends BukkitRunnable {
        private final Obelisco obelisco;
        private final OpcionJefe opcion;
        private final Player activador;
        private final Location centro;
        private final boolean soloActivador;
        private final double radioEsfera;
        private int ticksRestantes = cuentaAtrasSegundos * 20;
        private double giro = 0;

        Invocacion(Obelisco obelisco, OpcionJefe opcion, Player activador) {
            this.obelisco = obelisco;
            this.opcion = opcion;
            this.activador = activador;
            this.soloActivador = obelisco.getRadioConvocatoria() <= 0;
            this.radioEsfera = soloActivador ? 2.5 : obelisco.getRadioConvocatoria();
            this.centro = obelisco.getUbicacion().clone().add(0.5, 1.0, 0.5);
        }

        private String nombreJefe() {
            return bossTemplateManager.obtener(opcion.getBossTemplateId())
                .map(t -> net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', t.getNombre()))
                .orElse("el jefe");
        }

        @Override
        public void run() {
            if (ticksRestantes <= 0) {
                cancel();
                terminar();
                return;
            }
            dibujarEsfera();
            if (ticksRestantes % 20 == 0) avisar(ticksRestantes / 20);
            ticksRestantes -= 5;
        }

        private void dibujarEsfera() {
            if (centro.getWorld() == null) return;
            Particle.DustOptions polvo = new Particle.DustOptions(obelisco.getColorFaro(), 1.6f);
            int puntos = (int) Math.max(60, Math.min(320, 60 + radioEsfera * 14));
            double anguloAureo = Math.PI * (3 - Math.sqrt(5));
            for (int i = 0; i < puntos; i++) {
                double y = 1 - 2.0 * (i + 0.5) / puntos;
                double r = Math.sqrt(Math.max(0, 1 - y * y));
                double theta = anguloAureo * i + giro;
                centro.getWorld().spawnParticle(Particle.DUST,
                    centro.getX() + Math.cos(theta) * r * radioEsfera,
                    centro.getY() + y * radioEsfera,
                    centro.getZ() + Math.sin(theta) * r * radioEsfera,
                    1, 0, 0, 0, 0, polvo);
            }
            giro += 0.15;
        }

        private void avisar(int segundos) {
            if (centro.getWorld() == null) return;
            boolean primera = segundos == cuentaAtrasSegundos;
            double alcance = radioEsfera + 15;
            for (Player p : centro.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(centro) > alcance * alcance) continue;
                boolean dentro = p.getLocation().distanceSquared(centro) <= radioEsfera * radioEsfera;
                if (primera) {
                    p.sendMessage(net.md_5.bungee.api.ChatColor.DARK_PURPLE + "El obelisco empieza a invocar a " + nombreJefe()
                        + net.md_5.bungee.api.ChatColor.DARK_PURPLE + ". En " + segundos + "s, quienes esten dentro de la esfera seran llevados a la arena.");
                }
                String texto = (dentro ? net.md_5.bungee.api.ChatColor.GREEN + "Dentro de la esfera" : net.md_5.bungee.api.ChatColor.RED + "¡Entra en la esfera!")
                    + net.md_5.bungee.api.ChatColor.GRAY + "  |  " + net.md_5.bungee.api.ChatColor.LIGHT_PURPLE + nombreJefe()
                    + net.md_5.bungee.api.ChatColor.GRAY + " en " + net.md_5.bungee.api.ChatColor.WHITE + segundos + "s";
                p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, new net.md_5.bungee.api.chat.TextComponent(texto));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 0.6f + (cuentaAtrasSegundos - segundos) * 0.08f);
            }
        }

        private List<Player> participantes() {
            List<Player> lista = new ArrayList<>();
            if (soloActivador) {
                if (activador.isOnline()) lista.add(activador);
                return lista;
            }
            if (centro.getWorld() == null) return lista;
            double radioCuadrado = radioEsfera * radioEsfera;
            for (Player p : centro.getWorld().getPlayers()) {
                if (p.getGameMode() == GameMode.SPECTATOR) continue;
                if (p.getLocation().distanceSquared(centro) <= radioCuadrado) lista.add(p);
            }
            return lista;
        }

        /** Fin de la cuenta atras: lleva a los de dentro de la esfera a la arena e invoca al jefe. */
        void terminar() {
            invocaciones.remove(obelisco.getId());
            List<Player> dentro = participantes();
            if (dentro.isEmpty()) {
                fallar("Nadie estaba dentro de la esfera: la invocacion se cancelo y se devolvieron los items.");
                return;
            }
            String problema = razonPorLaQueLaOpcionNoSePuede(opcion);
            BossTemplate jefe = bossTemplateManager.obtener(opcion.getBossTemplateId()).orElse(null);
            if (problema != null || jefe == null || !bossManager.invocar(obelisco, opcion, jefe, dentro)) {
                fallar((problema != null ? problema : "No se pudo invocar el jefe (revisa la consola).") + " Se devolvieron los items.");
                return;
            }
            if (centro.getWorld() != null) centro.getWorld().playSound(centro, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.6f);
            guardarTodo();
        }

        private void fallar(String mensaje) {
            reembolsar();
            if (activador.isOnline()) activador.sendMessage(net.md_5.bungee.api.ChatColor.RED + mensaje);
        }

        /** Devuelve los requisitos al jugador que activo el obelisco (o los suelta en el obelisco si ya no esta). */
        void reembolsar() {
            for (ItemStack requisito : opcion.getRequisitos()) {
                ItemStack copia = requisito.clone();
                if (activador.isOnline()) {
                    activador.getInventory().addItem(copia)
                        .values().forEach(resto -> activador.getWorld().dropItemNaturally(activador.getLocation(), resto));
                } else if (centro.getWorld() != null) {
                    centro.getWorld().dropItemNaturally(centro, copia);
                }
            }
        }
    }
}
