package com.bossforge.habilidades.impl;

import com.bossforge.habilidades.ContextoHabilidadBoss;
import com.bossforge.habilidades.HabilidadBossEjecutor;
import com.bossforge.model.HabilidadBossInstancia;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

public class AtaqueAreaHabilidad implements HabilidadBossEjecutor {
    @Override
    public void ejecutar(HabilidadBossInstancia instancia, ContextoHabilidadBoss ctx) {
        double radio = Math.max(1, instancia.parametro("radio", 4));
        double dano = Math.max(0, instancia.parametro("dano", 6));

        ctx.getBoss().getWorld().spawnParticle(Particle.EXPLOSION, ctx.getBoss().getLocation(), 1);
        for (Player jugador : ctx.getJugadoresEnArena()) {
            if (jugador.getLocation().distance(ctx.getBoss().getLocation()) <= radio) {
                jugador.damage(dano, ctx.getBoss());
            }
        }
    }
}
