package com.etheria.gremios.manager;

import com.etheria.gremios.GremiosPlugin;
import com.etheria.gremios.model.Guild;
import com.etheria.gremios.util.Msg;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/** Tutorial interactivo: /gremio tutorial. Algunos pasos se completan solos al hacerlos. */
public class TutorialManager implements Listener {
    private static final class Step {
        final String title;
        final Material icon;
        final List<String> text;
        final String hint;
        final Predicate<Player> auto; // null = manual

        Step(String title, Material icon, List<String> text, String hint, Predicate<Player> auto) {
            this.title = title;
            this.icon = icon;
            this.text = text;
            this.hint = hint;
            this.auto = auto;
        }
    }

    private static final class Progress {
        int step;
        boolean done, skipped;
    }

    private static final class Holder implements InventoryHolder {
        Inventory inv;
        int step;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final GremiosPlugin plugin;
    private final File file;
    private final Map<UUID, Progress> data = new HashMap<>();
    private final List<Step> steps = new ArrayList<>();

    public TutorialManager(GremiosPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "tutorial.yml");
        load();
        build();
    }

    private void build() {
        steps.add(new Step("Bienvenido a los gremios", Material.WRITABLE_BOOK, List.of(
                "§7Un gremio es tu comunidad: comparte banco,", "§7territorio, zonas de recursos y tecnologías.",
                "§7Este tutorial te guía paso a paso.", "§7Algunos pasos se marcan solos al hacerlos."), "/gremio", null));
        steps.add(new Step("Únete o funda un gremio", Material.WHITE_BANNER, List.of(
                "§7Pide unirte a uno existente o fúndalo", "§7(necesitas el permiso de un admin).", "§7Los gremios tienen rangos: Recluta → Líder."),
                "/gremio solicitar <nombre>  ·  /gremio crear <nombre>", p -> plugin.guilds.of(p) != null));
        steps.add(new Step("Llena el banco del gremio", Material.GOLD_INGOT, List.of(
                "§7El banco paga el mantenimiento, las", "§7tecnologías, las guerras y los asedios.", "§7Deposita la moneda del servidor."),
                "/gremio depositar <cantidad>", p -> {
                    Guild g = plugin.guilds.of(p);
                    return g != null && g.bank > 0;
                }));
        steps.add(new Step("Reclama un territorio", Material.BELL, List.of(
                "§7Un territorio es tu base protegida.", "§7Puede subir de Pueblo a Fortaleza y Reinado.", "§7Míralos en el mapa de territorios."),
                "/gremio mapa", p -> {
                    Guild g = plugin.guilds.of(p);
                    return g != null && g.hasTerritory();
                }));
        steps.add(new Step("Zonas de recursos", Material.IRON_PICKAXE, List.of(
                "§7Minas, talas, canteras, granjas y pesca", "§7producen objetos para el gremio dueño.", "§7Habla con el aldeano de la zona para cobrar."),
                "/gremio mapa", null));
        steps.add(new Step("Investiga una tecnología", Material.ENCHANTING_TABLE, List.of(
                "§7Más de 80 tecnologías en 8 ramas:", "§7economía, militar, defensa, cultivos, minería,",
                "§7magia, logística y gobierno.", "§7Un Mariscal o superior empieza la investigación."),
                "/gremio tecnologias", p -> {
                    Guild g = plugin.guilds.of(p);
                    return g != null && (plugin.tech.count(g) > 0 || !plugin.tech.doing(g).isEmpty());
                }));
        steps.add(new Step("Alianzas y diplomacia", Material.WHITE_BANNER, List.of(
                "§7Alíate con otros gremios o ponte bajo", "§7la protección de uno mayor como vasallo.",
                "§7Los vasallos pagan tributo y solo pueden", "§7ser atacados en guerra con su señor."), "/gremio diplomacia", null));
        steps.add(new Step("Guerra y asedios", Material.IRON_SWORD, List.of(
                "§7Declara guerra a un gremio y gana puntos", "§7con asedios y bajas. Quien llega primero gana",
                "§7el botín y se firma una tregua.", "§7Los gremios pequeños están protegidos."), "/gremio diplomacia  ·  /gremio asedio", null));
        steps.add(new Step("Eventos y consecuencias", Material.NETHER_STAR, List.of(
                "§7Los eventos del servidor dan premios.", "§7Si se pierden, pueden llegar desastres:",
                "§7sequías, meteoritos, tornados... Prepárate."), "/desastre estado", null));
        steps.add(new Step("¡Tutorial completado!", Material.EMERALD, List.of(
                "§7Ya conoces lo esencial.", "§aRecibes una recompensa al terminar.", "§7Puedes repetirlo con §f/gremio tutorial reiniciar§7."), "", null));
    }

