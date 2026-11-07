package com.bossforge.boss;

import com.bossforge.model.BossTemplate;
import com.bossforge.model.HabilidadBossInstancia;
import com.bossforge.model.Obelisco;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BossActivo {
    private final LivingEntity entidad;
    private final BossTemplate template;
    private final Obelisco obelisco;
    private final List<Player> jugadoresEnCombate = new ArrayList<>();

    private final Map<HabilidadBossInstancia, Long> proximaEjecucionMillis = new HashMap<>();
    private final Set<HabilidadBossInstancia> disparadasPorVidaBaja = new HashSet<>();
    private int faseActual = 1;

    public BossActivo(LivingEntity entidad, BossTemplate template, Obelisco obelisco, List<Player> jugadoresIniciales) {
        this.entidad = entidad;
        this.template = template;
        this.obelisco = obelisco;
        this.jugadoresEnCombate.addAll(jugadoresIniciales);
    }

    public LivingEntity getEntidad() { return entidad; }
    public BossTemplate getTemplate() { return template; }
    public Obelisco getObelisco() { return obelisco; }
    public List<Player> getJugadoresEnCombate() { return jugadoresEnCombate; }

    public boolean puedeEjecutarPeriodica(HabilidadBossInstancia habilidad) {
        Long proxima = proximaEjecucionMillis.get(habilidad);
        return proxima == null || System.currentTimeMillis() >= proxima;
    }

    public void marcarEjecutada(HabilidadBossInstancia habilidad) {
        long proxima = System.currentTimeMillis() + Math.round(habilidad.getCooldownSegundos() * 1000);
        proximaEjecucionMillis.put(habilidad, proxima);
    }

    public boolean yaSeDisparoLaHabilidadDeVidaBaja(HabilidadBossInstancia habilidad) {
        return disparadasPorVidaBaja.contains(habilidad);
    }

    public void marcarDisparadaPorVidaBaja(HabilidadBossInstancia habilidad) {
        disparadasPorVidaBaja.add(habilidad);
    }

    public boolean estaViva() {
        return entidad != null && entidad.isValid() && !entidad.isDead();
    }

    public int getFaseActual() { return faseActual; }

    /** Avanza a la siguiente fase y limpia los disparos "al bajar de vida" para que puedan volver a ocurrir en la fase nueva. */
    public void avanzarFase() {
        faseActual++;
        disparadasPorVidaBaja.clear();
    }
}
