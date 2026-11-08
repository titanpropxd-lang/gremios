package com.etheria.gremios.manager;

import com.etheria.gremios.GremiosPlugin;
import com.etheria.gremios.model.Guild;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Registro de auditoría: quién movió el banco, cambió rangos, expulsó, etc.
 * Se guarda en plugins/GremiosGUI/auditoria.log (una línea por acción) y se consulta con /gremioadmin auditoria.
 * Formato de línea: fecha|gremioId|actor|accion|detalle
 */
public class AuditManager {
    private final GremiosPlugin plugin;
    private final File file;
    private final SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public AuditManager(GremiosPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "auditoria.log");
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace('|', '/').replace('\n', ' ').replaceAll("§.", "");
    }

    public void log(Guild g, String actor, String action, String detail) {
        log(g == null ? "-" : g.id, actor, action, detail);
    }

    public void log(Guild g, Player actor, String action, String detail) {
        log(g, actor == null ? "sistema" : actor.getName(), action, detail);
    }

    public void log(String guildId, String actor, String action, String detail) {
        if (!plugin.getConfig().getBoolean("antiabuso.auditoria", true)) return;
        final String line = fmt.format(new Date()) + "|" + clean(guildId) + "|" + clean(actor) + "|" + clean(action) + "|" + clean(detail);
        // escritura asíncrona para no frenar el servidor
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            synchronized (this) {
                try (BufferedWriter w = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                    w.write(line);
                    w.newLine();
                } catch (IOException ex) {
                    plugin.getLogger().warning("No se pudo escribir la auditoría: " + ex.getMessage());
                }
            }
        });
    }

    /** Últimas n líneas que contengan el filtro (gremio, jugador o acción); vacío = todas. */
    public synchronized List<String> recent(String filter, int n) {
        List<String> out = new ArrayList<>();
        if (!file.exists()) return out;
        try {
            List<String> all = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            String f = filter == null ? "" : filter.toLowerCase();
            for (int i = all.size() - 1; i >= 0 && out.size() < n; i--) {
                String l = all.get(i);
                if (f.isEmpty() || l.toLowerCase().contains(f)) out.add(0, l);
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudo leer la auditoría: " + ex.getMessage());
        }
        return out;
    }

    public static String pretty(String line) {
        String[] p = line.split("\\|", 5);
        if (p.length < 5) return "§7" + line;
        return "§8" + p[0].substring(5) + " §6" + p[1] + " §f" + p[2] + " §e" + p[3] + " §7" + p[4];
    }
}
