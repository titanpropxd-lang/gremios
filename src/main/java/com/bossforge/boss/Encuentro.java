package com.bossforge.boss;

import com.bossforge.model.Obelisco;
import com.bossforge.model.OpcionJefe;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Un combate en curso de un obelisco: la opcion de jefe elegida, quienes participan y los jefes vivos. */
public class Encuentro {
    private final Obelisco obelisco;
    private final OpcionJefe opcion;
    private final List<Player> jugadores;
    private final List<BossActivo> activos = new ArrayList<>();
    private int jefesDerrotados = 0;
    private Location ultimaMuerte;
    private boolean finalizado = false;

    public Encuentro(Obelisco obelisco, OpcionJefe opcion, List<Player> jugadores) {
        this.obelisco = obelisco;
        this.opcion = opcion;
        this.jugadores = new ArrayList<>(jugadores);
    }

    public Obelisco getObelisco() { return obelisco; }
    public OpcionJefe getOpcion() { return opcion; }
    public List<Player> getJugadores() { return jugadores; }
    public List<BossActivo> getActivos() { return activos; }
    public int getJefesDerrotados() { return jefesDerrotados; }
    public Location getUltimaMuerte() { return ultimaMuerte; }
    public boolean isFinalizado() { return finalizado; }
    public void setFinalizado(boolean v) { this.finalizado = v; }

    public void registrarDerrota(Location donde) {
        jefesDerrotados++;
        ultimaMuerte = donde != null ? donde.clone() : ultimaMuerte;
    }
}
