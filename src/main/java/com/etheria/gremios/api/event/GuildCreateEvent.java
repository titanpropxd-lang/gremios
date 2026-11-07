package com.etheria.gremios.api.event;

import com.etheria.gremios.model.Guild;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Se fundó un gremio. */
public class GuildCreateEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Guild guild;
    private final Player leader;

    public GuildCreateEvent(Guild guild, Player leader) {
        this.guild = guild;
        this.leader = leader;
    }

    public Guild getGuild() {
        return guild;
    }

    public Player getLeader() {
        return leader;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
