package com.bossforge.habilidades;

import com.bossforge.model.HabilidadBossInstancia;

public interface HabilidadBossEjecutor {
    void ejecutar(HabilidadBossInstancia instancia, ContextoHabilidadBoss contexto);
}
