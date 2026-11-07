package com.bossforge.habilidades.impl;

import com.bossforge.habilidades.ContextoHabilidadBoss;
import com.bossforge.habilidades.HabilidadBossEjecutor;
import com.bossforge.model.HabilidadBossInstancia;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;

public class CurarseHabilidad implements HabilidadBossEjecutor {
    @Override
    public void ejecutar(HabilidadBossInstancia instancia, ContextoHabilidadBoss ctx) {
        LivingEntity boss = ctx.getBoss();
        double porcentaje = Math.max(0, Math.min(100, instancia.parametro("porcentaje", 10)));
        double vidaMaxima = boss.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null
            ? boss.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() : boss.getHealth();
        double curacion = vidaMaxima * (porcentaje / 100.0);
        boss.setHealth(Math.min(vidaMaxima, boss.getHealth() + curacion));
    }
}
