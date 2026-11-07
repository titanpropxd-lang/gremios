package com.etheria.gremios.api.event;

import com.etheria.gremios.model.Guild;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Depósito o retiro en el banco del gremio (ya aplicado). */
public class GuildBankEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Guild guild;
    private final Player player;
    private final long amount;
    private final boolean deposit;

    public GuildBankEvent(Guild guild, Player player, long amount, boolean deposit) {
        this.guild = guild;
        this.player = player;
        this.amount = amount;
        this.deposit = deposit;
    }

    public Guild getGuild() {
        return guild;
    }

    public Player getPlayer() {
        return player;
    }

    public long getAmount() {
        return amount;
    }

    public boolean isDeposit() {
        return deposit;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
