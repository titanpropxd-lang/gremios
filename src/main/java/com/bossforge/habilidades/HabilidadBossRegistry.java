package com.bossforge.habilidades;

import com.bossforge.boss.BossEscudoManager;
import com.bossforge.habilidades.impl.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Catalogo fijo de habilidades que un admin puede asignar a un
 * BossTemplate desde la GUI. Anadir una habilidad nueva al juego es
 * SOLO: crear su clase en habilidades/impl y registrarla aqui — la
 * GUI, la persistencia y el motor de ejecucion la reconocen solos.
 */
public class HabilidadBossRegistry {
    private final Map<String, HabilidadCatalogoEntry> catalogo = new LinkedHashMap<>();

    public HabilidadBossRegistry(BossEscudoManager escudos) {
        registrar("invocar_mobs", "Invocar mobs", "Invoca zombis alrededor del jefe.",
            List.of(
                new ParametroDefinicion("cantidad", "Cantidad", 3, 1, 20),
                new ParametroDefinicion("radio", "Radio", 5, 1, 30)
            ),
            new InvocarMobsHabilidad());

        registrar("ataque_area", "Ataque en area", "Dano a todos los jugadores dentro de un radio.",
            List.of(
                new ParametroDefinicion("radio", "Radio", 4, 1, 30),
                new ParametroDefinicion("dano", "Dano", 6, 0, 100)
            ),
            new AtaqueAreaHabilidad());

        registrar("teletransporte_jugador", "Teletransporte", "Se teletransporta cerca del jugador mas cercano.",
            List.of(new ParametroDefinicion("distanciaMax", "Distancia maxima", 20, 1, 100)),
            new TeletransporteAJugadorHabilidad());

        registrar("curarse", "Curarse", "Recupera un porcentaje de su vida maxima.",
            List.of(new ParametroDefinicion("porcentaje", "Porcentaje (%)", 10, 1, 100)),
            new CurarseHabilidad());

        registrar("proyectil_fuego", "Proyectil de fuego", "Dispara una bola de fuego al jugador mas cercano.",
            List.of(
                new ParametroDefinicion("potenciaExplosion", "Potencia explosion", 1, 0, 4),
                new ParametroDefinicion("incendiario", "Incendiario (1=si, 0=no)", 1, 0, 1)
            ),
            new ProyectilFuegoHabilidad());

        registrar("velocidad_temporal", "Velocidad temporal", "Aumenta su velocidad por un tiempo.",
            List.of(
                new ParametroDefinicion("duracionSegundos", "Duracion (s)", 8, 1, 120),
                new ParametroDefinicion("nivel", "Nivel", 1, 0, 4)
            ),
            new VelocidadTemporalBossHabilidad());

        registrar("empujar_cercanos", "Empujar cercanos", "Onda expansiva que aleja a los jugadores cercanos.",
            List.of(
                new ParametroDefinicion("radio", "Radio", 5, 1, 20),
                new ParametroDefinicion("fuerza", "Fuerza", 1.5, 0.1, 5)
            ),
            new EmpujarCercanosHabilidad());

        registrar("invocar_rayo", "Invocar rayo", "Cae un rayo sobre un jugador aleatorio cercano.",
            List.of(new ParametroDefinicion("dañino", "Hace dano (1=si, 0=no)", 1, 0, 1)),
            new InvocarRayoHabilidad());

        registrar("escudo_temporal", "Escudo temporal", "Reduce el dano que recibe por un tiempo.",
            List.of(
                new ParametroDefinicion("porcentajeReduccion", "Reduccion (%)", 50, 1, 100),
                new ParametroDefinicion("duracionSegundos", "Duracion (s)", 5, 1, 60)
            ),
            new EscudoTemporalBossHabilidad(escudos));

        registrar("nube_veneno", "Nube de veneno", "Deja una nube que envenena a los jugadores dentro del radio.",
            List.of(
                new ParametroDefinicion("radio", "Radio", 4, 1, 20),
                new ParametroDefinicion("duracionSegundos", "Duracion (s)", 6, 1, 60)
            ),
            new NubeVenenoHabilidad());

        registrar("congelar_area", "Congelar area", "Ralentiza y atonta a los jugadores dentro del radio.",
            List.of(
                new ParametroDefinicion("radio", "Radio", 5, 1, 20),
                new ParametroDefinicion("duracionSegundos", "Duracion (s)", 5, 1, 60),
                new ParametroDefinicion("nivel", "Nivel", 2, 0, 4)
            ),
            new CongelarAreaHabilidad());

        registrar("ceguera_area", "Ceguera en area", "Ciega y marea a los jugadores dentro del radio.",
            List.of(
                new ParametroDefinicion("radio", "Radio", 6, 1, 20),
                new ParametroDefinicion("duracionSegundos", "Duracion (s)", 4, 1, 30)
            ),
            new CegueraAreaHabilidad());

        registrar("invocar_esqueletos", "Invocar esqueletos", "Invoca esqueletos arqueros alrededor del jefe.",
            List.of(
                new ParametroDefinicion("cantidad", "Cantidad", 2, 1, 10),
                new ParametroDefinicion("radio", "Radio", 6, 1, 30)
            ),
            new InvocarEsqueletosArquerosHabilidad());

        registrar("furia", "Furia", "Aumenta temporalmente su fuerza de ataque. Ideal para una segunda fase.",
            List.of(
                new ParametroDefinicion("duracionSegundos", "Duracion (s)", 10, 1, 120),
                new ParametroDefinicion("nivel", "Nivel", 1, 0, 4)
            ),
            new FuriaHabilidad());
    }

    private void registrar(String id, String nombre, String descripcion, List<ParametroDefinicion> parametros, HabilidadBossEjecutor ejecutor) {
        catalogo.put(id, new HabilidadCatalogoEntry(id, nombre, descripcion, parametros, ejecutor));
    }

    public Optional<HabilidadCatalogoEntry> obtener(String id) {
        return Optional.ofNullable(catalogo.get(id));
    }

    public List<HabilidadCatalogoEntry> todas() {
        return List.copyOf(catalogo.values());
    }
}
