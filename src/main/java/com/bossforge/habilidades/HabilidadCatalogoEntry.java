package com.bossforge.habilidades;

import java.util.List;

public record HabilidadCatalogoEntry(
    String id,
    String nombre,
    String descripcion,
    List<ParametroDefinicion> parametros,
    HabilidadBossEjecutor ejecutor
) { }
