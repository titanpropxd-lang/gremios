package com.bossforge.model;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Una "opcion" de un obelisco, como en ARK: un jefe que se puede invocar con SUS propios
 * requisitos (items a depositar) y SU propio loot. Un obelisco puede tener varias opciones
 * y el jugador elige cual invocar.
 */
public class OpcionJefe {
    private String id = UUID.randomUUID().toString();
    /** Id del BossTemplate (propio o envoltorio de un mob de MythicMobs). */
    private String bossTemplateId;
    private List<ItemStack> requisitos = new ArrayList<>();
    private List<LootEntry> loot = new ArrayList<>();

    public OpcionJefe() { }

    public OpcionJefe(String bossTemplateId) {
        this.bossTemplateId = bossTemplateId;
    }

    public String getId() { return id; }
    public void setId(String v) { this.id = v; }

    public String getBossTemplateId() { return bossTemplateId; }
    public void setBossTemplateId(String v) { this.bossTemplateId = v; }

    public List<ItemStack> getRequisitos() { return requisitos; }
    public void setRequisitos(List<ItemStack> v) { this.requisitos = v != null ? v : new ArrayList<>(); }

    public List<LootEntry> getLoot() { return loot; }
    public void setLoot(List<LootEntry> v) { this.loot = v != null ? v : new ArrayList<>(); }

    /** Una opcion se puede ofrecer a los jugadores cuando tiene jefe y al menos un requisito. */
    public boolean estaCompleta() {
        return bossTemplateId != null && !requisitos.isEmpty();
    }
}
