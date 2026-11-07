package com.bossforge.habilidades.impl;

import com.bossforge.habilidades.ContextoHabilidadBoss;
import com.bossforge.habilidades.HabilidadBossEjecutor;
import com.bossforge.model.HabilidadBossInstancia;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class CegueraAreaHabilidad implements HabilidadBossEjecutor {
    @Override
    public void ejecutar(HabilidadBossInstancia instancia, ContextoHabilidadBoss ctx) {
        double radio = Math.max(1, instancia.parametro("radio", 6));
        int duracionTicks = (int) Math.round(Math.max(1, instancia.parametro("duracionSegundos", 4)) * 20);

        Location centro = ctx.getBoss().getLocation();
        centro.getWorld().spawnParticle(Particle.SQUID_INK, centro, 30, radio / 2, 1, radio / 2, 0.02);

        for (Player jugador : ctx.getJugadoresEnArena()) {
            if (jugador.getLocation().distance(centro) <= radio) {
                jugador.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, duracionTicks, 0));
                jugador.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, duracionTicks, 0));
            }
        }
    }
}
