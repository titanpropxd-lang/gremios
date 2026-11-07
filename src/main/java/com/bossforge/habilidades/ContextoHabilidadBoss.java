package com.bossforge.habilidades;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.logging.Logger;

public class ContextoHabilidadBoss {
    private final Plugin plugin;
    private final LivingEntity boss;
    private final List<Player> jugadoresEnArena;
    private final Logger logger;

    public ContextoHabilidadBoss(Plugin plugin, LivingEntity boss, List<Player> jugadoresEnArena, Logger logger) {
        this.plugin = plugin;
        this.boss = boss;
        this.jugadoresEnArena = jugadoresEnArena;
        this.logger = logger;
    }

    public Plugin getPlugin() { return plugin; }
    public LivingEntity getBoss() { return boss; }
    public List<Player> getJugadoresEnArena() { return jugadoresEnArena; }
    public Logger getLogger() { return logger; }

    public Player jugadorMasCercano() {
        return jugadoresEnArena.stream()
            .filter(p -> p.getWorld().equals(boss.getWorld()))
            .min((a, b) -> Double.compare(
                a.getLocation().distanceSquared(boss.getLocation()),
                b.getLocation().distanceSquared(boss.getLocation())))
            .orElse(null);
    }
}
