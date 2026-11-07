package com.bossforge.listeners;

import com.bossforge.boss.BossManager;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class BossCombateListener implements Listener {
    private final BossManager bossManager;
    private final org.bukkit.plugin.java.JavaPlugin plugin;

    public BossCombateListener(BossManager bossManager, org.bukkit.plugin.java.JavaPlugin plugin) {
        this.bossManager = bossManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDanoRecibido(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entidad)) return;
        double reduccion = bossManager.reduccionEscudoActiva(entidad);
        if (reduccion > 0) {
            event.setDamage(Math.max(0, event.getDamage() * (1 - reduccion / 100.0)));
        }
    }

    /** El loot de los jefes lo da el obelisco: se quitan los drops originales (los de vanilla; MythicMobs tiene los suyos aparte). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDropsDelJefe(EntityDeathEvent event) {
        if (!plugin.getConfig().getBoolean("jefes.anular_drops_originales", true)) return;
        if (bossManager.bossActivoDe(event.getEntity()).isPresent()) event.getDrops().clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMuerte(EntityDeathEvent event) {
        bossManager.manejarMuerte(event.getEntity());
    }

    /** Un participante que muere queda fuera del combate; si eran todos, el combate termina. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJugadorMuere(PlayerDeathEvent event) {
        bossManager.jugadorFuera(event.getEntity(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJugadorSeVa(PlayerQuitEvent event) {
        bossManager.jugadorFuera(event.getPlayer(), false);
    }
}
