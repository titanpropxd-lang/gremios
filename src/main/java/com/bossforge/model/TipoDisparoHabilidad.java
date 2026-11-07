package com.bossforge.model;

public enum TipoDisparoHabilidad {
    /** Se dispara cada X segundos (cooldownSegundos) mientras el boss esta en combate. */
    PERIODICA,
    /** Se dispara UNA sola vez por combate, en cuanto la vida del boss baja del porcentaje configurado. */
    AL_BAJAR_DE_VIDA
}
