package com.bossforge.gui;

import com.bossforge.BossForgeContext;
import com.bossforge.integration.MythicHook;
import com.bossforge.model.BossTemplate;
import com.bossforge.model.Obelisco;
import com.bossforge.model.OpcionJefe;
import com.bossforge.util.MenuItemBuilder;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Menu con los mobs de MythicMobs (paginado y con buscador). Clic = anade ese mob como jefe
 * del obelisco y abre su pantalla para configurar sus requisitos y su loot.
 */
public class MythicMobSeleccionGuiHolder implements BossForgeGuiHolder {
    private static final int POR_PAGINA = 45;

    private final BossForgeContext ctx;
    private final Player jugador;
    private final Obelisco obelisco;
    private final int pagina;
    /** Texto de busqueda activo (null = sin filtro). */
    private final String filtro;
    private final List<MythicHook.MobInfo> mobs;
    private final Inventory inventario;

    public MythicMobSeleccionGuiHolder(BossForgeContext ctx, Player jugador, Obelisco obelisco, int pagina) {
        this(ctx, jugador, obelisco, pagina, null);
    }

    public MythicMobSeleccionGuiHolder(BossForgeContext ctx, Player jugador, Obelisco obelisco, int pagina, String filtro) {
        this.ctx = ctx;
        this.jugador = jugador;
        this.obelisco = obelisco;
        this.pagina = pagina;
        this.filtro = filtro == null || filtro.isBlank() ? null : filtro.trim();
        this.mobs = filtrar(ctx.mythic.listar(), this.filtro);
        String titulo = ChatColor.DARK_PURPLE + "MythicMobs (" + mobs.size() + ")";
        if (this.filtro != null) {
            String corto = this.filtro.length() > 12 ? this.filtro.substring(0, 12) + "…" : this.filtro;
            titulo += ChatColor.GRAY + " «" + corto + "»";
        }
        this.inventario = Bukkit.createInventory(this, 54, titulo);
        construir();
    }

    @Override
    public Inventory getInventory() { return inventario; }

    // ------------------------------------------------------------------ buscador

    private static String normalizar(String texto) {
        return texto.toLowerCase(Locale.ROOT).trim();
    }

    private static String sinColores(String nombre) {
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', nombre));
    }

    /**
     * Busca por id interno y por nombre visible, sin importar mayusculas ni colores. Con varias palabras
     * deben aparecer TODAS. Primero salen las coincidencias exactas del id, luego las que empiezan por lo escrito.
     */
    private static List<MythicHook.MobInfo> filtrar(List<MythicHook.MobInfo> todos, String filtro) {
        if (filtro == null) return todos;
        String buscado = normalizar(filtro);
        String[] palabras = buscado.split("\\s+");

        List<MythicHook.MobInfo> resultado = new ArrayList<>();
        for (MythicHook.MobInfo mob : todos) {
            String texto = normalizar(mob.id() + " " + sinColores(mob.nombre()));
            boolean coincide = true;
            for (String palabra : palabras) {
                if (!texto.contains(palabra)) { coincide = false; break; }
            }
            if (coincide) resultado.add(mob);
        }
        resultado.sort(Comparator
            .comparingInt((MythicHook.MobInfo m) -> puntaje(m, buscado))
            .thenComparing(MythicHook.MobInfo::id, String.CASE_INSENSITIVE_ORDER));
        return resultado;
    }

    private static int puntaje(MythicHook.MobInfo mob, String buscado) {
        String id = normalizar(mob.id());
        if (id.equals(buscado)) return 0;
        if (id.startsWith(buscado)) return 1;
        return 2;
    }

    private void pedirBusqueda() {
        jugador.closeInventory();
        jugador.sendMessage(ChatColor.YELLOW + "Escribe el nombre (o parte del nombre) del mob de MythicMobs. "
            + "Puedes poner varias palabras. Escribe 'cancelar' para volver.");
        ctx.entradaTexto.solicitar(jugador.getUniqueId(), "busqueda de mob de MythicMobs", texto -> {
            String escrito = texto.trim();
            String nuevoFiltro = "cancelar".equalsIgnoreCase(escrito) ? filtro : escrito;
            Bukkit.getScheduler().runTask(ctx.plugin,
                () -> new MythicMobSeleccionGuiHolder(ctx, jugador, obelisco, 0, nuevoFiltro).abrir());
        });
    }

    // ------------------------------------------------------------------ GUI

