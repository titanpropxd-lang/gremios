package com.etheria.gremios.gui;

import com.etheria.gremios.GremiosPlugin;
import com.etheria.gremios.manager.SeasonManager;
import com.etheria.gremios.model.Guild;
import com.etheria.gremios.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Menú de ranking semanal, temporada, historial y premios (/gremio ranking). */
public class SeasonGui implements Listener {
    private enum Mode { SEM_JUG, SEM_GRE, TEM_JUG, TEM_GRE, HIST, PREMIOS }

    private static final int[] GRID = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

    private static final class Holder implements InventoryHolder {
        Inventory inv;
        Mode mode = Mode.SEM_JUG;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final GremiosPlugin plugin;

    public SeasonGui(GremiosPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player p) {
        if (!plugin.seasons.enabled()) {
            Msg.to(p, "§cEl ranking y las temporadas están desactivados.");
            return;
        }
        Holder h = new Holder();
        h.inv = Bukkit.createInventory(h, 54, "§8🏆 §6Ranking y temporadas");
        draw(h, p);
        p.openInventory(h.inv);
    }

    private ItemStack item(Material m, String name, List<String> lore, boolean glow) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        if (glow) meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        it.setItemMeta(meta);
        return it;
    }

    private static String medal(int pos) {
        return switch (pos) {
            case 1 -> "§6§l① ";
            case 2 -> "§7§l② ";
            case 3 -> "§c§l③ ";
            default -> "§8" + pos + "º ";
        };
    }

