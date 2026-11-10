package com.etheria.gremios.api.event;

import com.etheria.gremios.model.Guild;
import com.etheria.gremios.model.Raid;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Un asedio terminó. attackerWon=true si cayó el Nodo. */
public class RaidEndEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Raid raid;
    private final Guild attacker;
    private final Guild defender;
    private final boolean attackerWon;
    private final long stolen;

    public RaidEndEvent(Raid raid, Guild attacker, Guild defender, boolean attackerWon, long stolen) {
        this.raid = raid;
        this.attacker = attacker;
        this.defender = defender;
        this.attackerWon = attackerWon;
        this.stolen = stolen;
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

    public boolean isAttackerWon() {
        return attackerWon;
    }

    public long getStolen() {
        return stolen;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
