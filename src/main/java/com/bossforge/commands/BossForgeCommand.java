package com.bossforge.commands;

import com.bossforge.BossForgeContext;
import com.bossforge.gui.MenuPrincipalGuiHolder;
import com.bossforge.model.Obelisco;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;

public class BossForgeCommand implements CommandExecutor {
    private final BossForgeContext ctx;

    public BossForgeCommand(BossForgeContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player jugador)) {
            sender.sendMessage("Este comando solo puede usarse en el juego.");
            return true;
        }

        if (args.length == 0 || "menu".equalsIgnoreCase(args[0])) {
            new MenuPrincipalGuiHolder(ctx, jugador, 0).abrir();
            return true;
        }

        if ("crear".equalsIgnoreCase(args[0])) {
            String nombre = args.length > 1
                ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                : "Obelisco " + (ctx.obeliskManager.todos().size() + 1);
            Obelisco nuevo = ctx.obeliskManager.crear(nombre);
            jugador.sendMessage(ChatColor.GREEN + "Obelisco '" + nuevo.getNombre() + "' creado. Configuralo con /bossforge menu");
            return true;
        }

        if ("reload".equalsIgnoreCase(args[0])) {
            ctx.plugin.reloadConfig();
            jugador.sendMessage(ChatColor.GREEN + "Configuracion recargada.");
            return true;
        }

        jugador.sendMessage(ChatColor.YELLOW + "Uso: /bossforge <menu|crear [nombre]|reload>");
        return true;
    }
}
