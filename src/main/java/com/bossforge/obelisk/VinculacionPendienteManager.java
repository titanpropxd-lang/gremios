package com.bossforge.obelisk;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Mientras un admin esta "vinculando" un obelisco a un bloque, guardamos aqui que obelisco espera. */
public class VinculacionPendienteManager {
    private final Map<UUID, String> pendientes = new ConcurrentHashMap<>();

    public void marcarEsperando(UUID jugador, String obeliscoId) {
        pendientes.put(jugador, obeliscoId);
    }

    public String consumir(UUID jugador) {
        return pendientes.remove(jugador);
    }

    public boolean estaEsperando(UUID jugador) {
        return pendientes.containsKey(jugador);
    }
}
