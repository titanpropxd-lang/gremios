package com.bossforge.habilidades.impl;

import com.bossforge.boss.BossEscudoManager;
import com.bossforge.habilidades.ContextoHabilidadBoss;
import com.bossforge.habilidades.HabilidadBossEjecutor;
import com.bossforge.model.HabilidadBossInstancia;

public class EscudoTemporalBossHabilidad implements HabilidadBossEjecutor {
    private final BossEscudoManager escudos;

    public EscudoTemporalBossHabilidad(BossEscudoManager escudos) {
        this.escudos = escudos;
    }

    @Override
    public void ejecutar(HabilidadBossInstancia instancia, ContextoHabilidadBoss ctx) {
        double porcentaje = Math.max(0, Math.min(100, instancia.parametro("porcentajeReduccion", 50)));
        double duracion = Math.max(0, instancia.parametro("duracionSegundos", 5));
        if (duracion <= 0) return;
        escudos.activar(ctx.getBoss().getUniqueId(), porcentaje, duracion);
    }
}
