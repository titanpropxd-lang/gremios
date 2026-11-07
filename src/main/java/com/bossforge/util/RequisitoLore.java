package com.bossforge.util;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Texto "- Item x3" para mostrar una lista de requisitos en el lore de un icono. */
public final class RequisitoLore {
    private RequisitoLore() { }

    public static List<String> lineas(List<ItemStack> requisitos) {
        List<String> lineas = new ArrayList<>();
        for (ItemStack requerido : requisitos) {
            String nombre = requerido.hasItemMeta() && requerido.getItemMeta().hasDisplayName()
                ? requerido.getItemMeta().getDisplayName() : requerido.getType().toString();
            lineas.add(ChatColor.WHITE + "- " + nombre + ChatColor.GRAY + " x" + requerido.getAmount());
        }
        return lineas;
    }
}
