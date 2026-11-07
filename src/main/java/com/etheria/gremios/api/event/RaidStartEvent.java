package com.etheria.gremios.api.event;

import com.etheria.gremios.model.Guild;
import com.etheria.gremios.model.Raid;
import com.etheria.gremios.model.Territory;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Empieza la fase activa de un asedio. */
public class RaidStartEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Raid raid;
    private final Guild attacker;
    private final Guild defender;
    private final Territory territory;

    public RaidStartEvent(Raid raid, Guild attacker, Guild defender, Territory territory) {
        this.raid = raid;
        this.attacker = attacker;
        this.defender = defender;
        this.territory = territory;
    }

    public Raid getRaid() {
        return raid;
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

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
