package com.bossforge.listeners;

import com.bossforge.BossForgeContext;
import com.bossforge.gui.DepositoGuiHolder;
import com.bossforge.gui.SelectorJefeGuiHolder;
import com.bossforge.model.OpcionJefe;
import java.util.List;
import com.bossforge.model.Obelisco;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class ObeliskInteractListener implements Listener {
    private final BossForgeContext ctx;

    public ObeliskInteractListener(BossForgeContext ctx) {
        this.ctx = ctx;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block bloque = event.getClickedBlock();
        if (bloque == null) return;
        Player jugador = event.getPlayer();

        String obeliscoIdPendiente = ctx.vinculacionPendiente.consumir(jugador.getUniqueId());
        if (obeliscoIdPendiente != null) {
            event.setCancelled(true);
            ctx.obeliskManager.obtener(obeliscoIdPendiente).ifPresentOrElse(obelisco -> {
                obelisco.setUbicacion(bloque.getLocation());
                ctx.obeliskManager.actualizar(obelisco);
                jugador.sendMessage(ChatColor.GREEN + "Obelisco '" + obelisco.getNombre() + "' vinculado a este bloque.");
            }, () -> jugador.sendMessage(ChatColor.RED + "Ese obelisco ya no existe."));
            return;
        }

        Obelisco obelisco = ctx.obeliskManager.obtenerPorUbicacion(bloque.getLocation()).orElse(null);
        if (obelisco == null) return;

        event.setCancelled(true);
        if (!obelisco.isActivo()) {
            jugador.sendMessage(ChatColor.RED + "Este obelisco esta desactivado.");
            return;
        }
        String razonBloqueo = ctx.obeliskManager.razonPorLaQueNoPuedeActivarse(obelisco);
        if (razonBloqueo != null && !razonBloqueo.equals("Este obelisco todavia no esta completamente configurado.")) {
            // Igual dejamos abrir el deposito aunque este en cooldown/combate, solo para que
            // vean el mensaje claro en vez de que "no pase nada" al hacer clic.
            jugador.sendMessage(ChatColor.RED + razonBloqueo);
            return;
        }
        if (!obelisco.listoParaActivarse()) {
            jugador.sendMessage(ChatColor.RED + "Este obelisco todavia no esta configurado del todo.");
            return;
        }

        List<OpcionJefe> disponibles = obelisco.getOpciones().stream().filter(OpcionJefe::estaCompleta).toList();
        if (disponibles.size() == 1) {
            // Un solo jefe: no hace falta elegir, va directo al deposito.
            String problema = ctx.obeliskManager.razonPorLaQueLaOpcionNoSePuede(disponibles.get(0));
            if (problema != null) { jugador.sendMessage(ChatColor.RED + problema); return; }
            new DepositoGuiHolder(ctx, jugador, obelisco, disponibles.get(0)).abrir();
        } else {
            new SelectorJefeGuiHolder(ctx, jugador, obelisco).abrir();
        }
    }
}
