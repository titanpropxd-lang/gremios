package com.bossforge.habilidades.impl;

import com.bossforge.habilidades.ContextoHabilidadBoss;
import com.bossforge.habilidades.HabilidadBossEjecutor;
import com.bossforge.model.HabilidadBossInstancia;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public class NubeVenenoHabilidad implements HabilidadBossEjecutor {
    @Override
    public void ejecutar(HabilidadBossInstancia instancia, ContextoHabilidadBoss ctx) {
        double radio = Math.max(1, instancia.parametro("radio", 4));
        int duracionTicks = (int) Math.round(Math.max(1, instancia.parametro("duracionSegundos", 6)) * 20);
        Location centro = ctx.getBoss().getLocation();
        Plugin plugin = ctx.getPlugin();

        new BukkitRunnable() {
            int tiempoRestante = duracionTicks;

            @Override
            public void run() {
                if (tiempoRestante <= 0 || !ctx.getBoss().isValid()) {
                    cancel();
                    return;
                }
                centro.getWorld().spawnParticle(Particle.SNEEZE, centro, 10, radio / 2, 0.1, radio / 2);
                for (Player jugador : ctx.getJugadoresEnArena()) {
                    if (jugador.getLocation().distance(centro) <= radio) {
                        jugador.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 40, 0));
                    }
                }
                tiempoRestante -= 10;
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }
}