    private void construir() {
        inventario.clear();
        int desde = pagina * POR_PAGINA;
        int hasta = Math.min(mobs.size(), desde + POR_PAGINA);
        for (int i = desde; i < hasta; i++) {
            MythicHook.MobInfo mob = mobs.get(i);
            boolean yaAnadido = obelisco.getOpciones().stream()
                .anyMatch(o -> ctx.bossTemplateManager.obtener(o.getBossTemplateId())
                    .map(t -> mob.id().equalsIgnoreCase(t.getMythicMobId())).orElse(false));
            inventario.setItem(i - desde, MenuItemBuilder.de(icono(mob))
                .nombre(ChatColor.LIGHT_PURPLE + ChatColor.translateAlternateColorCodes('&', mob.nombre()))
                .lore(
                    ChatColor.GRAY + "ID: " + ChatColor.WHITE + mob.id(),
                    mob.tipoEntidad() != null ? ChatColor.GRAY + "Base: " + ChatColor.WHITE + mob.tipoEntidad() : "",
                    yaAnadido ? ChatColor.GREEN + "Ya esta en este obelisco" : "",
                    "",
                    ChatColor.YELLOW + "Clic para anadirlo y configurar sus requisitos").build());
        }
        if (mobs.isEmpty()) {
            inventario.setItem(22, MenuItemBuilder.de(Material.BARRIER)
                .nombre(ChatColor.RED + (filtro != null ? "Ningun mob coincide con «" + filtro + "»" : "No hay mobs en MythicMobs"))
                .lore(filtro != null
                    ? new String[]{ChatColor.GRAY + "Prueba con otra palabra o quita el filtro."}
                    : new String[]{ChatColor.GRAY + "Crea alguno en plugins/MythicMobs/mobs", ChatColor.GRAY + "y haz /mm reload."}).build());
        }

        inventario.setItem(45, pagina > 0 ? flecha("« Pagina anterior") : relleno());
        inventario.setItem(47, MenuItemBuilder.de(Material.COMPASS)
            .nombre(ChatColor.AQUA + "Buscar mob")
            .lore(
                ChatColor.GRAY + "Escribe parte del nombre o del ID.",
                ChatColor.GRAY + "Varias palabras = deben estar todas.",
                filtro != null ? ChatColor.GRAY + "Busqueda actual: " + ChatColor.WHITE + filtro : "",
                ChatColor.YELLOW + "Clic para buscar").build());
        inventario.setItem(49, MenuItemBuilder.de(Material.ARROW).nombre(ChatColor.YELLOW + "« Volver a los jefes").build());
        inventario.setItem(51, filtro != null
            ? MenuItemBuilder.de(Material.BARRIER).nombre(ChatColor.RED + "Quitar busqueda").lore(ChatColor.GRAY + "Volver a ver todos los mobs.").build()
            : relleno());
        inventario.setItem(53, hasta < mobs.size() ? flecha("Pagina siguiente »") : relleno());
    }

    private Material icono(MythicHook.MobInfo mob) {
        if (mob.tipoEntidad() != null) {
            Material huevo = Material.matchMaterial(mob.tipoEntidad().toUpperCase(Locale.ROOT) + "_SPAWN_EGG");
            if (huevo != null) return huevo;
        }
        return Material.DRAGON_HEAD;
    }

    private ItemStack flecha(String texto) {
        return MenuItemBuilder.de(Material.ARROW).nombre(ChatColor.YELLOW + texto).build();
    }

    private ItemStack relleno() {
        return MenuItemBuilder.de(Material.GRAY_STAINED_GLASS_PANE).nombre(" ").build();
    }

    @Override
    public void manejarClic(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() != inventario) return;
        int slot = event.getSlot();

        if (slot == 45 && pagina > 0) { new MythicMobSeleccionGuiHolder(ctx, jugador, obelisco, pagina - 1, filtro).abrir(); return; }
        if (slot == 47) { pedirBusqueda(); return; }
        if (slot == 49) { new ObeliscoJefesGuiHolder(ctx, jugador, obelisco).abrir(); return; }
        if (slot == 51 && filtro != null) { new MythicMobSeleccionGuiHolder(ctx, jugador, obelisco, 0, null).abrir(); return; }
        if (slot == 53) {
            if ((pagina + 1) * POR_PAGINA < mobs.size()) new MythicMobSeleccionGuiHolder(ctx, jugador, obelisco, pagina + 1, filtro).abrir();
            return;
        }

        int indice = pagina * POR_PAGINA + slot;
        if (slot >= POR_PAGINA || indice >= mobs.size()) return;

        MythicHook.MobInfo mob = mobs.get(indice);
        BossTemplate plantilla = ctx.bossTemplateManager.obtenerOCrearMythic(mob.id(), mob.nombre());
        OpcionJefe opcion = new OpcionJefe(plantilla.getId());
        obelisco.getOpciones().add(opcion);
        ctx.obeliskManager.actualizar(obelisco);
        jugador.sendMessage(ChatColor.GREEN + "Jefe anadido: " + ChatColor.LIGHT_PURPLE + mob.id()
            + ChatColor.GRAY + ". Ahora configura sus requisitos y su loot.");
        new OpcionJefeEditorGuiHolder(ctx, jugador, obelisco, opcion).abrir();
    }

    @Override
    public void manejarArrastre(InventoryDragEvent event) { event.setCancelled(true); }

    public void abrir() {
        jugador.openInventory(inventario);
    }
}
