package com.etheria.gremios.listener;

import com.etheria.gremios.GremiosPlugin;
import com.etheria.gremios.manager.SiegeItems;
import com.etheria.gremios.manager.SiegeManager;
import com.etheria.gremios.model.Guild;
import com.etheria.gremios.model.NpcRole;
import com.etheria.gremios.model.Raid;
import com.etheria.gremios.model.Territory;
import com.etheria.gremios.util.Msg;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Slime;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** NPCs, armas de asedio, nodo y guardias. */
public class CombatListener implements Listener {
    private final GremiosPlugin plugin;

    public CombatListener(GremiosPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ NPCs

    @EventHandler(priority = EventPriority.HIGH)
    public void onNpcInteract(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Entity en = e.getRightClicked();
        NpcRole role = plugin.npcs.roleOf(en);
        if (role != null) {
            e.setCancelled(true);
            Territory t = plugin.npcs.territoryOf(en);
            if (t == null) return;
            plugin.guis.openForNpc(e.getPlayer(), role, t);
            return;
        }
        if (en instanceof Slime) {
            SiegeManager.Unit u = plugin.siege.unitOf(en);
            if (u == null) return;
            e.setCancelled(true);
            Player p = e.getPlayer();
            ItemStack hand = p.getInventory().getItemInMainHand();
            boolean empty = hand == null || hand.getType().isAir();
            SiegeItems.Ammo a = SiegeItems.ammoOf(plugin, hand);
            if (p.isSneaking()) {
                if (empty) plugin.siege.pickup(p, u);
                else plugin.siege.status(p, u);
            } else if (a != null) {
                plugin.siege.load(p, u, hand, a);
            } else {
                plugin.siege.fire(p, u);
            }
        }
    }

    // ------------------------------------------------------------------ daño

    private Player playerOf(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Player p) {
            if (plugin.siege.isSiegeProjectile(pr)) return null;
            return p;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent e) {
        Entity ent = e.getEntity();
        if (plugin.raids.isNode(ent)) {
            e.setCancelled(true);
            if (e instanceof EntityDamageByEntityEvent be) {
                Player src = playerOf(be.getDamager());
                if (src != null) nodeHit(src, ent, be.getDamage());
            }
            return;
        }
        SiegeManager.Unit u = plugin.siege.unitOf(ent);
        if (u != null) {
            e.setCancelled(true);
            if (e instanceof EntityDamageByEntityEvent be) {
                Player src = playerOf(be.getDamager());
                if (src != null) unitHit(src, u, be.getDamage());
            }
            return;
        }
        if (plugin.npcs.roleOf(ent) != null) e.setCancelled(true);
    }

    private void nodeHit(Player src, Entity node, double dmg) {
        Raid r = plugin.raids.raidOfNode(node);
        if (r == null) return;
        Guild pg = plugin.guilds.of(src);
        if (pg == null || !pg.id.equals(r.attackerId)) {
            Msg.actionbar(src, "§cSolo el gremio atacante puede dañar el Nodo.");
            return;
        }
        double mult = plugin.getConfig().getDouble("raid.nodo.multiplicador-melee", 2.0);
        plugin.raids.damageNode(r, dmg * mult, src);
    }

    private void unitHit(Player src, SiegeManager.Unit u, double dmg) {
        Guild pg = plugin.guilds.of(src);
        if (pg == null || pg.id.equals(u.guildId)) {
            Msg.actionbar(src, "§cNo puedes dañar esa arma.");
            return;
        }
        Raid r = plugin.raids.activeRaidOf(pg);
        if (r == null || !(r.attackerId.equals(u.guildId) || r.defenderId.equals(u.guildId))) {
            Msg.actionbar(src, "§cSolo puedes destruir armas del enemigo durante un asedio.");
            return;
        }
        plugin.siege.damageUnit(u, dmg * 1.5, src);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        if (plugin.raids.isGuard(e.getEntity())) {
            e.getDrops().clear();
            e.setDroppedExp(0);
        }
    }

    // ------------------------------------------------------------------ proyectiles y entidades

    @EventHandler(priority = EventPriority.HIGH)
    public void onProjectileHit(ProjectileHitEvent e) {
        Projectile pr = e.getEntity();
        if (plugin.siege.isSiegeProjectile(pr)) plugin.siege.impact(pr);
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        for (Entity en : e.getEntities()) {
            if (en instanceof Slime s) plugin.siege.register(s);
        }
    }

    // ------------------------------------------------------------------ colocar armas y varita

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        ItemStack hand = e.getItem();
        if (hand == null) return;

        // varita de territorios
        if (plugin.territories.isWand(hand)) {
            if (!p.hasPermission("gremios.admin")) return;
            Block b = e.getClickedBlock();
            if (b == null) return;
            if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
                e.setCancelled(true);
                plugin.territories.sel(p.getUniqueId())[0] = b.getLocation();
                Msg.to(p, "§aPosición 1: §f" + b.getX() + " " + b.getY() + " " + b.getZ());
            } else if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
                e.setCancelled(true);
                plugin.territories.sel(p.getUniqueId())[1] = b.getLocation();
                Msg.to(p, "§aPosición 2: §f" + b.getX() + " " + b.getY() + " " + b.getZ());
            }
            return;
        }

        // armas de asedio
        SiegeItems.UnitType type = SiegeItems.unitTypeOf(plugin, hand);
        if (type != null) {
            e.setCancelled(true);
            if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
            Location loc = e.getClickedBlock().getRelative(e.getBlockFace()).getLocation().add(0.5, 0, 0.5);
            String err = plugin.siege.place(p, type, loc);
            if (err != null) {
                Msg.to(p, "§c" + err);
                return;
            }
            if (p.getGameMode() != GameMode.CREATIVE) hand.setAmount(hand.getAmount() - 1);
            return;
        }
        // la munición no se puede "usar" como objeto normal
        if (SiegeItems.ammoOf(plugin, hand) != null) {
            e.setUseItemInHand(Event.Result.DENY);
        }
    }
}
