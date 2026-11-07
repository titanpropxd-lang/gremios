package com.bossforge.gui;

import com.bossforge.BossForgeContext;
import com.bossforge.habilidades.HabilidadCatalogoEntry;
import com.bossforge.habilidades.ParametroDefinicion;
import com.bossforge.model.BossTemplate;
import com.bossforge.model.HabilidadBossInstancia;
import com.bossforge.model.TipoDisparoHabilidad;
import com.bossforge.util.MenuItemBuilder;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.List;

public class HabilidadConfigGuiHolder implements BossForgeGuiHolder {
    private final BossForgeContext ctx;
    private final Player jugador;
    private final BossTemplate boss;
    private final HabilidadCatalogoEntry catalogoEntry;
    private final HabilidadBossInstancia instancia;
    private final Inventory inventario;

    public HabilidadConfigGuiHolder(BossForgeContext ctx, Player jugador, BossTemplate boss,
                                     HabilidadCatalogoEntry catalogoEntry, HabilidadBossInstancia instancia) {
        this.ctx = ctx;
        this.jugador = jugador;
        this.boss = boss;
        this.catalogoEntry = catalogoEntry;
        this.instancia = instancia;
        this.inventario = Bukkit.createInventory(this, 54, ChatColor.DARK_RED + "Ajustes: " + ChatColor.RESET + catalogoEntry.nombre());
        construir();
    }

    @Override
    public Inventory getInventory() { return inventario; }

    private void construir() {
        inventario.clear();

        inventario.setItem(10, MenuItemBuilder.de(Material.CLOCK)
            .nombre(ChatColor.YELLOW + "Tipo de disparo: " + ChatColor.WHITE + instancia.getTipoDisparo())
            .lore(ChatColor.GRAY + "PERIODICA: se repite cada X segundos.",
                  ChatColor.GRAY + "AL_BAJAR_DE_VIDA: una vez, al bajar del %.",
                  ChatColor.YELLOW + "Clic para cambiar.").build());

        if (instancia.getTipoDisparo() == TipoDisparoHabilidad.PERIODICA) {
            inventario.setItem(11, MenuItemBuilder.de(Material.REPEATER)
                .nombre(ChatColor.YELLOW + "Cooldown: " + ChatColor.WHITE + instancia.getCooldownSegundos() + "s")
                .lore(ChatColor.GRAY + "Escribe el nuevo valor en el chat.").build());
        } else {
            inventario.setItem(11, MenuItemBuilder.de(Material.REDSTONE)
                .nombre(ChatColor.YELLOW + "Umbral de vida: " + ChatColor.WHITE + instancia.getPorcentajeVidaUmbral() + "%")
                .lore(ChatColor.GRAY + "Escribe el nuevo valor en el chat.").build());
        }

        inventario.setItem(12, MenuItemBuilder.de(Material.NETHER_STAR)
            .nombre(ChatColor.YELLOW + "Fase: " + ChatColor.WHITE + etiquetaFase(instancia.getFase()))
            .lore(ChatColor.GRAY + "En que fase del jefe se puede disparar.",
                  ChatColor.GRAY + "Solo importa si el jefe tiene 2 fases.",
                  ChatColor.YELLOW + "Clic para cambiar.").build());

        List<ParametroDefinicion> parametros = catalogoEntry.parametros();
        int slotBase = 19;
        for (int i = 0; i < parametros.size() && i < 9; i++) {
            ParametroDefinicion def = parametros.get(i);
            double valorActual = instancia.parametro(def.clave(), def.valorPorDefecto());
            inventario.setItem(slotBase + i, MenuItemBuilder.de(Material.PAPER)
                .nombre(ChatColor.AQUA + def.etiqueta() + ": " + ChatColor.WHITE + formatear(valorActual))
                .lore(ChatColor.GRAY + "Rango sugerido: " + def.min() + " - " + def.max(),
                      ChatColor.GRAY + "Escribe el nuevo valor en el chat.").build());
        }

        inventario.setItem(49, MenuItemBuilder.de(Material.ARROW).nombre(ChatColor.YELLOW + "« Volver").build());
    }

    private String formatear(double valor) {
        return valor == Math.floor(valor) ? String.valueOf((long) valor) : String.valueOf(valor);
    }

    private String etiquetaFase(int fase) {
        return switch (fase) {
            case 1 -> "Solo fase 1";
            case 2 -> "Solo fase 2";
            default -> "Ambas fases";
        };
    }

    @Override
    public void manejarClic(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() != inventario) return;
        int slot = event.getSlot();

        if (slot == 49) { new HabilidadSeleccionGuiHolder(ctx, jugador, boss).abrir(); return; }
        if (slot == 10) {
            instancia.setTipoDisparo(instancia.getTipoDisparo() == TipoDisparoHabilidad.PERIODICA
                ? TipoDisparoHabilidad.AL_BAJAR_DE_VIDA : TipoDisparoHabilidad.PERIODICA);
            guardarYRefrescar();
            return;
        }
        if (slot == 12) {
            instancia.setFase((instancia.getFase() + 1) % 3);
            guardarYRefrescar();
            return;
        }
        if (slot == 11) {
            if (instancia.getTipoDisparo() == TipoDisparoHabilidad.PERIODICA) {
                pedirNumero("cooldown en segundos", instancia::setCooldownSegundos);
            } else {
                pedirNumero("umbral de vida (0-100)", instancia::setPorcentajeVidaUmbral);
            }
            return;
        }

        List<ParametroDefinicion> parametros = catalogoEntry.parametros();
        int indice = slot - 19;
        if (indice >= 0 && indice < parametros.size()) {
            ParametroDefinicion def = parametros.get(indice);
            pedirNumero(def.etiqueta(), valor -> instancia.getParametros().put(def.clave(), valor));
        }
    }

    private void pedirNumero(String descripcion, java.util.function.DoubleConsumer asignador) {
        jugador.closeInventory();
        jugador.sendMessage(ChatColor.YELLOW + "Escribe el valor de '" + descripcion + "' en el chat (o 'cancelar'):");
        ctx.entradaTexto.solicitar(jugador.getUniqueId(), descripcion, texto -> {
            if (!"cancelar".equalsIgnoreCase(texto.trim())) {
                try {
                    asignador.accept(Double.parseDouble(texto.trim()));
                    ctx.bossTemplateManager.actualizar(boss);
                    jugador.sendMessage(ChatColor.GREEN + "Valor actualizado.");
                } catch (NumberFormatException e) {
                    jugador.sendMessage(ChatColor.RED + "Eso no es un numero valido, no se cambio nada.");
                }
            }
            Bukkit.getScheduler().runTask(ctx.plugin, () -> new HabilidadConfigGuiHolder(ctx, jugador, boss, catalogoEntry, instancia).abrir());
        });
    }

    private void guardarYRefrescar() {
        ctx.bossTemplateManager.actualizar(boss);
        construir();
        jugador.openInventory(inventario);
    }

    public void abrir() {
        jugador.openInventory(inventario);
    }
}
