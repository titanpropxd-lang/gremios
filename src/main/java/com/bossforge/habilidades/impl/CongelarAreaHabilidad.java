package com.bossforge.habilidades.impl;

import com.bossforge.habilidades.ContextoHabilidadBoss;
import com.bossforge.habilidades.HabilidadBossEjecutor;
import com.bossforge.model.HabilidadBossInstancia;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class CongelarAreaHabilidad implements HabilidadBossEjecutor {
    @Override
    public void ejecutar(HabilidadBossInstancia instancia, ContextoHabilidadBoss ctx) {
        double radio = Math.max(1, instancia.parametro("radio", 5));
        int duracionTicks = (int) Math.round(Math.max(1, instancia.parametro("duracionSegundos", 5)) * 20);
        int nivel = (int) Math.max(0, instancia.parametro("nivel", 2));

        Location centro = ctx.getBoss().getLocation();
        centro.getWorld().spawnParticle(Particle.SNOWFLAKE, centro, 40, radio / 2, 0.5, radio / 2, 0.01);

        for (Player jugador : ctx.getJugadoresEnArena()) {
            if (jugador.getLocation().distance(centro) <= radio) {
                jugador.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duracionTicks, nivel));
                jugador.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, duracionTicks, nivel));
            }
        }
    }
}