    private void draw(Holder h, Player p) {
        SeasonManager sm = plugin.seasons;
        h.inv.clear();
        ItemStack f = item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of(), false);
        for (int i = 0; i < 54; i++) h.inv.setItem(i, f);
        Mode[] ms = Mode.values();
        Material[] mat = {Material.PLAYER_HEAD, Material.WHITE_BANNER, Material.GOLDEN_HELMET, Material.GOLD_BLOCK, Material.BOOK, Material.CHEST};
        String[] nm = {"§eSemana · jugadores", "§eSemana · gremios", "§6Temporada · jugadores", "§6Temporada · gremios", "§bHistorial", "§aPremios"};
        for (int i = 0; i < ms.length; i++)
            h.inv.setItem(i + 1, item(mat[i], (h.mode == ms[i] ? "§l▶ " : "") + nm[i], List.of(h.mode == ms[i] ? "§8Abierto" : "§eClic: §7ver"), h.mode == ms[i]));
        Guild mine = plugin.guilds.of(p);
        switch (h.mode) {
            case SEM_JUG, TEM_JUG -> {
                boolean seas = h.mode == Mode.TEM_JUG;
                List<SeasonManager.Row> rows = sm.topPlayers(seas, GRID.length);
                for (int i = 0; i < rows.size(); i++) {
                    SeasonManager.Row r = rows.get(i);
                    ItemStack it = new ItemStack(Material.PLAYER_HEAD);
                    SkullMeta meta = (SkullMeta) it.getItemMeta();
                    UUID u = UUID.fromString(r.key());
                    meta.setOwningPlayer(Bukkit.getOfflinePlayer(u));
                    meta.setDisplayName(medal(i + 1) + "§f" + r.name());
                    List<String> lore = new ArrayList<>();
                    lore.add("§7Gremio: §f" + r.sub());
                    lore.add("§7Puntos: §e" + Msg.num(r.pts()));
                    if (r.stats() != null)
                        lore.add("§8Bajas " + r.stats().kills + " · Asedios " + r.stats().raids + " · Misiones " + r.stats().missions + " · Nodo " + Msg.num(r.stats().nodeDmg));
                    String t = sm.titleOf(u);
                    if (!t.isEmpty()) lore.add("§7Título: " + t);
                    meta.setLore(lore);
                    if (r.key().equals(p.getUniqueId().toString())) meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                    it.setItemMeta(meta);
                    h.inv.setItem(GRID[i], it);
                }
                if (rows.isEmpty()) h.inv.setItem(22, item(Material.BARRIER, "§7Todavía nadie ha sumado puntos", List.of(), false));
            }
            case SEM_GRE, TEM_GRE -> {
                boolean seas = h.mode == Mode.TEM_GRE;
                List<SeasonManager.Row> rows = sm.topGuilds(seas, GRID.length);
                for (int i = 0; i < rows.size(); i++) {
                    SeasonManager.Row r = rows.get(i);
                    Guild g = plugin.guilds.byId(r.key());
                    List<String> lore = new ArrayList<>();
                    lore.add("§7Puntos: §e" + Msg.num(r.pts()));
                    lore.add(r.sub());
                    if (g != null) {
                        lore.add("§7Nivel: §f" + g.level + " §8· §7Miembros: §f" + g.members.size());
                        String t = sm.titleOf(g);
                        if (!t.isEmpty()) lore.add("§7Título: " + t);
                    }
                    boolean me = mine != null && mine.id.equals(r.key());
                    h.inv.setItem(GRID[i], item(i == 0 ? Material.GOLD_BLOCK : i == 1 ? Material.IRON_BLOCK : i == 2 ? Material.COPPER_BLOCK : Material.WHITE_BANNER,
                            medal(i + 1) + "§f" + r.name(), lore, me));
                }
                if (rows.isEmpty()) h.inv.setItem(22, item(Material.BARRIER, "§7Todavía ningún gremio ha sumado puntos", List.of(), false));
            }
            case HIST -> {
                List<String> hl = sm.historyLines();
                for (int i = 0; i < hl.size() && i < GRID.length; i++) {
                    boolean season = hl.get(i).startsWith("TEMPORADA");
                    h.inv.setItem(GRID[i], item(season ? Material.NETHER_STAR : Material.PAPER, (season ? "§6" : "§7") + hl.get(i), List.of(), season));
                }
                if (hl.isEmpty()) h.inv.setItem(22, item(Material.BARRIER, "§7Aún no hay historial", List.of("§7Se llena al cerrar cada semana."), false));
            }
            case PREMIOS -> {
                var c = plugin.getConfig();
                h.inv.setItem(20, item(Material.GOLD_INGOT, "§e★ Semanal · jugadores",
                        List.of("§7Top: §f" + c.getLongList("temporadas.premios.jugador-top"), "§7El 1º es el §eMVP§7 y lleva título."), false));
                h.inv.setItem(22, item(Material.GOLD_BLOCK, "§6♛ Semanal · gremios",
                        List.of("§7Top: §f" + c.getLongList("temporadas.premios.gremio-top"), "§7+ XP de gremio. Título «Gremio de la semana»."), false));
                h.inv.setItem(24, item(Material.NETHER_STAR, "§6✦ Temporada",
                        List.of("§7Gremios: §f" + c.getLongList("temporadas.premios.temporada-gremio"),
                                "§7Jugadores: §f" + c.getLongList("temporadas.premios.temporada-jugador"),
                                "§7Títulos de Campeón/Subcampeón hasta la próxima temporada."), true));
                h.inv.setItem(31, item(Material.WRITABLE_BOOK, "§bCómo se puntúa",
                        List.of("§7Misión: §f" + c.getLong("temporadas.puntos.mision", 10),
                                "§7Baja en guerra/asedio: §f" + c.getLong("temporadas.puntos.baja", 15),
                                "§7Daño al Nodo: §f" + c.getDouble("temporadas.puntos.nodo-por-100-de-dano", 2.0) + " por 100",
                                "§7Ganar un asedio (atacando): §f" + c.getLong("temporadas.puntos.raid-victoria-gremio", 100) + " gremio",
                                "§7Defender un asedio: §f" + c.getLong("temporadas.puntos.raid-defensa-gremio", 80) + " gremio",
                                "§7Participar en el asedio ganado: §f" + c.getLong("temporadas.puntos.raid-participante", 25),
                                "§7Reliquia custodiada: §f" + c.getLong("temporadas.puntos.reliquia-semanal", 25) + " por semana"), false));
            }
        }
        // panel inferior
        List<String> info = new ArrayList<>();
        info.add("§7Temporada §f" + sm.season + " §8· §7semana §f" + sm.weekInSeason + "§7/§f" + sm.weeksPerSeason());
        info.add("§7Cierra la semana en: §f" + Msg.dur(sm.weekLeftMs()));
        info.add("§7Fin de temporada en: §f" + Msg.dur(sm.seasonLeftMs()));
        info.add("");
        info.add("§7Tus puntos: §e" + Msg.num(sm.ptsOf(p, false)) + " §8(semana) §e" + Msg.num(sm.ptsOf(p, true)) + " §8(temporada)");
        int pos = sm.positionOf(p.getUniqueId().toString(), false, false);
        if (pos > 0) info.add("§7Tu posición semanal: §f#" + pos);
        if (mine != null) {
            int gpos = sm.positionOf(mine.id, false, true);
            if (gpos > 0) info.add("§7Posición de tu gremio: §f#" + gpos);
            String t = sm.titleOf(mine);
            if (!t.isEmpty()) info.add("§7Título de tu gremio: " + t);
        }
        String mt = sm.titleOf(p.getUniqueId());
        if (!mt.isEmpty()) info.add("§7Tu título: " + mt);
        h.inv.setItem(49, item(Material.CLOCK, "§6§lTemporada " + sm.season, info, true));
        h.inv.setItem(53, item(Material.BARRIER, "§cCerrar", List.of(), false));
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != h.inv) return;
        int s = e.getRawSlot();
        if (s == 53) {
            p.closeInventory();
            return;
        }
        if (s >= 1 && s <= Mode.values().length) {
            h.mode = Mode.values()[s - 1];
            draw(h, p);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
