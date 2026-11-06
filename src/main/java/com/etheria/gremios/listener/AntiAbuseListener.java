package com.etheria.gremios.listener;

import com.etheria.gremios.GremiosPlugin;
import com.etheria.gremios.model.Guild;
import com.etheria.gremios.model.Raid;
import com.etheria.gremios.util.Msg;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;

/** Evita esconder botín en el cofre de Ender mientras tu gremio está bajo asedio activo. */
public class AntiAbuseListener implements Listener {
    private final GremiosPlugin plugin;

    public AntiAbuseListener(GremiosPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        if (e.getInventory().getType() != InventoryType.ENDER_CHEST) return;
        if (!plugin.getConfig().getBoolean("antiabuso.activo", true)) return;
        if (!plugin.getConfig().getBoolean("antiabuso.bloquear-ender-en-asedio", true)) return;
        if (!(e.getPlayer() instanceof Player p) || p.hasPermission("gremios.admin")) return;
        Guild g = plugin.guilds.of(p);
        if (g == null) return;
        Raid r = plugin.raids.of(g);
        if (r != null && !r.test && (r.state == Raid.State.ACTIVE || r.state == Raid.State.LOOT)) {
            e.setCancelled(true);
            Msg.to(p, "§cNo puedes usar el cofre de Ender mientras tu gremio está en pleno asedio.");
        }
    }
}
