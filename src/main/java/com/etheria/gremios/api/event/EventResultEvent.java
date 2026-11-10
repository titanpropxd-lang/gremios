package com.etheria.gremios.api.event;

import com.etheria.gremios.model.Guild;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Un evento del servidor (KOTH o jefe) terminó. isWon()=false si nadie lo ganó (tiempo agotado / el jefe resistió). */
public class EventResultEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String eventId;
    private final String eventName;
    private final String type;
    private final String world;
    private final boolean won;
    private final Guild winner;

    public EventResultEvent(String eventId, String eventName, String type, String world, boolean won, Guild winner) {
        this.eventId = eventId;
        this.eventName = eventName;
        this.type = type;
        this.world = world;
        this.won = won;
        this.winner = winner;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventName() {
        return eventName;
    }

    /** KOTH o JEFE. */
    public String getType() {
        return type;
    }

    public String getWorld() {
        return world;
    }

    public boolean isWon() {
        return won;
    }

    /** Gremio ganador (puede ser null, p. ej. si cayó el jefe). */
    public Guild getWinner() {
        return winner;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
