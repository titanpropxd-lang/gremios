package com.etheria.gremios.command;

import com.etheria.gremios.GremiosPlugin;
import org.bukkit.command.CommandSender;

/** /gremioadmin npc estado|reparar */
public class NpcCommand {
    private final GremiosPlugin plugin;

    public NpcCommand(GremiosPlugin plugin) {
        this.plugin = plugin;
    }

    public void run(CommandSender s, String[] a) {
        String sub = a.length > 1 ? a[1].toLowerCase() : "estado";
        switch (sub) {
            case "reparar" -> {
                plugin.npcs.repairAll();
                s.sendMessage("§aNPC recreados en sitios seguros. Usa §f/gremioadmin npc estado§a para comprobar.");
            }
            default -> {
                s.sendMessage("§6NPC cargados: §f" + plugin.npcs.countLoaded());
                for (String l : plugin.npcs.report()) s.sendMessage(l);
                s.sendMessage("§8Si alguno sale NO EXISTE: §f/gremioadmin npc reparar");
            }
        }
    }
}
