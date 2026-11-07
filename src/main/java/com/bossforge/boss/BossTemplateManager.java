package com.bossforge.boss;

import com.bossforge.model.BossTemplate;
import com.bossforge.storage.BossStorage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class BossTemplateManager {
    private final BossStorage storage;
    private final Map<String, BossTemplate> plantillas;

    public BossTemplateManager(BossStorage storage) {
        this.storage = storage;
        this.plantillas = new LinkedHashMap<>(storage.cargar());
    }

    public List<BossTemplate> todas() {
        return List.copyOf(plantillas.values());
    }

    public Optional<BossTemplate> obtener(String id) {
        return Optional.ofNullable(plantillas.get(id));
    }

    public BossTemplate crear() {
        BossTemplate nuevo = new BossTemplate();
        plantillas.put(nuevo.getId(), nuevo);
        guardar();
        return nuevo;
    }

    /** Devuelve la plantilla que envuelve a ese mob de MythicMobs; si todavia no existe la crea. */
    public BossTemplate obtenerOCrearMythic(String mythicMobId, String nombre) {
        for (BossTemplate t : plantillas.values()) {
            if (mythicMobId.equalsIgnoreCase(t.getMythicMobId())) return t;
        }
        BossTemplate nuevo = new BossTemplate();
        nuevo.setMythicMobId(mythicMobId);
        nuevo.setNombre(nombre != null && !nombre.isBlank() ? nombre : mythicMobId);
        plantillas.put(nuevo.getId(), nuevo);
        guardar();
        return nuevo;
    }

    public void actualizar(BossTemplate template) {
        plantillas.put(template.getId(), template);
        guardar();
    }

    public void eliminar(String id) {
        plantillas.remove(id);
        guardar();
    }

    public void guardar() {
        storage.guardar(plantillas);
    }
}
