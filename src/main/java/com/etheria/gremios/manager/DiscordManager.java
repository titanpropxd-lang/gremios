package com.etheria.gremios.manager;

import com.etheria.gremios.GremiosPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Anuncios a Discord por webhook (sin bot). Los envíos van en un hilo aparte y nunca bloquean el servidor.
 * Tipos: asedios, eventos, gremios, alianzas, ranking.
 */
public class DiscordManager {
    private final GremiosPlugin plugin;
    private final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();
    private volatile boolean running;
    private long retryAfter = 0;

    public static final String[] KINDS = {"asedios", "eventos", "gremios", "alianzas", "ranking", "servidor"};
    private final java.util.Map<String, Boolean> over = new java.util.HashMap<>();
    private final java.io.File file;
    private String hookOverride = "";
    private Boolean activeOverride = null;

    public DiscordManager(GremiosPlugin plugin) {
        this.plugin = plugin;
        this.file = new java.io.File(plugin.getDataFolder(), "discord.yml");
        if (file.exists()) {
            var y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
            hookOverride = y.getString("webhook", "").trim();
            if (y.contains("activo")) activeOverride = y.getBoolean("activo");
            var s = y.getConfigurationSection("tipos");
            if (s != null) for (String k : s.getKeys(false)) over.put(k, s.getBoolean(k));
        }
    }

    public static String label(String k) {
        return switch (k) {
            case "asedios" -> "Asedios";
            case "eventos" -> "Eventos";
            case "gremios" -> "Gremios";
            case "alianzas" -> "Alianzas";
            case "ranking" -> "Ranking";
            default -> "Servidor";
        };
    }

    public static String describe(String k) {
        return switch (k) {
            case "asedios" -> "Declaraciones, inicios y resultados de asedios.";
            case "eventos" -> "Jefes y eventos de mundo y quién los gana.";
            case "gremios" -> "Gremios fundados, disueltos y que suben de nivel.";
            case "alianzas" -> "Alianzas selladas o rotas.";
            case "ranking" -> "Ranking semanal y MVP.";
            default -> "El servidor se enciende o se apaga.";
        };
    }

    public boolean hasWebhook() {
        return !url().isBlank();
    }

    public boolean typeOn(String kind) {
        Boolean o = over.get(kind);
        return o != null ? o : plugin.getConfig().getBoolean("discord.tipos." + kind, true);
    }

    /** Guarda el webhook en discord.yml (no hace falta tocar config.yml) y activa los anuncios. */
    public String setWebhook(String u) {
        u = u.trim().replace("\"", "").replace("'", "").replace("<", "").replace(">", "");
        if (u.startsWith("discord.com/") || u.startsWith("discordapp.com/")) u = "https://" + u;
        if (!u.startsWith("https://discord.com/api/webhooks/") && !u.startsWith("https://discordapp.com/api/webhooks/")
                && !u.startsWith("https://canary.discord.com/api/webhooks/") && !u.startsWith("https://ptb.discord.com/api/webhooks/"))
            return "Eso no parece un webhook de Discord (debe empezar por https://discord.com/api/webhooks/).";
        hookOverride = u;
        activeOverride = true;
        save();
        return null;
    }

    public void setActive(boolean on) {
        activeOverride = on;
        save();
    }

    public void setType(String kind, boolean on) {
        over.put(kind, on);
        save();
    }

    private void save() {
        var y = new org.bukkit.configuration.file.YamlConfiguration();
        if (!hookOverride.isEmpty()) y.set("webhook", hookOverride);
        if (activeOverride != null) y.set("activo", activeOverride);
        for (var e : over.entrySet()) y.set("tipos." + e.getKey(), e.getValue());
        try {
            y.save(file);
        } catch (java.io.IOException ex) {
            plugin.getLogger().warning("No se pudo guardar discord.yml: " + ex.getMessage());
        }
    }

