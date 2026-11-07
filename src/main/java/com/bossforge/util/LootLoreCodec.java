package com.bossforge.util;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * La probabilidad de un loot se muestra como una linea extra de lore
 * SOLO dentro del editor (para poder verla y cambiarla con clics sin
 * necesidad de chat). Antes de guardar el item como loot real, esa
 * linea se retira — nunca queda en el item que finalmente sueltan los
 * jugadores.
 */
public class LootLoreCodec {
    private static final String PREFIJO = ChatColor.GRAY + "Probabilidad de caida: " + ChatColor.YELLOW;
    private static final Pattern PATRON = Pattern.compile("Probabilidad de caida: (\\d+(?:\\.\\d+)?)%");

    private LootLoreCodec() { }

    public static ItemStack conProbabilidadEnLore(ItemStack original, double probabilidad) {
        ItemStack copia = original.clone();
        ItemMeta meta = copia.getItemMeta();
        if (meta == null) return copia;

        List<String> lore = sinLineaDeProbabilidad(meta.hasLore() ? meta.getLore() : new ArrayList<>());
        lore.add(PREFIJO + redondear(probabilidad) + "%");
        meta.setLore(lore);
        copia.setItemMeta(meta);
        return copia;
    }

    public static double extraerProbabilidad(ItemStack item, double porDefecto) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) return porDefecto;
        for (String linea : meta.getLore()) {
            Matcher m = PATRON.matcher(ChatColor.stripColor(linea));
            if (m.find()) return Double.parseDouble(m.group(1));
        }
        return porDefecto;
    }

    /** Devuelve una copia del item SIN la linea de probabilidad — la version que se guarda de verdad. */
    public static ItemStack sinLoreDeProbabilidad(ItemStack item) {
        ItemStack copia = item.clone();
        ItemMeta meta = copia.getItemMeta();
        if (meta == null || !meta.hasLore()) return copia;
        List<String> loreLimpio = sinLineaDeProbabilidad(meta.getLore());
        meta.setLore(loreLimpio.isEmpty() ? null : loreLimpio);
        copia.setItemMeta(meta);
        return copia;
    }

    private static List<String> sinLineaDeProbabilidad(List<String> lore) {
        List<String> resultado = new ArrayList<>();
        for (String linea : lore) {
            if (!PATRON.matcher(ChatColor.stripColor(linea)).find()) resultado.add(linea);
        }
        return resultado;
    }

    private static double redondear(double valor) {
        return Math.round(valor * 10.0) / 10.0;
    }
}
