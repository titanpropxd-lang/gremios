package com.bossforge.model;

/** Como se invocan los jefes cuando un obelisco tiene mas de uno asignado. */
public enum ModoInvocacion {
    /** Aparecen todos los jefes de la lista a la vez. */
    TODOS("Todos a la vez"),
    /** Aparece solo UNO de la lista, elegido al azar. */
    ALEATORIO("Uno al azar"),
    /** Aparecen de uno en uno, en el orden de la lista: el siguiente sale al morir el anterior. */
    OLEADAS("Por oleadas (en orden)");

    private final String nombre;

    ModoInvocacion(String nombre) { this.nombre = nombre; }

    public String getNombre() { return nombre; }

    public ModoInvocacion siguiente() {
        ModoInvocacion[] v = values();
        return v[(ordinal() + 1) % v.length];
    }
}
