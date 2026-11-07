package com.bossforge.model;

import org.bukkit.inventory.ItemStack;

public class LootEntry {
    private ItemStack item;
    private double probabilidad; // 0-100

    public LootEntry() { }

    public LootEntry(ItemStack item, double probabilidad) {
        this.item = item;
        this.probabilidad = probabilidad;
    }

    public ItemStack getItem() { return item; }
    public void setItem(ItemStack item) { this.item = item; }

    public double getProbabilidad() { return probabilidad; }
    public void setProbabilidad(double probabilidad) { this.probabilidad = probabilidad; }
}
