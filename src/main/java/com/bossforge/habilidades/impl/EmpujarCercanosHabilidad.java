package com.bossforge.habilidades.impl;

import com.bossforge.habilidades.ContextoHabilidadBoss;
import com.bossforge.habilidades.HabilidadBossEjecutor;
import com.bossforge.model.HabilidadBossInstancia;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class EmpujarCercanosHabilidad implements HabilidadBossEjecutor {
    @Override
    public void ejecutar(HabilidadBossInstancia instancia, ContextoHabilidadBoss ctx) {
        double radio = Math.max(1, instancia.parametro("radio", 5));
        double fuerza = Math.max(0.1, instancia.parametro("fuerza", 1.5));

        ctx.getBoss().getWorld().spawnParticle(Particle.SWEEP_ATTACK, ctx.getBoss().getLocation(), 10, radio / 2, 0.2, radio / 2);
        for (Player jugador : ctx.getJugadoresEnArena()) {
            double distancia = jugador.getLocation().distance(ctx.getBoss().getLocation());
            if (distancia > radio || distancia < 0.1) continue;
            Vector direccion = jugador.getLocation().toVector().subtract(ctx.getBoss().getLocation().toVector()).normalize();
            direccion.setY(0.3);
            jugador.setVelocity(direccion.multiply(fuerza));
        }
    }
}
