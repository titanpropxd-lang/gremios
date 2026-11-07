package com.bossforge.gui;

import com.bossforge.BossForgeContext;
import com.bossforge.model.Obelisco;
import com.bossforge.util.MenuItemBuilder;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;

public class ObeliscoConfigGuiHolder implements BossForgeGuiHolder {
    private final BossForgeContext ctx;
    private final Player jugador;
    private final Obelisco obelisco;
    private final Inventory inventario;

    public ObeliscoConfigGuiHolder(BossForgeContext ctx, Player jugador, Obelisco obelisco) {
        this.ctx = ctx;
        this.jugador = jugador;
        this.obelisco = obelisco;
        this.inventario = Bukkit.createInventory(this, 54, ChatColor.DARK_PURPLE + "Obelisco: " + ChatColor.RESET + obelisco.getNombre());
        construir();
    }

    @Override
    public Inventory getInventory() { return inventario; }

    private void construir() {
        inventario.clear();

        List<String> resumen = new ArrayList<>();
        resumen.add(ChatColor.GRAY + "Estado: " + ChatColor.WHITE + obelisco.getEstado());
        resumen.add(ChatColor.GRAY + "Activo: " + (obelisco.isActivo() ? ChatColor.GREEN + "si" : ChatColor.RED + "no"));
        resumen.add(ChatColor.GRAY + "Ubicacion: " + (obelisco.getUbicacion() != null ? ChatColor.WHITE + "vinculada" : ChatColor.RED + "sin vincular"));
        resumen.add(ChatColor.GRAY + "Arena: " + (obelisco.tieneArenaCompleta() ? ChatColor.WHITE + "definida" : ChatColor.RED + "incompleta"));
        resumen.add(ChatColor.GRAY + "Jefes: " + (obelisco.getOpciones().isEmpty() ? ChatColor.RED + "sin asignar" : ChatColor.WHITE + String.valueOf(obelisco.getOpciones().size())));
        resumen.add(ChatColor.GRAY + "Aparicion jugadores: " + (obelisco.getJugadorSpawn() != null ? ChatColor.WHITE + "definida" : ChatColor.RED + "sin definir"));
        inventario.setItem(4, MenuItemBuilder.de(Material.BEACON).nombre(ChatColor.AQUA + obelisco.getNombre()).lore(resumen).build());

        inventario.setItem(10, MenuItemBuilder.de(Material.NAME_TAG)
            .nombre(ChatColor.YELLOW + "Renombrar")
            .lore(ChatColor.GRAY + "Escribe el nuevo nombre en el chat.").build());

        inventario.setItem(11, MenuItemBuilder.de(Material.CHEST)
            .nombre(ChatColor.YELLOW + "Vincular ubicacion")
            .lore(ChatColor.GRAY + "Clic y luego haz clic derecho", ChatColor.GRAY + "en el bloque del obelisco en el mundo.").build());

        inventario.setItem(12, MenuItemBuilder.de(Material.RED_CONCRETE)
            .nombre(ChatColor.YELLOW + "Definir arena: esquina 1")
            .lore(ChatColor.GRAY + "Usa tu posicion actual.").build());

        inventario.setItem(13, MenuItemBuilder.de(Material.BLUE_CONCRETE)
            .nombre(ChatColor.YELLOW + "Definir arena: esquina 2")
            .lore(ChatColor.GRAY + "Usa tu posicion actual.").build());

        inventario.setItem(14, MenuItemBuilder.de(Material.DRAGON_EGG)
            .nombre(ChatColor.YELLOW + "Definir aparicion del jefe")
            .lore(ChatColor.GRAY + "Usa tu posicion actual.",
                  ChatColor.GRAY + "Si no se define, se usa la ubicacion del obelisco.").build());

        inventario.setItem(15, MenuItemBuilder.de(materialParaColor())
            .nombre(ChatColor.YELLOW + "Color del faro")
            .lore(ChatColor.GRAY + "El haz de luz del obelisco (estilo ARK).",
                  ChatColor.YELLOW + "Clic para elegir color.").build());

        inventario.setItem(16, MenuItemBuilder.de(obelisco.isFaroActivo() ? Material.BEACON : Material.GRAY_STAINED_GLASS)
            .nombre(obelisco.isFaroActivo() ? ChatColor.GREEN + "Faro encendido (clic para apagar)" : ChatColor.RED + "Faro apagado (clic para encender)")
            .build());

        inventario.setItem(22, MenuItemBuilder.de(Material.RESPAWN_ANCHOR)
            .nombre(ChatColor.YELLOW + "Definir aparicion del jugador")
            .lore(ChatColor.GRAY + "Usa tu posicion actual.",
                  ChatColor.GRAY + "Aqui se teletransportan los jugadores",
                  ChatColor.GRAY + "al invocarse el jefe final.").build());

        inventario.setItem(23, MenuItemBuilder.de(Material.SLIME_BALL)
            .nombre(ChatColor.YELLOW + "Radio de aparicion: " + ChatColor.WHITE + formatear(obelisco.getJugadorSpawnRadio()))
            .lore(ChatColor.GRAY + "Area alrededor del punto donde",
                  ChatColor.GRAY + "se reparten los jugadores al azar.",
                  ChatColor.GRAY + "Escribe el nuevo valor en el chat.").build());

        inventario.setItem(21, MenuItemBuilder.de(Material.ZOMBIE_HEAD)
            .nombre(ChatColor.GOLD + "Jefes del obelisco (" + obelisco.getOpciones().size() + ")")
            .lore(ChatColor.GRAY + "Los jefes que los jugadores pueden elegir.",
                  ChatColor.GRAY + "Cada jefe tiene SUS requisitos y SU loot.",
                  ChatColor.GRAY + "Anade jefes de MythicMobs o de tu catalogo.").build());

        inventario.setItem(24, MenuItemBuilder.de(Material.ENDER_EYE)
            .nombre(ChatColor.YELLOW + "Radio de convocatoria: " + ChatColor.WHITE + formatear(obelisco.getRadioConvocatoria()))
            .lore(ChatColor.GRAY + "Los jugadores a esta distancia del obelisco",
                  ChatColor.GRAY + "son llevados a la arena al invocar.",
                  ChatColor.GRAY + "0 = solo quien activa el obelisco.",
                  ChatColor.GRAY + "Escribe el nuevo valor en el chat.").build());

        inventario.setItem(31, MenuItemBuilder.de(obelisco.isActivo() ? Material.LIME_DYE : Material.GRAY_DYE)
            .nombre(obelisco.isActivo() ? ChatColor.GREEN + "Activado (clic para desactivar)" : ChatColor.RED + "Desactivado (clic para activar)")
            .build());

        inventario.setItem(40, MenuItemBuilder.de(Material.BARRIER)
            .nombre(ChatColor.RED + "Eliminar obelisco")
            .lore(ChatColor.GRAY + "Shift + clic para confirmar.").build());

        inventario.setItem(49, MenuItemBuilder.de(Material.ARROW).nombre(ChatColor.YELLOW + "« Volver").build());
    }

