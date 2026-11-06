package com.etheria.gremios.api.event;

import com.etheria.gremios.model.Guild;
import com.etheria.gremios.model.Territory;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Un gremio conquista un territorio por asedio (oldOwner puede ser null). */
public class TerritoryCaptureEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Territory territory;
    private final Guild newOwner;
    private final Guild oldOwner;

    public TerritoryCaptureEvent(Territory territory, Guild newOwner, Guild oldOwner) {
        this.territory = territory;
        this.newOwner = newOwner;
        this.oldOwner = oldOwner;
    }

    public Territory getTerritory() {
        return territory;
    }

    public Guild getNewOwner() {
        return newOwner;
    }

    public Guild getOldOwner() {
        return oldOwner;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
