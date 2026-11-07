package com.etheria.gremios.api;

import com.etheria.gremios.GremiosPlugin;
import com.etheria.gremios.model.Guild;
import com.etheria.gremios.model.Raid;
import com.etheria.gremios.model.Territory;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * API pública de GremiosGUI. Se obtiene así desde otro plugin:
 * <pre>
 * RegisteredServiceProvider&lt;GremiosAPI&gt; rsp = Bukkit.getServicesManager().getRegistration(GremiosAPI.class);
 * GremiosAPI api = rsp.getProvider();
 * </pre>
 * Todo debe llamarse desde el hilo principal. Los eventos están en com.etheria.gremios.api.event.
 */
public class GremiosAPI {
    private final GremiosPlugin plugin;

    public GremiosAPI(GremiosPlugin plugin) {
        this.plugin = plugin;
    }

    public static GremiosAPI get() {
        var rsp = Bukkit.getServicesManager().getRegistration(GremiosAPI.class);
        return rsp == null ? null : rsp.getProvider();
    }

    public Guild getGuild(UUID player) {
        return plugin.guilds.of(player);
    }

    public Guild getGuild(Player player) {
        return plugin.guilds.of(player);
    }

    public Guild getGuildByName(String name) {
        return plugin.guilds.byName(name);
    }

    public Collection<Guild> getGuilds() {
        return Collections.unmodifiableCollection(plugin.guilds.all());
    }

    public String getRank(Player p) {
        var r = plugin.guilds.rankOf(p);
        return r == null ? null : r.name();
    }

    public boolean sameGuildOrAllied(Player a, Player b) {
        Guild x = plugin.guilds.of(a), y = plugin.guilds.of(b);
        return x != null && y != null && (x == y || plugin.guilds.isAllied(x, y));
    }

    public boolean areAllies(Guild a, Guild b) {
        return plugin.guilds.isAllied(a, b);
    }

    public Collection<Territory> getTerritories() {
        return Collections.unmodifiableCollection(plugin.territories.all());
    }

    public Territory getTerritory(String id) {
        return plugin.territories.get(id);
    }

    public Raid getRaid(Guild g) {
        return plugin.raids.of(g);
    }

    public boolean isInActiveRaid(Player p) {
        return plugin.raids.sideOf(p) != null;
    }

    public List<Raid> getRaids() {
        return new ArrayList<>(plugin.raids.all());
    }

    public long getBank(Guild g) {
        return g.bank;
    }
}