    private Material materialParaColor() {
        org.bukkit.Color c = obelisco.getColorFaro();
        if (c.getRed() > 200 && c.getGreen() > 200 && c.getBlue() > 200) return Material.WHITE_DYE;
        if (c.getRed() < 50 && c.getGreen() < 50 && c.getBlue() < 50) return Material.BLACK_DYE;
        if (c.getRed() > 150 && c.getGreen() < 100 && c.getBlue() < 100) return Material.RED_DYE;
        if (c.getGreen() > 150 && c.getRed() < 100 && c.getBlue() < 100) return Material.LIME_DYE;
        if (c.getBlue() > 150 && c.getRed() < 100 && c.getGreen() < 100) return Material.BLUE_DYE;
        if (c.getRed() > 150 && c.getGreen() > 150 && c.getBlue() < 100) return Material.YELLOW_DYE;
        if (c.getGreen() > 150 && c.getBlue() > 150 && c.getRed() < 100) return Material.CYAN_DYE;
        if (c.getRed() > 150 && c.getBlue() > 150 && c.getGreen() < 100) return Material.MAGENTA_DYE;
        return Material.LIGHT_GRAY_DYE;
    }

    private String formatear(double valor) {
        return valor == Math.floor(valor) ? String.valueOf((long) valor) : String.valueOf(valor);
    }

    @Override
    public void manejarClic(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() != inventario) return;

