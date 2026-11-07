package com.bossforge.gui;

import com.bossforge.BossForgeContext;
import com.bossforge.model.BossTemplate;
import com.bossforge.util.MenuItemBuilder;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.List;

public class BossEditorGuiHolder implements BossForgeGuiHolder {
    private static final List<EntityType> TIPOS_DISPONIBLES = List.of(
        EntityType.ZOMBIE, EntityType.HUSK, EntityType.DROWNED, EntityType.SKELETON, EntityType.STRAY,
        EntityType.WITHER_SKELETON, EntityType.SPIDER, EntityType.CAVE_SPIDER, EntityType.CREEPER,
        EntityType.ENDERMAN, EntityType.BLAZE, EntityType.PIGLIN_BRUTE, EntityType.VINDICATOR,
        EntityType.EVOKER, EntityType.RAVAGER, EntityType.PILLAGER, EntityType.MAGMA_CUBE,
        EntityType.SLIME, EntityType.HOGLIN, EntityType.ZOGLIN, EntityType.SHULKER,
        EntityType.GUARDIAN, EntityType.ELDER_GUARDIAN, EntityType.PHANTOM, EntityType.WITHER,
        EntityType.WARDEN, EntityType.ENDER_DRAGON
    );

    private final BossForgeContext ctx;
    private final Player jugador;
    private final BossTemplate boss;
    private final Inventory inventario;

    public BossEditorGuiHolder(BossForgeContext ctx, Player jugador, BossTemplate boss) {
        this.ctx = ctx;
        this.jugador = jugador;
        this.boss = boss;
        this.inventario = Bukkit.createInventory(this, 54, ChatColor.DARK_RED + "Jefe: " + ChatColor.RESET + boss.getNombre());
        construir();
    }

    @Override
    public Inventory getInventory() { return inventario; }

    private void construir() {
        inventario.clear();
        inventario.setItem(4, MenuItemBuilder.de(Material.SPAWNER)
            .nombre(ChatColor.RED + boss.getNombre())
            .lore(
                ChatColor.GRAY + "Base: " + ChatColor.WHITE + boss.getTipoBase(),
                ChatColor.GRAY + "Vida: " + ChatColor.WHITE + (int) boss.getVidaMaxima(),
                ChatColor.GRAY + "Dano: " + ChatColor.WHITE + (int) boss.getDanoAtaque(),
                ChatColor.GRAY + "Habilidades: " + ChatColor.WHITE + boss.getHabilidades().size(),
                ChatColor.GRAY + "Fases: " + ChatColor.WHITE + boss.getNumeroFases()
            ).build());

        inventario.setItem(19, MenuItemBuilder.de(Material.NAME_TAG)
            .nombre(ChatColor.YELLOW + "Renombrar")
            .lore(ChatColor.GRAY + "Escribe el nuevo nombre en el chat.").build());

        inventario.setItem(20, MenuItemBuilder.de(Material.ZOMBIE_SPAWN_EGG)
            .nombre(ChatColor.YELLOW + "Tipo base: " + ChatColor.WHITE + boss.getTipoBase())
            .lore(ChatColor.GRAY + "Clic para cambiar al siguiente tipo.").build());

        inventario.setItem(21, MenuItemBuilder.de(Material.RED_DYE)
            .nombre(ChatColor.YELLOW + "Vida maxima: " + ChatColor.WHITE + (int) boss.getVidaMaxima())
            .lore(ChatColor.GRAY + "Escribe el nuevo valor en el chat.").build());

        inventario.setItem(22, MenuItemBuilder.de(Material.IRON_SWORD)
            .nombre(ChatColor.YELLOW + "Dano de ataque: " + ChatColor.WHITE + (int) boss.getDanoAtaque())
            .lore(ChatColor.GRAY + "Escribe el nuevo valor en el chat.").build());

        inventario.setItem(31, MenuItemBuilder.de(Material.BLAZE_POWDER)
            .nombre(ChatColor.GOLD + "Habilidades (" + boss.getHabilidades().size() + ")")
            .lore(ChatColor.GRAY + "Elige que habilidades tiene este jefe.").build());

        inventario.setItem(32, MenuItemBuilder.de(Material.TOTEM_OF_UNDYING)
            .nombre(ChatColor.GOLD + "Fases: " + ChatColor.WHITE + boss.getNumeroFases())
            .lore(ChatColor.GRAY + "1 = una sola fase.",
                  ChatColor.GRAY + "2 = tiene una segunda fase al bajar de vida.",
                  ChatColor.YELLOW + "Clic para cambiar.").build());

        if (boss.getNumeroFases() >= 2) {
            inventario.setItem(33, MenuItemBuilder.de(Material.EXPERIENCE_BOTTLE)
                .nombre(ChatColor.GOLD + "Umbral fase 2: " + ChatColor.WHITE + (int) boss.getPorcentajeVidaCambioFase() + "%")
                .lore(ChatColor.GRAY + "Vida a la que pasa a la fase 2.",
                      ChatColor.GRAY + "Escribe el nuevo valor en el chat.").build());
        }

        inventario.setItem(49, MenuItemBuilder.de(Material.ARROW).nombre(ChatColor.YELLOW + "« Volver").build());
    }

    @Override
    public void manejarClic(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() != inventario) return;

        switch (event.getSlot()) {
            case 19 -> pedirTexto("nombre del jefe", texto -> boss.setNombre(texto.trim()));
            case 20 -> {
                int indice = TIPOS_DISPONIBLES.indexOf(boss.getTipoBase());
                boss.setTipoBase(TIPOS_DISPONIBLES.get((indice + 1) % TIPOS_DISPONIBLES.size()));
                guardarYRefrescar();
            }
            case 21 -> pedirTexto("vida maxima (numero)", texto -> parsearYAsignar(texto, boss::setVidaMaxima));
            case 22 -> pedirTexto("dano de ataque (numero)", texto -> parsearYAsignar(texto, boss::setDanoAtaque));
            case 31 -> new HabilidadSeleccionGuiHolder(ctx, jugador, boss).abrir();
            case 32 -> {
                boss.setNumeroFases(boss.getNumeroFases() >= 2 ? 1 : 2);
                guardarYRefrescar();
            }
            case 33 -> pedirTexto("umbral de vida para la fase 2 (0-100)", texto -> parsearYAsignar(texto, boss::setPorcentajeVidaCambioFase));
            case 49 -> new BossCatalogoGuiHolder(ctx, jugador, null).abrir();
            default -> { }
        }
    }

    private void parsearYAsignar(String texto, java.util.function.DoubleConsumer asignador) {
        try {
            double valor = Double.parseDouble(texto.trim());
            asignador.accept(Math.max(1, valor));
            jugador.sendMessage(ChatColor.GREEN + "Valor actualizado.");
        } catch (NumberFormatException e) {
            jugador.sendMessage(ChatColor.RED + "Eso no es un numero valido, no se cambio nada.");
        }
    }

    private void pedirTexto(String descripcion, java.util.function.Consumer<String> alRecibir) {
        jugador.closeInventory();
        jugador.sendMessage(ChatColor.YELLOW + "Escribe " + descripcion + " en el chat (o 'cancelar'):");
        ctx.entradaTexto.solicitar(jugador.getUniqueId(), descripcion, texto -> {
            if (!"cancelar".equalsIgnoreCase(texto.trim())) {
                alRecibir.accept(texto);
                ctx.bossTemplateManager.actualizar(boss);
            }
            Bukkit.getScheduler().runTask(ctx.plugin, () -> new BossEditorGuiHolder(ctx, jugador, boss).abrir());
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
