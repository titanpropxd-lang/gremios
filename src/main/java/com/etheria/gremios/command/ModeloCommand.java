package com.etheria.gremios.command;

import com.etheria.gremios.GremiosPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Locale;

/** /gremioadmin modelo probar | giro <grados> | refrescar */
public class ModeloCommand {
    private final GremiosPlugin plugin;

    public ModeloCommand(GremiosPlugin plugin) {
        this.plugin = plugin;
    }

    public void run(CommandSender s, String[] a) {
        String act = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "";
        switch (act) {
            case "probar" -> {
                if (!(s instanceof Player p)) {
                    s.sendMessage("§cSolo jugadores.");
                    return;
                }
                Location l = p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(3));
                l.setYaw(p.getLocation().getYaw() + 180f); // mirando hacia ti
                l.setPitch(0f);
                Entity d = plugin.models.spawnTest(l, 7434);
                Bukkit.getScheduler().runTaskLater(plugin, d::remove, 20L * 60);
                s.sendMessage("§6Modelo de prueba (Capitán) delante de ti durante 60 s.");
                s.sendMessage("§7Si te mira de FRENTE (ves su cara): todo bien. Si le ves la ESPALDA: §e/gremioadmin modelo giro 180");
                s.sendMessage("§7Si lo ves de lado: prueba §e/gremioadmin modelo giro 90 §7o §e270§7. Giro actual: §f" + plugin.models.giro());
                s.sendMessage("§7Si NO ves nada: el resourcepack no está aplicado o es una versión antigua (vuelve a descargarlo).");
            }
            case "giro" -> {
                if (a.length < 3) {
                    s.sendMessage("§6/gremioadmin modelo giro <0|90|180|270> §7(actual: " + plugin.models.giro() + ")");
                    return;
                }
                try {
                    plugin.getConfig().set("modelos.giro", Double.parseDouble(a[2]));
                } catch (NumberFormatException ex) {
                    s.sendMessage("§cNúmero inválido.");
                    return;
                }
                plugin.saveConfig();
                plugin.models.tick();
                plugin.banners.refreshAll();
                s.sendMessage("§aGiro aplicado a NPC y banderas: " + plugin.models.giro());
            }
            case "refrescar" -> {
                plugin.models.tick();
                plugin.banners.refreshAll();
                s.sendMessage("§aModelos refrescados.");
            }
            default -> s.sendMessage("§6/gremioadmin modelo §eprobar | giro <grados> | refrescar");
        }
    }
}