    private Progress prog(UUID u) {
        return data.computeIfAbsent(u, k -> new Progress());
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("tutorial.activo", true);
    }

    // ------------------------------------------------------------------ comandos
    public void command(Player p, String[] args) {
        String a = args.length > 0 ? args[0].toLowerCase() : "";
        Progress pr = prog(p.getUniqueId());
        switch (a) {
            case "saltar", "omitir" -> {
                pr.skipped = true;
                Msg.to(p, "§7Tutorial omitido. Puedes retomarlo con §f/gremio tutorial reiniciar§7.");
                save();
                return;
            }
            case "reiniciar", "reset" -> {
                pr.step = 0;
                pr.done = false;
                pr.skipped = false;
                save();
            }
            default -> {
                if (pr.skipped) pr.skipped = false;
            }
        }
        open(p, pr.done ? steps.size() - 1 : pr.step);
    }

    public void open(Player p, int stepIdx) {
        Holder h = new Holder();
        h.step = Math.max(0, Math.min(steps.size() - 1, stepIdx));
        h.inv = Bukkit.createInventory(h, 27, "§6📘 Tutorial §8(" + (h.step + 1) + "/" + steps.size() + ")");
        draw(h, p);
        p.openInventory(h.inv);
    }

    private ItemStack item(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        it.setItemMeta(meta);
        return it;
    }

