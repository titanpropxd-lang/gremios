package com.bossforge.util;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class MenuItemBuilder {
    private final ItemStack item;
    private final ItemMeta meta;

    private MenuItemBuilder(Material material) {
        this.item = new ItemStack(material);
        this.meta = item.getItemMeta();
    }

    public static MenuItemBuilder de(Material material) {
        return new MenuItemBuilder(material);
    }

    public MenuItemBuilder nombre(String nombre) {
        meta.setDisplayName(ChatColor.RESET + nombre);
        return this;
    }

    public MenuItemBuilder lore(String... lineas) {
        meta.setLore(List.of(lineas));
        return this;
    }

    public MenuItemBuilder lore(List<String> lineas) {
        meta.setLore(lineas);
        return this;
    }

    public MenuItemBuilder cantidad(int cantidad) {
        item.setAmount(Math.max(1, Math.min(64, cantidad)));
        return this;
    }

    public ItemStack build() {
        item.setItemMeta(meta);
        return item;
    }
}
