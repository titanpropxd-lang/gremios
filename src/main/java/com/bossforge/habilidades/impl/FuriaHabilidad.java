package com.bossforge.habilidades.impl;

import com.bossforge.habilidades.ContextoHabilidadBoss;
import com.bossforge.habilidades.HabilidadBossEjecutor;
import com.bossforge.model.HabilidadBossInstancia;
import org.bukkit.Particle;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class FuriaHabilidad implements HabilidadBossEjecutor {
    @Override
    public void ejecutar(HabilidadBossInstancia instancia, ContextoHabilidadBoss ctx) {
        int ticks = (int) Math.round(Math.max(0, instancia.parametro("duracionSegundos", 10)) * 20);
        int nivel = (int) Math.max(0, instancia.parametro("nivel", 1));
        if (ticks <= 0) return;

        ctx.getBoss().addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, ticks, nivel));
        ctx.getBoss().getWorld().spawnParticle(Particle.ANGRY_VILLAGER, ctx.getBoss().getLocation().add(0, 1.5, 0), 6);
    }
}
