package com.bossforge.model;

import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BossTemplate {
    private String id = UUID.randomUUID().toString();
    private String nombre = "Nuevo Jefe";
    private EntityType tipoBase = EntityType.ZOMBIE;
    private double vidaMaxima = 200;
    private double danoAtaque = 10;
    private List<HabilidadBossInstancia> habilidades = new ArrayList<>();

    /**
     * Si no es null, este jefe es un mob de MythicMobs (internal name): su vida, dano, skills y
     * apariencia los define MythicMobs; aqui solo se usa para elegirlo y colocarle el loot del obelisco.
     */
    private String mythicMobId;

    /** 1 = una sola fase (comportamiento clasico), 2 = tiene una segunda fase al bajar de vida. */
    private int numeroFases = 1;
    /** % de vida al que pasa de fase 1 a fase 2 (solo aplica si numeroFases = 2). */
    private double porcentajeVidaCambioFase = 50;

    public String getMythicMobId() { return mythicMobId; }
    public void setMythicMobId(String v) { this.mythicMobId = v; }
    public boolean esMythic() { return mythicMobId != null && !mythicMobId.isBlank(); }

    public String getId() { return id; }
    public void setId(String v) { this.id = v; }

    public String getNombre() { return nombre; }
    public void setNombre(String v) { this.nombre = v; }

    public EntityType getTipoBase() { return tipoBase; }
    public void setTipoBase(EntityType v) { this.tipoBase = v; }

    public double getVidaMaxima() { return vidaMaxima; }
    public void setVidaMaxima(double v) { this.vidaMaxima = v; }

    public double getDanoAtaque() { return danoAtaque; }
    public void setDanoAtaque(double v) { this.danoAtaque = v; }

    public List<HabilidadBossInstancia> getHabilidades() { return habilidades; }
    public void setHabilidades(List<HabilidadBossInstancia> v) { this.habilidades = v; }

    public int getNumeroFases() { return numeroFases; }
    public void setNumeroFases(int v) { this.numeroFases = Math.max(1, Math.min(2, v)); }

    public double getPorcentajeVidaCambioFase() { return porcentajeVidaCambioFase; }
    public void setPorcentajeVidaCambioFase(double v) { this.porcentajeVidaCambioFase = Math.max(1, Math.min(99, v)); }
}
