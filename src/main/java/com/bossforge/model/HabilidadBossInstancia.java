package com.bossforge.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Una habilidad del catalogo (ver HabilidadBossRegistry), ya
 * configurada para un boss concreto: con que frecuencia/condicion se
 * dispara y con que valores de parametros (radio, cantidad, duracion,
 * segun lo que pida esa habilidad).
 */
public class HabilidadBossInstancia {
    private String habilidadId; // clave en HabilidadBossRegistry, ej. "invocar_mobs"
    private TipoDisparoHabilidad tipoDisparo = TipoDisparoHabilidad.PERIODICA;
    private double cooldownSegundos = 10; // usado si tipoDisparo = PERIODICA
    private double porcentajeVidaUmbral = 50; // usado si tipoDisparo = AL_BAJAR_DE_VIDA
    private Map<String, Double> parametros = new HashMap<>();
    /** 0 = activa en cualquier fase, 1 = solo en fase 1, 2 = solo en fase 2. */
    private int fase = 0;

    public HabilidadBossInstancia() { }

    public HabilidadBossInstancia(String habilidadId) {
        this.habilidadId = habilidadId;
    }

    public String getHabilidadId() { return habilidadId; }
    public void setHabilidadId(String v) { this.habilidadId = v; }

    public TipoDisparoHabilidad getTipoDisparo() { return tipoDisparo; }
    public void setTipoDisparo(TipoDisparoHabilidad v) { this.tipoDisparo = v; }

    public double getCooldownSegundos() { return cooldownSegundos; }
    public void setCooldownSegundos(double v) { this.cooldownSegundos = v; }

    public double getPorcentajeVidaUmbral() { return porcentajeVidaUmbral; }
    public void setPorcentajeVidaUmbral(double v) { this.porcentajeVidaUmbral = v; }

    public Map<String, Double> getParametros() { return parametros; }
    public void setParametros(Map<String, Double> v) { this.parametros = v; }

    public int getFase() { return fase; }
    public void setFase(int v) { this.fase = Math.max(0, Math.min(2, v)); }

    /** true si esta habilidad puede dispararse estando el jefe en la fase indicada. */
    public boolean activaEnFase(int faseActual) {
        return fase == 0 || fase == faseActual;
    }

    public double parametro(String clave, double porDefecto) {
        return parametros.getOrDefault(clave, porDefecto);
    }
}
