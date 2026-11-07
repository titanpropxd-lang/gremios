package com.bossforge.obelisk;

import com.bossforge.model.Obelisco;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Dibuja, cada pocos ticks, un haz de particulas de color sobre cada
 * obelisco activo (con el faro encendido) - el mismo efecto visual
 * que los obeliscos de ARK: Survival Evolved, pero con el color que
 * elija el admin en la GUI.
 */
public class ObeliskBeaconTask extends BukkitRunnable {
    private static final double ALTURA_HAZ = 60;
    private static final double ESPACIADO = 0.75;
    private static final float TAMANO_PARTICULA = 1.1f;

    private final ObeliskManager obeliskManager;

    public ObeliskBeaconTask(ObeliskManager obeliskManager) {
        this.obeliskManager = obeliskManager;
    }

    @Override
    public void run() {
        for (Obelisco obelisco : obeliskManager.todos()) {
            if (!obelisco.isActivo() || !obelisco.isFaroActivo()) continue;
            Location base = obelisco.getUbicacion();
            if (base == null || base.getWorld() == null) continue;
            dibujarHaz(base, obelisco);
        }
    }

    private void dibujarHaz(Location base, Obelisco obelisco) {
        World mundo = base.getWorld();
        if (!mundo.isChunkLoaded(base.getBlockX() >> 4, base.getBlockZ() >> 4)) return;

        Particle.DustOptions opciones = new Particle.DustOptions(obelisco.getColorFaro(), TAMANO_PARTICULA);
        Location punto = base.clone().add(0.5, 1.0, 0.5);
        for (double y = 0; y < ALTURA_HAZ; y += ESPACIADO) {
            mundo.spawnParticle(Particle.DUST, punto.getX(), punto.getY() + y, punto.getZ(), 1, 0, 0, 0, 0, opciones);
        }
    }
}
