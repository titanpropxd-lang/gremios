package com.bossforge.habilidades.impl;

import com.bossforge.habilidades.ContextoHabilidadBoss;
import com.bossforge.habilidades.HabilidadBossEjecutor;
import com.bossforge.model.HabilidadBossInstancia;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class TeletransporteAJugadorHabilidad implements HabilidadBossEjecutor {
    @Override
    public void ejecutar(HabilidadBossInstancia instancia, ContextoHabilidadBoss ctx) {
        Player objetivo = ctx.jugadorMasCercano();
        if (objetivo == null) return;

        double distanciaMax = Math.max(1, instancia.parametro("distanciaMax", 20));
        if (objetivo.getLocation().distance(ctx.getBoss().getLocation()) < 3) return; // ya esta cerca, no hace falta

        if (objetivo.getLocation().distance(ctx.getBoss().getLocation()) > distanciaMax * 3) return; // demasiado lejos, ignora

        Location destino = objetivo.getLocation().clone().add(
            objetivo.getLocation().getDirection().multiply(-2).setY(0)
        );
        ctx.getBoss().teleport(destino);
    }
}
