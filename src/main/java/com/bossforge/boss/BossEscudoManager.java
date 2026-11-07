package com.bossforge.boss;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BossEscudoManager {
    private record Escudo(double porcentaje, long expiraEn) { }

    private final ConcurrentHashMap<UUID, Escudo> activos = new ConcurrentHashMap<>();

    public void activar(UUID bossUuid, double porcentaje, double duracionSegundos) {
        long expiraEn = System.currentTimeMillis() + Math.round(duracionSegundos * 1000);
        activos.merge(bossUuid, new Escudo(porcentaje, expiraEn),
            (actual, nuevo) -> nuevo.expiraEn() > actual.expiraEn() ? nuevo : actual);
    }

    public double porcentajeActivo(UUID bossUuid) {
        Escudo escudo = activos.get(bossUuid);
        if (escudo == null) return 0;
        if (System.currentTimeMillis() > escudo.expiraEn()) {
            activos.remove(bossUuid, escudo);
            return 0;
        }
        return escudo.porcentaje();
    }

    public void limpiar(UUID bossUuid) {
        activos.remove(bossUuid);
    }

    public void limpiarTodo() {
        activos.clear();
    }
}
