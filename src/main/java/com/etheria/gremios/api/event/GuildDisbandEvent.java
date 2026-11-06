package com.etheria.gremios.api.event;

import com.etheria.gremios.model.Guild;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Se va a disolver un gremio (aún existe cuando se lanza). */
public class GuildDisbandEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Guild guild;

    public GuildDisbandEvent(Guild guild) {
        this.guild = guild;
    }

    public Guild getGuild() {
        return guild;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
