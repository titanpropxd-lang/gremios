package com.bossforge.listeners;

import com.bossforge.BossForgeContext;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class ChatInputListener implements Listener {
    // NOTA: AsyncPlayerChatEvent esta deprecado en Spigot en favor de un
    // sistema de chat mas nuevo (io.papermc.paper.event.player.AsyncChatEvent
    // en Paper), pero sigue funcionando en 1.21.x y es la opcion mas portable
    // entre Spigot y Paper sin depender de la API especifica de Paper.
    private final BossForgeContext ctx;

    public ChatInputListener(BossForgeContext ctx) {
        this.ctx = ctx;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!ctx.entradaTexto.tieneSolicitudPendiente(event.getPlayer().getUniqueId())) return;

        event.setCancelled(true);
        String mensaje = event.getMessage();
        // El callback puede tocar la API de Bukkit (abrir inventarios, etc.),
        // y este evento corre async — hay que volver al hilo principal.
        Bukkit.getScheduler().runTask(ctx.plugin, () ->
            ctx.entradaTexto.resolver(event.getPlayer().getUniqueId(), mensaje));
    }
}
