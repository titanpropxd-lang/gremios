package com.bossforge.listeners;

import com.bossforge.model.Obelisco;
import com.bossforge.obelisk.ObeliskManager;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumSet;
import java.util.Set;

/**
 * En la arena de un jefe solo pueden aparecer los jefes y sus esbirros: se bloquean los mobs que
 * salen "solos" (naturales, spawners, refuerzos, razzias...). Lo que invocan MythicMobs, los
 * comandos o los plugins (razon CUSTOM/COMMAND/...) no se toca.
 */
public class ArenaListener implements Listener {
    private static final Set<SpawnReason> BLOQUEADOS = EnumSet.of(
        SpawnReason.NATURAL, SpawnReason.CHUNK_GEN, SpawnReason.SPAWNER, SpawnReason.TRAP,
        SpawnReason.PATROL, SpawnReason.RAID, SpawnReason.VILLAGE_INVASION, SpawnReason.VILLAGE_DEFENSE,
        SpawnReason.REINFORCEMENTS, SpawnReason.NETHER_PORTAL, SpawnReason.LIGHTNING, SpawnReason.BREEDING,
        SpawnReason.DROWNED, SpawnReason.SILVERFISH_BLOCK);

    private final JavaPlugin plugin;
    private final ObeliskManager obeliskManager;

    public ArenaListener(JavaPlugin plugin, ObeliskManager obeliskManager) {
        this.plugin = plugin;
        this.obeliskManager = obeliskManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (!BLOQUEADOS.contains(event.getSpawnReason())) return;
        if (!plugin.getConfig().getBoolean("arena.bloquear_mobs_naturales", true)) return;

        Location donde = event.getLocation();
        for (Obelisco obelisco : obeliskManager.todos()) {
            if (obelisco.arenaContiene(donde)) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
