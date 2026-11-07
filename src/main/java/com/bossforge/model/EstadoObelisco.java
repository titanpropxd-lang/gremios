package com.bossforge.model;

public enum EstadoObelisco {
    /** Esperando que depositen los items requeridos. */
    ESPERANDO,
    /** Boss activo, combate en curso. */
    EN_COMBATE,
    /** Combate terminado, en cooldown antes de poder activarse de nuevo. */
    EN_COOLDOWN
}