    private void draw(Holder h, Player p) {
        Step s = steps.get(h.step);
        Progress pr = prog(p.getUniqueId());
        h.inv.clear();
        List<String> lore = new ArrayList<>(s.text);
        lore.add("");
        if (!s.hint.isEmpty()) lore.add("§eComando: §f" + s.hint);
        if (s.auto != null) lore.add(s.auto.test(p) ? "§a✔ Paso cumplido" : "§7Se marcará solo cuando lo hagas.");
        else lore.add("§7Pulsa Siguiente cuando lo hayas leído.");
        h.inv.setItem(13, item(s.icon, "§6§l" + (h.step + 1) + ". " + s.title, lore));
        if (h.step > 0) h.inv.setItem(11, item(Material.ARROW, "§7◀ Anterior", List.of()));
        if (h.step < steps.size() - 1) h.inv.setItem(15, item(Material.ARROW, "§aSiguiente ▶", List.of()));
        else h.inv.setItem(15, item(Material.EMERALD, pr.done ? "§a✔ Completado" : "§a§lTerminar y recibir recompensa", List.of()));
        h.inv.setItem(22, item(Material.BARRIER, "§cCerrar", List.of("§7Clic derecho: omitir el tutorial")));
        for (int i = 0; i < 27; i++) {
            if (h.inv.getItem(i) == null) h.inv.setItem(i, item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()));
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != h.inv) return;
        Progress pr = prog(p.getUniqueId());
        switch (e.getRawSlot()) {
            case 11 -> {
                if (h.step > 0) h.step--;
            }
            case 15 -> {
                if (h.step < steps.size() - 1) {
                    h.step++;
                    pr.step = Math.max(pr.step, h.step);
                } else if (!pr.done) {
                    finish(p, pr);
                    p.closeInventory();
                    return;
                }
            }
            case 22 -> {
                if (e.isRightClick()) {
                    pr.skipped = true;
                    Msg.to(p, "§7Tutorial omitido. Retómalo con §f/gremio tutorial reiniciar§7.");
                }
                p.closeInventory();
                save();
                return;
            }
            default -> {
                return;
            }
        }
        draw(h, p);
        save();
    }

    private void finish(Player p, Progress pr) {
        pr.done = true;
        pr.step = steps.size() - 1;
        save();
        Msg.to(p, "§a🎓 ¡Has completado el tutorial de gremios!");
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        long coin = plugin.getConfig().getLong("tutorial.recompensa-moneda", 8);
        if (coin > 0 && plugin.currency.isSet()) {
            plugin.currency.give(p, coin);
            Msg.to(p, "§7Recompensa: §e" + plugin.currency.fmt(coin));
        }
        long xp = plugin.getConfig().getLong("tutorial.recompensa-xp-gremio", 150);
        Guild g = plugin.guilds.of(p);
        if (g != null && xp > 0) plugin.guilds.addXp(g, xp);
        for (String c : plugin.getConfig().getStringList("tutorial.comandos")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c.replace("{jugador}", p.getName()));
        }
    }

    // ------------------------------------------------------------------ avance automático
    public void tick() {
        if (!enabled()) return;
        boolean remind = plugin.getConfig().getBoolean("tutorial.recordatorio", true);
        for (Player p : Bukkit.getOnlinePlayers()) {
            Progress pr = data.get(p.getUniqueId());
            if (pr == null || pr.done || pr.skipped) continue;
            Step s = steps.get(Math.min(pr.step, steps.size() - 1));
            if (s.auto != null && s.auto.test(p) && pr.step < steps.size() - 1) {
                pr.step++;
                save();
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f);
                Msg.to(p, "§a✔ Paso completado: §f" + s.title + "§7. Siguiente: §f" + steps.get(pr.step).title + " §8(/gremio tutorial)");
            } else if (remind && p.getOpenInventory().getTopInventory().getHolder() == null) {
                Msg.actionbar(p, "§6📘 Tutorial §7paso " + (pr.step + 1) + "/" + steps.size() + ": §f" + s.title + " §8(/gremio tutorial)");
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!enabled() || !plugin.getConfig().getBoolean("tutorial.ofrecer-al-entrar", true)) return;
        Player p = e.getPlayer();
        if (data.containsKey(p.getUniqueId())) return;
        Progress np = new Progress();
        if (plugin.guilds.of(p) != null) np.skipped = true; // veteranos: sin avisos
        data.put(p.getUniqueId(), np);
        save();
        if (np.skipped) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            TextComponent c = new TextComponent("§6📘 ¿Quieres aprender cómo funcionan los gremios? §e§l[CLIC AQUÍ PARA EL TUTORIAL]");
            c.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/gremio tutorial"));
            p.spigot().sendMessage(c);
        }, 100L);
    }

    // ------------------------------------------------------------------ persistencia
    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection s = y.getConfigurationSection("jugadores");
        if (s == null) return;
        for (String k : s.getKeys(false)) {
            try {
                Progress p = new Progress();
                p.step = s.getInt(k + ".paso");
                p.done = s.getBoolean(k + ".completo");
                p.skipped = s.getBoolean(k + ".omitido");
                data.put(UUID.fromString(k), p);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (var e : data.entrySet()) {
            y.set("jugadores." + e.getKey() + ".paso", e.getValue().step);
            y.set("jugadores." + e.getKey() + ".completo", e.getValue().done);
            y.set("jugadores." + e.getKey() + ".omitido", e.getValue().skipped);
        }
        try {
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudo guardar tutorial.yml: " + ex.getMessage());
        }
    }
}
