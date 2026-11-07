package com.bossforge.habilidades.impl;

import com.bossforge.habilidades.ContextoHabilidadBoss;
import com.bossforge.habilidades.HabilidadBossEjecutor;
import com.bossforge.model.HabilidadBossInstancia;
import org.bukkit.entity.Player;

import java.util.List;

public class InvocarRayoHabilidad implements HabilidadBossEjecutor {
    @Override
    public void ejecutar(HabilidadBossInstancia instancia, ContextoHabilidadBoss ctx) {
        List<Player> jugadores = ctx.getJugadoresEnArena();
        if (jugadores.isEmpty()) return;
        Player objetivo = jugadores.get((int) (Math.random() * jugadores.size()));
        boolean dañaAlGolpear = instancia.parametro("dañino", 1) >= 0.5;

        if (dañaAlGolpear) {
            objetivo.getWorld().strikeLightning(objetivo.getLocation());
        } else {
            objetivo.getWorld().strikeLightningEffect(objetivo.getLocation());
        }
    }
}
