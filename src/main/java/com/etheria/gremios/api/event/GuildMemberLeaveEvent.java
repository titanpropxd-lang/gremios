package com.etheria.gremios.api.event;

import com.etheria.gremios.model.Guild;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Un jugador sale o es expulsado de un gremio. */
public class GuildMemberLeaveEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Guild guild;
    private final UUID player;
    private final boolean kicked;

    public GuildMemberLeaveEvent(Guild guild, UUID player, boolean kicked) {
        this.guild = guild;
        this.player = player;
        this.kicked = kicked;
    }

    public Guild getGuild() {
        return guild;
    }

    public UUID getPlayer() {
        return player;
    }

    public boolean isKicked() {
        return kicked;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
