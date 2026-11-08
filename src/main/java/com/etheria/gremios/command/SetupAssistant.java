package com.etheria.gremios.command;

import com.etheria.gremios.GremiosPlugin;
import com.etheria.gremios.model.NpcRole;
import com.etheria.gremios.model.Territory;
import com.etheria.gremios.util.Compat;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** /gremioadmin asistente: lista de pasos en orden, con ✔/✘ y un clic para rellenar el comando que falta. */
public class SetupAssistant {
    private final GremiosPlugin plugin;
    private final CommandSender s;
    private int done, total;
    private String next;

    private record Step(boolean ok, boolean optional, String title, String why, String cmd) {
    }

    public SetupAssistant(GremiosPlugin plugin, CommandSender s) {
        this.plugin = plugin;
        this.s = s;
    }

    public void run() {
        List<Step> steps = new ArrayList<>();
        String vp = plugin.currency.vaultProblem();
        steps.add(new Step(plugin.currency.isSet() && vp == null, false, "Moneda del servidor",
                vp != null ? vp : "Sostén el objeto que será la moneda y pulsa. (o pon currency.modo: vault en config.yml)", "/gremioadmin moneda set"));

        var ts = plugin.territories.all();
        steps.add(new Step(!ts.isEmpty(), false, "Crear al menos un territorio (" + ts.size() + ")",
                "Selecciona dos esquinas con la varita y ponle nombre.", "/territorio varita"));
        Territory bad = null;
        String badWhy = "";
        for (Territory t : ts) {
            if (Bukkit.getWorld(t.world) == null) {
                bad = t;
                badWhy = "su mundo no existe";
            } else if (!plugin.snapshots.has(t)) {
                bad = t;
                badWhy = "falta el snapshot (copia para restaurar tras un asedio)";
            } else if (t.node == null) {
                bad = t;
                badWhy = "falta la posición del Nodo";
            } else if (t.spawn == null) {
                bad = t;
                badWhy = "falta el spawn";
            } else {
                List<String> miss = new ArrayList<>();
                for (NpcRole r : NpcRole.values()) if (!t.npcPos.containsKey(r.key())) miss.add(r.key());
                if (!miss.isEmpty()) {
                    bad = t;
                    badWhy = "faltan NPC: " + String.join(", ", miss);
                }
            }
            if (bad != null) break;
        }
        if (!ts.isEmpty()) {
            String cmd = "/territorio info " + (bad == null ? ts.iterator().next().id : bad.id);
            if (bad != null && badWhy.contains("snapshot")) cmd = "/territorio snapshot " + bad.id;
            else if (bad != null && badWhy.contains("Nodo")) cmd = "/territorio setnodo " + bad.id;
            else if (bad != null && badWhy.contains("spawn")) cmd = "/territorio setspawn " + bad.id;
            else if (bad != null && badWhy.contains("NPC")) cmd = "/territorio npc " + bad.id + " ";
            steps.add(new Step(bad == null, false, "Territorios completos (snapshot, Nodo, spawn, NPC)",
                    bad == null ? "" : bad.name + ": " + badWhy, cmd));
        }

        steps.add(new Step(!plugin.zones.all().isEmpty(), true, "Zonas de recursos (" + plugin.zones.all().size() + ")", "Minas/granjas que dan recursos al gremio.", "/gremioadmin zona crear "));
        steps.add(new Step(!plugin.events.all().isEmpty(), true, "Eventos (KOTH, jefes…) (" + plugin.events.all().size() + ")", "Contenido recurrente para todo el servidor.", "/gremioadmin evento "));
        steps.add(new Step(!plugin.missions.all().isEmpty(), true, "Misiones de gremio (" + plugin.missions.all().size() + ")", "Objetivos diarios con recompensa.", "/gremioadmin mision crear "));
        steps.add(new Step(!plugin.board.all().isEmpty(), true, "Tablón de misiones físico", "Un tablón en la capital.", "/gremioadmin tablon crear "));

        boolean pack = plugin.usePack();
        String rp = "";
        try {
            rp = Bukkit.getServer().getResourcePack();
        } catch (Throwable ignored) {
        }
        boolean rpSet = rp != null && !rp.isBlank();
        steps.add(new Step(!pack || rpSet, false, "Resourcepack", pack
                ? "gui.usar-resourcepack=true pero server.properties no tiene resource-pack. Sin él los menús se ven rotos (o pon gui.usar-resourcepack: false)."
                : "Menús en modo texto.", "/gremioadmin ayuda resourcepack"));

        boolean dc = plugin.discord != null && plugin.discord.active();
        steps.add(new Step(dc, true, "Avisos a Discord", "Webhook del canal de avisos.", "/gremioadmin discord webhook "));
        steps.add(new Step(plugin.web != null && plugin.getConfig().getBoolean("web.activo", true), true, "Mapa web", "Mira el enlace con /gremio mapaweb.", "/gremioadmin web estado"));
        steps.add(new Step(Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI"), true, "PlaceholderAPI", "Para %gremio_*% en scoreboards/BeautyQuests.", "/gremioadmin ayuda placeholders"));

        boolean vaultWanted = "vault".equalsIgnoreCase(plugin.getConfig().getString("currency.modo", "item"));
        if (vaultWanted || Compat.has("Vault")) {
            steps.add(new Step(vaultWanted && vp == null, true, "Economía con Vault", vaultWanted ? (vp == null ? "" : vp)
                    : "Vault detectado: puedes usar su dinero en vez de la moneda física (currency.modo: vault).", "/gremioadmin ayuda vault"));
        }
        boolean bedrock = Compat.has("Geyser-Spigot") || Compat.has("floodgate");
        if (bedrock) steps.add(new Step(plugin.getConfig().getBoolean("compat.bedrock.menus-simples", true), true, "Bedrock/Geyser", "Los jugadores Bedrock ven menús simples (sin resourcepack).", ""));
        if (Compat.has("WorldGuard")) {
            steps.add(new Step(true, true, "WorldGuard detectado", "Coexisten: si una región de WG y un territorio se solapan, protege el que sea más restrictivo. Evita solaparlos.", ""));
        }
        steps.add(new Step(plugin.getConfig().getBoolean("justicia.activo", true) && plugin.getConfig().getBoolean("antiabuso.activo", true), true,
                "Justicia y anti-abuso activos", "Revisa los límites en config.yml (justicia.*, antiabuso.*).", "/gremioadmin ayuda justicia"));

        s.sendMessage("§6§m                  §r §6§lAsistente de configuración §6§m                  ");
        for (Step st : steps) {
            total++;
            if (st.ok) done++;
            else if (next == null && !st.optional) next = st.title;
            line(st);
        }
        s.sendMessage("§7Progreso: §f" + done + "§7/§f" + total + (next == null ? " §a— lo esencial está listo." : " §7— siguiente paso: §e" + next));
        s.sendMessage("§8Pulsa un paso ✘ para rellenar el comando. §7(/gremioadmin diagnostico da el detalle completo)");
    }

    private void line(Step st) {
        String mark = st.ok ? "§a ✔ " : (st.optional ? "§e ○ " : "§c ✘ ");
        String text = mark + "§f" + st.title + (st.optional && !st.ok ? " §8(opcional)" : "");
        if (s instanceof Player p) {
            TextComponent c = new TextComponent(net.md_5.bungee.api.chat.TextComponent.fromLegacyText(text));
            if (!st.ok && !st.cmd.isEmpty()) {
                for (var e : c.getExtra()) e.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, st.cmd));
                c.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, st.cmd));
                c.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(
                        (st.why.isEmpty() ? "" : st.why + "\n") + "Clic: " + st.cmd)));
            }
            p.spigot().sendMessage(c);
            if (!st.ok && !st.why.isEmpty()) p.sendMessage("§8     " + st.why);
        } else {
            s.sendMessage(text + (st.ok || st.why.isEmpty() ? "" : " §7- " + st.why) + (st.ok || st.cmd.isEmpty() ? "" : " §8" + st.cmd));
        }
    }
}