    /** Envía una prueba y avisa al admin del resultado real de Discord. */
    public void test(org.bukkit.command.CommandSender who) {
        if (!hasWebhook()) {
            who.sendMessage("§cNo hay webhook. Ponlo con: §f/gremioadmin discord webhook <url>");
            return;
        }
        if (!active()) who.sendMessage("§eOjo: discord.activo está en false, los anuncios reales no se envían. Mando la prueba igualmente.");
        String json = build("gremios", "Mensaje de prueba de GremiosGUI enviado por " + who.getName() + ". Si lo ves, el webhook funciona.");
        who.sendMessage("§7Enviando prueba a Discord...");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String r = send(json);
            Bukkit.getScheduler().runTask(plugin, () -> who.sendMessage(r == null ? "§a✔ Discord aceptó el mensaje. Revisa el canal." : "§c✘ " + r));
        });
    }

    /** Mensaje libre del admin: /gremioadmin discord anunciar <texto>. */
    public void custom(org.bukkit.command.CommandSender who, String text) {
        if (problem() != null) {
            who.sendMessage("§c" + problem());
            return;
        }
        if (text.isBlank()) {
            who.sendMessage("§e/gremioadmin discord anunciar <texto>");
            return;
        }
        announceRaw("servidor", text);
        who.sendMessage("§aEnviado a Discord.");
    }

    public boolean enabled() {
        return active() && hasWebhook();
    }

    public boolean active() {
        return activeOverride != null ? activeOverride : plugin.getConfig().getBoolean("discord.activo", true);
    }

    private String fileHook() {
        try {
            java.io.File f = new java.io.File(plugin.getDataFolder(), "discord-webhook.txt");
            if (f.isFile()) for (String l : java.nio.file.Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                l = l.trim();
                if (l.startsWith("http")) return l;
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    /** De dónde sale el webhook: comando (discord.yml), config.yml o discord-webhook.txt. */
    public String source() {
        if (!hookOverride.isEmpty()) return "comando (discord.yml)";
        if (!plugin.getConfig().getString("discord.webhook", "").trim().isEmpty()) return "config.yml";
        if (!fileHook().isEmpty()) return "discord-webhook.txt";
        return "ninguna";
    }

    public String masked() {
        String u = url();
        return u.length() < 12 ? "(vacío)" : "…" + u.substring(u.length() - 6);
    }

    /** Explica en una línea por qué no se envía nada, o null si todo está bien. */
    public String problem() {
        if (!hasWebhook()) return "Falta el webhook. Usa /gremioadmin discord webhook <url> o crea plugins/GremiosGUI/discord-webhook.txt con la URL.";
        if (!active()) return "Está apagado. Usa /gremioadmin discord activar.";
        return null;
    }

    public void logStatus() {
        String p = problem();
        plugin.getLogger().info("Discord: " + (p == null ? "listo (webhook " + masked() + ", origen: " + source() + ")" : p));
    }

    private String url() {
        if (!hookOverride.isEmpty()) return hookOverride;
        String c = plugin.getConfig().getString("discord.webhook", "").trim();
        return c.isEmpty() ? fileHook() : c;
    }

    private int color(String kind) {
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("discord.colores");
        String def = switch (kind) {
            case "asedios" -> "E74C3C";
            case "eventos" -> "F1C40F";
            case "gremios" -> "3498DB";
            case "alianzas" -> "2ECC71";
            default -> "9B59B6";
        };
        String h = c == null ? def : c.getString(kind, def);
        try {
            return Integer.parseInt(h.replace("#", ""), 16);
        } catch (NumberFormatException e) {
            return 0x95A5A6;
        }
    }

    /** Quita códigos de color de Minecraft (§x) y el prefijo del plugin. */
    public static String clean(String s) {
        if (s == null) return "";
        return s.replaceAll("§[0-9a-fk-orA-FK-OR]", "").replaceAll("[\\p{Cntrl}&&[^\n]]", "").trim();
    }

    private static String esc(String s) {
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                default -> b.append(ch);
            }
        }
        return b.toString();
    }

    private String build(String kind, String text) {
        String t = clean(text);
        if (t.length() > 1800) t = t.substring(0, 1800);
        String title = plugin.getConfig().getString("discord.titulos." + kind, label(kind));
        return "{\"username\":\"" + esc(plugin.getConfig().getString("discord.nombre", "Crónicas")) + "\","
                + "\"embeds\":[{\"title\":\"" + esc(title) + "\",\"description\":\"" + esc(t) + "\",\"color\":" + color(kind)
                + ",\"timestamp\":\"" + java.time.Instant.now() + "\"}]}";
    }

    /** Envía un anuncio si el tipo está activado. */
    public void announce(String kind, String text) {
        if (!enabled() || !typeOn(kind)) return;
        announceRaw(kind, text);
    }

    /** Envío inmediato en el hilo actual (solo para el apagado, cuando ya no se pueden crear tareas). */
    public void announceNow(String kind, String text) {
        if (!enabled() || !typeOn(kind)) return;
        send(build(kind, text));
    }

    private void announceRaw(String kind, String text) {
        if (clean(text).isEmpty()) return;
        if (queue.size() > 50) queue.poll();
        queue.add(build(kind, text));
        pump();
    }

    private synchronized void pump() {
        if (running) return;
        running = true;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String j;
                while ((j = queue.poll()) != null) {
                    long wait = retryAfter - System.currentTimeMillis();
                    if (wait > 0) Thread.sleep(Math.min(wait, 10_000));
                    String err = send(j);
                    if (err != null) {
                        plugin.getLogger().warning("Discord: " + err);
                        if (err.contains("429")) {
                            retryAfter = System.currentTimeMillis() + 3000;
                            queue.add(j);
                        }
                    }
                    Thread.sleep(600);
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } finally {
                running = false;
                if (!queue.isEmpty()) pump();
            }
        });
    }

    /** Devuelve null si fue bien, o el motivo del fallo. */
    private String send(String json) {
        try {
            HttpURLConnection c = (HttpURLConnection) URI.create(url()).toURL().openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(5000);
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("User-Agent", "GremiosGUI");
            c.setDoOutput(true);
            try (OutputStream o = c.getOutputStream()) {
                o.write(json.getBytes(StandardCharsets.UTF_8));
            }
            int code = c.getResponseCode();
            c.disconnect();
            if (code == 429) return "Discord limita la velocidad (429). Espera unos segundos y reintenta.";
            if (code == 404 || code == 401) return "Webhook inválido o borrado (" + code + "). Crea otro y pégalo en config.yml.";
            if (code >= 400) return "Discord respondió " + code + ".";
            return null;
        } catch (Exception e) {
            return "No se pudo conectar: " + e.getMessage() + " (¿el hosting bloquea discord.com?)";
        }
    }
}
