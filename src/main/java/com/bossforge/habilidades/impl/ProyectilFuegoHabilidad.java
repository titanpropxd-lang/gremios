package com.bossforge.habilidades.impl;

import com.bossforge.habilidades.ContextoHabilidadBoss;
import com.bossforge.habilidades.HabilidadBossEjecutor;
import com.bossforge.model.HabilidadBossInstancia;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class ProyectilFuegoHabilidad implements HabilidadBossEjecutor {
    @Override
    public void ejecutar(HabilidadBossInstancia instancia, ContextoHabilidadBoss ctx) {
        Player objetivo = ctx.jugadorMasCercano();
        if (objetivo == null) return;

        Vector direccion = objetivo.getEyeLocation().subtract(ctx.getBoss().getEyeLocation()).toVector().normalize();
        Fireball fireball = ctx.getBoss().launchProjectile(Fireball.class, direccion);
        double potencia = Math.max(0, instancia.parametro("potenciaExplosion", 1));
        fireball.setYield((float) potencia);
        fireball.setIsIncendiary(instancia.parametro("incendiario", 1) >= 0.5);
    }
}
