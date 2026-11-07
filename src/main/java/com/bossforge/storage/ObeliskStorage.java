package com.bossforge.storage;

import com.bossforge.model.EstadoObelisco;
import com.bossforge.model.LootEntry;
import com.bossforge.model.Obelisco;
import com.bossforge.model.OpcionJefe;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class ObeliskStorage {
    private final File archivo;
    private final Logger logger;

    public ObeliskStorage(JavaPlugin plugin) {
        this.archivo = new File(plugin.getDataFolder(), "obelisks.yml");
        this.logger = plugin.getLogger();
    }

    public Map<String, Obelisco> cargar() {
        Map<String, Obelisco> resultado = new LinkedHashMap<>();
        if (!archivo.exists()) return resultado;

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(archivo);
        ConfigurationSection raiz = cfg.getConfigurationSection("obeliscos");
        if (raiz == null) return resultado;

        for (String id : raiz.getKeys(false)) {
            try {
                ConfigurationSection s = raiz.getConfigurationSection(id);
                if (s == null) continue;

                Obelisco obelisco = new Obelisco();
                obelisco.setId(id);
                obelisco.setNombre(s.getString("nombre", "Obelisco"));
                obelisco.setUbicacion(s.getLocation("ubicacion"));
                obelisco.setArenaPos1(s.getLocation("arenaPos1"));
                obelisco.setArenaPos2(s.getLocation("arenaPos2"));
                obelisco.setArenaSpawn(s.getLocation("arenaSpawn"));
                obelisco.setJugadorSpawn(s.getLocation("jugadorSpawn"));
                obelisco.setJugadorSpawnRadio(s.getDouble("jugadorSpawnRadio", 2));
                obelisco.setColorFaro(org.bukkit.Color.fromRGB(
                    s.getInt("colorFaro.r", 255), s.getInt("colorFaro.g", 255), s.getInt("colorFaro.b", 255)));
                obelisco.setFaroActivo(s.getBoolean("faroActivo", true));
                obelisco.setRadioConvocatoria(s.getDouble("radioConvocatoria", 10));
                obelisco.setActivo(s.getBoolean("activo", true));
                obelisco.setEstado(EstadoObelisco.valueOf(s.getString("estado", "ESPERANDO")));
                obelisco.setUltimaVezCompletadoEpochMillis(s.getLong("ultimaVezCompletadoEpochMillis", 0));

                List<OpcionJefe> opciones = new ArrayList<>();
                ConfigurationSection seccionOpciones = s.getConfigurationSection("opciones");
                if (seccionOpciones != null) {
                    for (String idOpcion : seccionOpciones.getKeys(false)) {
                        ConfigurationSection so = seccionOpciones.getConfigurationSection(idOpcion);
                        if (so == null) continue;
                        OpcionJefe opcion = new OpcionJefe(so.getString("bossTemplateId", null));
                        opcion.setId(idOpcion);
                        opcion.setRequisitos(leerRequisitos(so));
                        opcion.setLoot(leerLoot(so));
                        opciones.add(opcion);
                    }
                } else {
                    // Formato anterior: requisitos y loot del obelisco + uno o varios jefes.
                    // Cada jefe pasa a ser una opcion con una copia de esos requisitos y ese loot.
                    List<String> idsJefes = new ArrayList<>(s.getStringList("bossTemplateIds"));
                    String idViejo = s.getString("bossTemplateId", null);
                    if (idsJefes.isEmpty() && idViejo != null) idsJefes.add(idViejo);
                    for (String idJefe : idsJefes) {
                        OpcionJefe opcion = new OpcionJefe(idJefe);
                        opcion.setRequisitos(leerRequisitos(s));
                        opcion.setLoot(leerLoot(s));
                        opciones.add(opcion);
                    }
                }
                obelisco.setOpciones(opciones);

                resultado.put(id, obelisco);
            } catch (Exception e) {
                logger.warning("[BossForge] No se pudo cargar el obelisco '" + id + "' de obelisks.yml, se omite: " + e.getMessage());
            }
        }
        return resultado;
    }

    public void guardar(Map<String, Obelisco> obeliscos) {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Obelisco o : obeliscos.values()) {
            String base = "obeliscos." + o.getId() + ".";
            cfg.set(base + "nombre", o.getNombre());
            setLocationSiNoEsNula(cfg, base + "ubicacion", o.getUbicacion());
            setLocationSiNoEsNula(cfg, base + "arenaPos1", o.getArenaPos1());
            setLocationSiNoEsNula(cfg, base + "arenaPos2", o.getArenaPos2());
            setLocationSiNoEsNula(cfg, base + "arenaSpawn", o.getArenaSpawn());
            setLocationSiNoEsNula(cfg, base + "jugadorSpawn", o.getJugadorSpawn());
            cfg.set(base + "jugadorSpawnRadio", o.getJugadorSpawnRadio());
            cfg.set(base + "colorFaro.r", o.getColorFaro().getRed());
            cfg.set(base + "colorFaro.g", o.getColorFaro().getGreen());
            cfg.set(base + "colorFaro.b", o.getColorFaro().getBlue());
            cfg.set(base + "faroActivo", o.isFaroActivo());
            cfg.set(base + "radioConvocatoria", o.getRadioConvocatoria());
            cfg.set(base + "activo", o.isActivo());
            cfg.set(base + "estado", o.getEstado().name());
            cfg.set(base + "ultimaVezCompletadoEpochMillis", o.getUltimaVezCompletadoEpochMillis());

            for (OpcionJefe opcion : o.getOpciones()) {
                String baseOpcion = base + "opciones." + opcion.getId() + ".";
                cfg.set(baseOpcion + "bossTemplateId", opcion.getBossTemplateId());
                cfg.set(baseOpcion + "requisitos", new ArrayList<>(opcion.getRequisitos()));

                List<Map<String, Object>> loot = new ArrayList<>();
                for (LootEntry entrada : opcion.getLoot()) {
                    Map<String, Object> mapa = new LinkedHashMap<>();
                    mapa.put("item", entrada.getItem());
                    mapa.put("probabilidad", entrada.getProbabilidad());
                    loot.add(mapa);
                }
                cfg.set(baseOpcion + "loot", loot);
            }
        }

        try {
            archivo.getParentFile().mkdirs();
            cfg.save(archivo);
        } catch (IOException e) {
            logger.severe("[BossForge] No se pudo guardar obelisks.yml: " + e.getMessage());
        }
    }

    private List<ItemStack> leerRequisitos(ConfigurationSection seccion) {
        List<ItemStack> requisitos = new ArrayList<>();
        for (Object o : seccion.getList("requisitos", new ArrayList<>())) {
            if (o instanceof ItemStack item) requisitos.add(item);
        }
        return requisitos;
    }

    private List<LootEntry> leerLoot(ConfigurationSection seccion) {
        List<LootEntry> loot = new ArrayList<>();
        for (Map<?, ?> mapa : seccion.getMapList("loot")) {
            Object itemCrudo = mapa.get("item");
            if (!(itemCrudo instanceof ItemStack item)) continue;
            double probabilidad = mapa.get("probabilidad") instanceof Number n ? n.doubleValue() : 100;
            loot.add(new LootEntry(item, probabilidad));
        }
        return loot;
    }

    private void setLocationSiNoEsNula(YamlConfiguration cfg, String path, Location location) {
        if (location != null) cfg.set(path, location);
    }
}
