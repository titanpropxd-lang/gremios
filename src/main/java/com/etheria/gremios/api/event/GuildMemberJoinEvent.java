package com.etheria.gremios.api.event;

import com.etheria.gremios.model.Guild;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Un jugador entra a un gremio. */
public class GuildMemberJoinEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Guild guild;
    private final UUID player;

    public GuildMemberJoinEvent(Guild guild, UUID player) {
        this.guild = guild;
        this.player = player;
    }

    public Guild getGuild() {
        return guild;
    }

    public UUID getPlayer() {
        return player;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
