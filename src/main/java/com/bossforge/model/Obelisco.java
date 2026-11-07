package com.bossforge.model;

import org.bukkit.Color;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Obelisco {
    private String id = UUID.randomUUID().toString();
    private String nombre = "Nuevo Obelisco";

    /** Bloque fisico que el jugador debe pulsar para abrir el deposito. Puede ser null si aun no se ha vinculado. */
    private Location ubicacion;

    private Location arenaPos1;
    private Location arenaPos2;
    /** Donde aparece el boss. Puede ser null (usa 'ubicacion'). */
    private Location arenaSpawn;

    /** Zona donde se teletransportan los jugadores al invocarse el jefe. Puede ser null (no se teletransporta a nadie). */
    private Location jugadorSpawn;
    /** Radio (en bloques) alrededor de jugadorSpawn en el que se reparten los jugadores al azar. */
    private double jugadorSpawnRadio = 2;

    /** Color del haz de luz del faro del obelisco (estilo ARK: Survival Evolved). */
    private Color colorFaro = Color.WHITE;
    /** Si el faro de luz esta visible mientras el obelisco esta activo. */
    private boolean faroActivo = true;

    /** Jefes que se pueden invocar desde este obelisco (el jugador elige uno). Cada uno con sus requisitos y su loot. */
    private List<OpcionJefe> opciones = new ArrayList<>();
    /** Jugadores a esta distancia (bloques) del obelisco son llevados a la arena al invocar. 0 = solo quien activa. */
    private double radioConvocatoria = 10;

    private boolean activo = true;
    private EstadoObelisco estado = EstadoObelisco.ESPERANDO;
    private long ultimaVezCompletadoEpochMillis = 0;

    public String getId() { return id; }
    public void setId(String v) { this.id = v; }

    public String getNombre() { return nombre; }
    public void setNombre(String v) { this.nombre = v; }

    public Location getUbicacion() { return ubicacion; }
    public void setUbicacion(Location v) { this.ubicacion = v; }

    public Location getArenaPos1() { return arenaPos1; }
    public void setArenaPos1(Location v) { this.arenaPos1 = v; }

    public Location getArenaPos2() { return arenaPos2; }
    public void setArenaPos2(Location v) { this.arenaPos2 = v; }

    public Location getArenaSpawn() { return arenaSpawn; }
    public void setArenaSpawn(Location v) { this.arenaSpawn = v; }

    public Location getJugadorSpawn() { return jugadorSpawn; }
    public void setJugadorSpawn(Location v) { this.jugadorSpawn = v; }

    public double getJugadorSpawnRadio() { return jugadorSpawnRadio; }
    public void setJugadorSpawnRadio(double v) { this.jugadorSpawnRadio = Math.max(0, v); }

    public Color getColorFaro() { return colorFaro; }
    public void setColorFaro(Color v) { this.colorFaro = v != null ? v : Color.WHITE; }

    public boolean isFaroActivo() { return faroActivo; }
    public void setFaroActivo(boolean v) { this.faroActivo = v; }

    public List<OpcionJefe> getOpciones() { return opciones; }
    public void setOpciones(List<OpcionJefe> v) { this.opciones = v != null ? v : new ArrayList<>(); }

    public OpcionJefe buscarOpcion(String id) {
        for (OpcionJefe o : opciones) if (o.getId().equals(id)) return o;
        return null;
    }

    public double getRadioConvocatoria() { return radioConvocatoria; }
    public void setRadioConvocatoria(double v) { this.radioConvocatoria = Math.max(0, v); }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean v) { this.activo = v; }

    public EstadoObelisco getEstado() { return estado; }
    public void setEstado(EstadoObelisco v) { this.estado = v; }

    public long getUltimaVezCompletadoEpochMillis() { return ultimaVezCompletadoEpochMillis; }
    public void setUltimaVezCompletadoEpochMillis(long v) { this.ultimaVezCompletadoEpochMillis = v; }

    public boolean tieneArenaCompleta() {
        return arenaPos1 != null && arenaPos2 != null
            && arenaPos1.getWorld() != null && arenaPos2.getWorld() != null
            && arenaPos1.getWorld().equals(arenaPos2.getWorld());
    }

    /** Caja (inclusiva por bloques) que forman las dos esquinas de la arena, o null si la arena no esta completa. */
    public org.bukkit.util.BoundingBox cajaArena() {
        if (!tieneArenaCompleta()) return null;
        return new org.bukkit.util.BoundingBox(
            Math.min(arenaPos1.getBlockX(), arenaPos2.getBlockX()),
            Math.min(arenaPos1.getBlockY(), arenaPos2.getBlockY()),
            Math.min(arenaPos1.getBlockZ(), arenaPos2.getBlockZ()),
            Math.max(arenaPos1.getBlockX(), arenaPos2.getBlockX()) + 1,
            Math.max(arenaPos1.getBlockY(), arenaPos2.getBlockY()) + 1,
            Math.max(arenaPos1.getBlockZ(), arenaPos2.getBlockZ()) + 1);
    }

    /** ¿Esta esa ubicacion dentro de la arena? (sin cargar chunks) */
    public boolean arenaContiene(Location l) {
        org.bukkit.util.BoundingBox caja = cajaArena();
        return caja != null && l != null && l.getWorld() != null
            && l.getWorld().equals(arenaPos1.getWorld())
            && caja.contains(l.getX(), l.getY(), l.getZ());
    }

    public boolean listoParaActivarse() {
        return ubicacion != null && tieneArenaCompleta() && opciones.stream().anyMatch(OpcionJefe::estaCompleta);
    }
}
