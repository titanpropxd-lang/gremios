package com.bossforge.storage;

import com.bossforge.model.BossTemplate;
import com.bossforge.model.HabilidadBossInstancia;
import com.bossforge.model.TipoDisparoHabilidad;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Guarda y carga bosses.yml. Cada BossTemplate se serializa a mano
 * (campo por campo) en vez de depender de serializacion automatica,
 * para tener control total sobre el formato y poder evolucionarlo
 * sin romper archivos ya guardados.
 */
public class BossStorage {
    private final File archivo;
    private final Logger logger;

    public BossStorage(JavaPlugin plugin) {
        this.archivo = new File(plugin.getDataFolder(), "bosses.yml");
        this.logger = plugin.getLogger();
    }

    public Map<String, BossTemplate> cargar() {
        Map<String, BossTemplate> resultado = new LinkedHashMap<>();
        if (!archivo.exists()) return resultado;

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(archivo);
        ConfigurationSection raiz = cfg.getConfigurationSection("bosses");
        if (raiz == null) return resultado;

        for (String id : raiz.getKeys(false)) {
            try {
                ConfigurationSection seccion = raiz.getConfigurationSection(id);
                if (seccion == null) continue;
                BossTemplate boss = new BossTemplate();
                boss.setId(id);
                boss.setNombre(seccion.getString("nombre", "Jefe sin nombre"));
                boss.setTipoBase(EntityType.valueOf(seccion.getString("tipoBase", "ZOMBIE")));
                boss.setVidaMaxima(seccion.getDouble("vidaMaxima", 200));
                boss.setDanoAtaque(seccion.getDouble("danoAtaque", 10));
                boss.setMythicMobId(seccion.getString("mythicMobId", null));
                boss.setNumeroFases(seccion.getInt("numeroFases", 1));
                boss.setPorcentajeVidaCambioFase(seccion.getDouble("porcentajeVidaCambioFase", 50));

                List<HabilidadBossInstancia> habilidades = new ArrayList<>();
                List<Map<?, ?>> listaHabilidades = seccion.getMapList("habilidades");
                for (Map<?, ?> mapa : listaHabilidades) {
                    HabilidadBossInstancia h = new HabilidadBossInstancia();
                    h.setHabilidadId(String.valueOf(mapa.get("habilidadId")));
                    Object tipoDisparoCrudo = mapa.get("tipoDisparo");
                    String tipoDisparoTexto = (tipoDisparoCrudo != null) ? String.valueOf(tipoDisparoCrudo) : "PERIODICA";
                    h.setTipoDisparo(TipoDisparoHabilidad.valueOf(tipoDisparoTexto));
                    h.setCooldownSegundos(numero(mapa.get("cooldownSegundos"), 10));
                    h.setPorcentajeVidaUmbral(numero(mapa.get("porcentajeVidaUmbral"), 50));
                    h.setFase((int) numero(mapa.get("fase"), 0));
                    Map<String, Double> parametros = new HashMap<>();
                    Object parametrosCrudos = mapa.get("parametros");
                    if (parametrosCrudos instanceof Map<?, ?> mapaParametros) {
                        for (var entrada : mapaParametros.entrySet()) {
                            parametros.put(String.valueOf(entrada.getKey()), numero(entrada.getValue(), 0));
                        }
                    }
                    h.setParametros(parametros);
                    habilidades.add(h);
                }
                boss.setHabilidades(habilidades);

                resultado.put(id, boss);
            } catch (Exception e) {
                logger.warning("[BossForge] No se pudo cargar el boss '" + id + "' de bosses.yml, se omite: " + e.getMessage());
            }
        }
        return resultado;
    }

    public void guardar(Map<String, BossTemplate> bosses) {
        YamlConfiguration cfg = new YamlConfiguration();
        for (BossTemplate boss : bosses.values()) {
            String base = "bosses." + boss.getId() + ".";
            cfg.set(base + "nombre", boss.getNombre());
            cfg.set(base + "tipoBase", boss.getTipoBase().name());
            cfg.set(base + "vidaMaxima", boss.getVidaMaxima());
            cfg.set(base + "danoAtaque", boss.getDanoAtaque());
            cfg.set(base + "mythicMobId", boss.getMythicMobId());
            cfg.set(base + "numeroFases", boss.getNumeroFases());
            cfg.set(base + "porcentajeVidaCambioFase", boss.getPorcentajeVidaCambioFase());

            List<Map<String, Object>> listaHabilidades = new ArrayList<>();
            for (HabilidadBossInstancia h : boss.getHabilidades()) {
                Map<String, Object> mapa = new LinkedHashMap<>();
                mapa.put("habilidadId", h.getHabilidadId());
                mapa.put("tipoDisparo", h.getTipoDisparo().name());
                mapa.put("cooldownSegundos", h.getCooldownSegundos());
                mapa.put("porcentajeVidaUmbral", h.getPorcentajeVidaUmbral());
                mapa.put("fase", h.getFase());
                mapa.put("parametros", h.getParametros());
                listaHabilidades.add(mapa);
            }
            cfg.set(base + "habilidades", listaHabilidades);
        }

        try {
            archivo.getParentFile().mkdirs();
            cfg.save(archivo);
        } catch (IOException e) {
            logger.severe("[BossForge] No se pudo guardar bosses.yml: " + e.getMessage());
        }
    }

    private double numero(Object valor, double porDefecto) {
        if (valor instanceof Number n) return n.doubleValue();
        return porDefecto;
    }
}
