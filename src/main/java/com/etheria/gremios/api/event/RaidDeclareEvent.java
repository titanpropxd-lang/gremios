package com.etheria.gremios.api.event;

import com.etheria.gremios.model.Guild;
import com.etheria.gremios.model.Territory;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Un gremio va a declarar un asedio. Cancelable. */
public class RaidDeclareEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled;
    private String cancelReason;
    private final Guild attacker;
    private final Guild defender;
    private final Territory territory;
    private final Player declarer;

    public RaidDeclareEvent(Guild attacker, Guild defender, Territory territory, Player declarer) {
        this.attacker = attacker;
        this.defender = defender;
        this.territory = territory;
        this.declarer = declarer;
    }

    public Guild getAttacker() {
        return attacker;
    }

    public Guild getDefender() {
        return defender;
    }

    public Territory getTerritory() {
        return territory;
    }

    public Player getDeclarer() {
        return declarer;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    /** Mensaje que verá el jugador si cancelas (opcional). */
    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String reason) {
        this.cancelReason = reason;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