        switch (event.getSlot()) {
            case 10 -> pedirRenombrar();
            case 11 -> iniciarVinculacion();
            case 12 -> { obelisco.setArenaPos1(jugador.getLocation()); guardarYRefrescar("Esquina 1 de la arena definida."); }
            case 13 -> { obelisco.setArenaPos2(jugador.getLocation()); guardarYRefrescar("Esquina 2 de la arena definida."); }
            case 14 -> { obelisco.setArenaSpawn(jugador.getLocation()); guardarYRefrescar("Punto de aparicion del jefe definido."); }
            case 15 -> new ObeliskColorGuiHolder(ctx, jugador, obelisco).abrir();
            case 16 -> { obelisco.setFaroActivo(!obelisco.isFaroActivo()); guardarYRefrescar(null); }
            case 22 -> { obelisco.setJugadorSpawn(jugador.getLocation()); guardarYRefrescar("Punto de aparicion del jugador definido."); }
            case 23 -> pedirNumero("radio de aparicion de jugadores en bloques", obelisco::setJugadorSpawnRadio);
            case 21 -> new ObeliscoJefesGuiHolder(ctx, jugador, obelisco).abrir();
            case 24 -> pedirNumero("radio de convocatoria en bloques (0 = solo quien activa)", obelisco::setRadioConvocatoria);
            case 31 -> { obelisco.setActivo(!obelisco.isActivo()); guardarYRefrescar(null); }
            case 40 -> {
                if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
                    ctx.obeliskManager.eliminar(obelisco.getId());
                    jugador.sendMessage(ChatColor.RED + "Obelisco eliminado.");
                    new MenuPrincipalGuiHolder(ctx, jugador, 0).abrir();
                }
            }
            case 49 -> new MenuPrincipalGuiHolder(ctx, jugador, 0).abrir();
            default -> { }
        }
    }

    private void pedirRenombrar() {
        jugador.closeInventory();
        jugador.sendMessage(ChatColor.YELLOW + "Escribe el nuevo nombre del obelisco en el chat (o 'cancelar'):");
        ctx.entradaTexto.solicitar(jugador.getUniqueId(), "nombre de obelisco", texto -> {
            if (!"cancelar".equalsIgnoreCase(texto.trim())) {
                obelisco.setNombre(texto.trim());
                ctx.obeliskManager.actualizar(obelisco);
                jugador.sendMessage(ChatColor.GREEN + "Nombre actualizado a: " + texto.trim());
            }
            Bukkit.getScheduler().runTask(ctx.plugin, () -> new ObeliscoConfigGuiHolder(ctx, jugador, obelisco).abrir());
        });
    }

    private void pedirNumero(String descripcion, java.util.function.DoubleConsumer asignador) {
        jugador.closeInventory();
        jugador.sendMessage(ChatColor.YELLOW + "Escribe el " + descripcion + " (o 'cancelar'):");
        ctx.entradaTexto.solicitar(jugador.getUniqueId(), descripcion, texto -> {
            if (!"cancelar".equalsIgnoreCase(texto.trim())) {
                try {
                    asignador.accept(Double.parseDouble(texto.trim()));
                    ctx.obeliskManager.actualizar(obelisco);
                    jugador.sendMessage(ChatColor.GREEN + "Valor actualizado.");
                } catch (NumberFormatException e) {
                    jugador.sendMessage(ChatColor.RED + "Eso no es un numero valido, no se cambio nada.");
                }
            }
            Bukkit.getScheduler().runTask(ctx.plugin, () -> new ObeliscoConfigGuiHolder(ctx, jugador, obelisco).abrir());
        });
    }

    private void iniciarVinculacion() {
        jugador.closeInventory();
        ctx.vinculacionPendiente.marcarEsperando(jugador.getUniqueId(), obelisco.getId());
        jugador.sendMessage(ChatColor.YELLOW + "Haz clic derecho en el bloque que sera el obelisco (tienes 30s).");
        Bukkit.getScheduler().runTaskLater(ctx.plugin, () -> ctx.vinculacionPendiente.consumir(jugador.getUniqueId()), 600L);
    }

    private void guardarYRefrescar(String mensaje) {
        ctx.obeliskManager.actualizar(obelisco);
        if (mensaje != null) jugador.sendMessage(ChatColor.GREEN + mensaje);
        construir();
        jugador.openInventory(inventario);
    }

    public void abrir() {
        jugador.openInventory(inventario);
    }
}
