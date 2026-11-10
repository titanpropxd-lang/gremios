package com.etheria.gremios.tech;

import com.etheria.gremios.tech.Tech.Branch;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Definición del árbol: 82 tecnologías + 8 de la rama Slimefun (opcional). */
public final class TechTree {
    private TechTree() {
    }

    public static final Map<String, Tech> ALL = new LinkedHashMap<>();

    private static void t(String id, String name, Branch b, int tier, Material icon, String desc, String reqs, String... eff) {
        List<String> r = new ArrayList<>();
        if (!reqs.isEmpty()) for (String s : reqs.split(",")) r.add(s.trim());
        Tech t = new Tech(id, name, b, tier, icon, desc, r);
        for (String e : eff) {
            String[] p = e.split("=");
            t.effects.put(p[0], Double.parseDouble(p[1]));
        }
        ALL.put(id, t);
    }

    static {
        Branch E = Branch.ECONOMIA, M = Branch.MILITAR, D = Branch.DEFENSA, A = Branch.AGRICULTURA, N = Branch.MINERIA,
                G = Branch.MAGIA, L = Branch.LOGISTICA, P = Branch.GOBIERNO, S = Branch.SLIMEFUN;

        // ---------------- Economía (11)
        t("eco_comercio", "Comercio Local", E, 1, Material.EMERALD, "Los primeros mercaderes llegan a vuestras puertas.", "", "interes=0.2");
        t("eco_contabilidad", "Contabilidad", E, 1, Material.WRITABLE_BOOK, "Llevar las cuentas ahorra dinero.", "", "mantenimiento=5");
        t("eco_mercado", "Mercado Central", E, 2, Material.CHEST, "Una plaza donde todo se compra y se vende.", "eco_comercio", "interes=0.3", "zona=5");
        t("eco_mercaderes", "Gremio de Mercaderes", E, 2, Material.GOLD_NUGGET, "Contratos mejores con proveedores.", "eco_contabilidad", "mantenimiento=7");
        t("eco_rutas", "Rutas Comerciales", E, 3, Material.MAP, "Caravanas que enriquecen vuestras zonas.", "eco_mercado", "zona=10");
        t("eco_banca", "Banca", E, 3, Material.GOLD_BLOCK, "Vuestro banco empieza a generar intereses.", "eco_mercado,eco_contabilidad", "interes=0.5");
        t("eco_fiscal", "Reforma Fiscal", E, 4, Material.PAPER, "Menos burocracia, menos gastos.", "eco_banca", "mantenimiento=10");
        t("eco_monopolio", "Monopolio", E, 4, Material.NETHER_STAR, "Controláis el comercio de la región.", "eco_rutas", "zona=15");
        t("eco_inversion", "Inversión Segura", E, 5, Material.DIAMOND, "El capital trabaja por vosotros.", "eco_banca,eco_fiscal", "interes=0.8");
        t("eco_imperio", "Imperio Comercial", E, 5, Material.ENDER_CHEST, "Vuestras mercancías llegan a todas partes.", "eco_monopolio", "zona=20", "mantenimiento=8");
        t("eco_tesoreria", "Tesorería Real", E, 6, Material.BEACON, "La cumbre de la economía del reino.", "eco_inversion,eco_imperio", "interes=1", "mantenimiento=10", "costo=10");

        // ---------------- Militar (12)
        t("mil_entrenamiento", "Entrenamiento Básico", M, 1, Material.WOODEN_SWORD, "Los reclutas aprenden a empuñar un arma.", "", "dmg-guerra=2");
        t("mil_armeria", "Armería", M, 1, Material.ANVIL, "Mejores armas para vuestros guerreros.", "", "dmg-guerra=2");
        t("mil_formacion", "Formación en Línea", M, 2, Material.IRON_SWORD, "Combatir juntos multiplica la fuerza.", "mil_entrenamiento", "dmg-guerra=3");
        t("mil_arqueria", "Arquería", M, 2, Material.BOW, "Arqueros veloces en vuestras tierras.", "mil_armeria", "dmg-guerra=2", "buff:SPEED=1");
        t("mil_arsenal", "Arsenal", M, 3, Material.CROSSBOW, "Almacenes llenos de munición.", "mil_armeria", "botin-guerra=10");
        t("mil_caballeria", "Caballería", M, 3, Material.SADDLE, "Jinetes rápidos y saltarines.", "mil_formacion,mil_arqueria", "dmg-guerra=4", "buff:JUMP_BOOST=1");
        t("mil_logistica", "Logística Militar", M, 3, Material.BARREL, "Podéis sostener más frentes a la vez.", "mil_formacion", "guerras=1");
        t("mil_asedio", "Ingeniería de Asedio", M, 4, Material.TNT, "Catapultas y arietes mejor diseñados.", "mil_caballeria", "botin-guerra=15");
        t("mil_veteranos", "Veteranos", M, 4, Material.NETHERITE_SCRAP, "Soldados curtidos en mil batallas.", "mil_logistica", "dmg-guerra=5", "def-terr=5");
        t("mil_elite", "Guardia de Élite", M, 5, Material.DIAMOND_SWORD, "Los mejores guerreros del gremio.", "mil_asedio,mil_veteranos", "dmg-guerra=6");
        t("mil_estrategia", "Gran Estrategia", M, 5, Material.FILLED_MAP, "Planificáis campañas enteras.", "mil_elite", "botin-guerra=20", "guerras=1");
        t("mil_conquista", "Señores de la Guerra", M, 6, Material.NETHERITE_SWORD, "Vuestro nombre infunde terror.", "mil_estrategia", "dmg-guerra=8", "botin-guerra=25");

        // ---------------- Defensa (10)
        t("def_empalizada", "Empalizada", D, 1, Material.OAK_FENCE, "Una valla de madera para empezar.", "", "def-terr=3");
        t("def_vigilancia", "Vigilancia", D, 1, Material.SPYGLASS, "Centinelas que ven en la oscuridad.", "", "buff:NIGHT_VISION=1");
        t("def_murallas", "Murallas de Piedra", D, 2, Material.STONE_BRICKS, "Muros que resisten el asedio.", "def_empalizada", "def-terr=4", "proteccion=10");
        t("def_atalayas", "Atalayas", D, 2, Material.LANTERN, "Torres de vigilancia en cada esquina.", "def_vigilancia", "proteccion=10");
        t("def_foso", "Foso y Trampas", D, 3, Material.COBWEB, "Un foso lleno de sorpresas.", "def_murallas", "def-terr=5");
        t("def_reparacion", "Cuerpo de Reparación", D, 3, Material.IRON_PICKAXE, "Albañiles que no descansan.", "def_murallas", "buff:ABSORPTION=1");
        t("def_fortaleza", "Fortaleza Inexpugnable", D, 4, Material.DEEPSLATE_BRICKS, "Una fortaleza para la historia.", "def_foso,def_atalayas", "def-terr=6", "proteccion=15");
        t("def_guarnicion", "Guarnición Permanente", D, 4, Material.IRON_CHESTPLATE, "Tropas siempre listas.", "def_reparacion", "buff:RESISTANCE=1");
        t("def_ciudadela", "Ciudadela", D, 5, Material.POLISHED_BLACKSTONE_BRICKS, "Una ciudad amurallada.", "def_fortaleza,def_guarnicion", "def-terr=8", "proteccion=20");
        t("def_eterno", "Bastión Eterno", D, 6, Material.OBSIDIAN, "Nadie ha conseguido tomarlo jamás.", "def_ciudadela", "def-terr=10", "proteccion=25", "buff:REGENERATION=1");

        // ---------------- Agricultura (11)
        t("agr_arado", "Arado de Hierro", A, 1, Material.IRON_HOE, "Los cultivos crecen mejor.", "", "cultivo=10");
        t("agr_rotacion", "Rotación de Cultivos", A, 1, Material.WHEAT_SEEDS, "La tierra descansa y rinde más.", "", "cultivo=10");
        t("agr_riego", "Riego", A, 2, Material.WATER_BUCKET, "Canales que llevan agua a los campos.", "agr_arado", "cultivo=10");
        t("agr_pesca", "Pesquerías", A, 2, Material.FISHING_ROD, "Redes y barcas para pescar.", "agr_rotacion", "zona=5");
        t("agr_ganaderia", "Ganadería", A, 2, Material.BEEF, "Corrales llenos de animales.", "agr_rotacion", "buff:SATURATION=1");
        t("agr_molinos", "Molinos", A, 3, Material.BREAD, "Molinos que multiplican la cosecha.", "agr_riego", "zona=5", "cultivo=10");
        t("agr_apicultura", "Apicultura", A, 3, Material.HONEYCOMB, "Miel que da salud.", "agr_ganaderia", "buff:HEALTH_BOOST=1");
        t("agr_invernaderos", "Invernaderos", A, 4, Material.GLASS, "Cosechas todo el año.", "agr_molinos", "cultivo=15");
        t("agr_festines", "Festines", A, 4, Material.CAKE, "Banquetes que unen al gremio.", "agr_apicultura", "miembros=2");
        t("agr_dorada", "Cosecha Dorada", A, 5, Material.GOLDEN_CARROT, "Rendimientos nunca vistos.", "agr_invernaderos,agr_festines", "cultivo=20", "zona=10");
        t("agr_granero", "Granero Real", A, 6, Material.HAY_BLOCK, "Abundancia para todo el reino.", "agr_dorada", "cultivo=25", "mantenimiento=5");

        // ---------------- Minería (10)
        t("min_picos", "Picos Reforzados", N, 1, Material.STONE_PICKAXE, "Excavar cuesta menos.", "", "buff:HASTE=1");
        t("min_prospeccion", "Prospección", N, 1, Material.COMPASS, "Sabéis dónde buscar.", "", "minerales=5");
        t("min_barrenos", "Barrenos", N, 2, Material.GUNPOWDER, "Voladuras controladas.", "min_picos", "minerales=5");
        t("min_fundicion", "Fundición", N, 2, Material.FURNACE, "Hornos más eficientes.", "min_prospeccion", "zona=5");
        t("min_galerias", "Galerías", N, 3, Material.RAIL, "Túneles seguros y amplios.", "min_barrenos", "buff:HASTE=2");
        t("min_veta", "Vetas Profundas", N, 3, Material.DEEPSLATE_DIAMOND_ORE, "Os adentráis en las vetas ricas.", "min_fundicion,min_prospeccion", "minerales=8");
        t("min_maquinaria", "Maquinaria", N, 4, Material.PISTON, "Máquinas que trabajan solas.", "min_galerias", "zona=10");
        t("min_refineria", "Refinería", N, 4, Material.BLAST_FURNACE, "Aprovecháis hasta la última pepita.", "min_veta", "minerales=10");
        t("min_industria", "Revolución Industrial", N, 5, Material.CRAFTER, "La era de las fábricas.", "min_maquinaria,min_refineria", "zona=15", "minerales=10");
        t("min_coloso", "Coloso Minero", N, 6, Material.NETHERITE_PICKAXE, "La mina más grande del mundo.", "min_industria", "zona=20", "minerales=15", "buff:HASTE=2");

        // ---------------- Magia (10)
        t("mag_estudio", "Estudios Arcanos", G, 1, Material.BOOK, "Primeras lecciones de magia.", "", "xp-orbes=10");
        t("mag_herbolaria", "Herbolaria", G, 1, Material.FERN, "Plantas con propiedades mágicas.", "", "buff:LUCK=1");
        t("mag_encantamiento", "Encantamiento", G, 2, Material.ENCHANTED_BOOK, "El arte de imbuir objetos.", "mag_estudio", "xp-orbes=15");
        t("mag_alquimia", "Alquimia", G, 2, Material.BREWING_STAND, "Pociones y transmutaciones.", "mag_herbolaria", "buff:WATER_BREATHING=1");
        t("mag_runas", "Runas", G, 3, Material.LAPIS_LAZULI, "Símbolos de poder antiguo.", "mag_encantamiento", "xp=5");
        t("mag_rituales", "Rituales", G, 3, Material.SOUL_LANTERN, "Ceremonias que protegen del fuego.", "mag_alquimia,mag_estudio", "buff:FIRE_RESISTANCE=1");
        t("mag_astral", "Magia Astral", G, 4, Material.AMETHYST_CLUSTER, "El poder de las estrellas.", "mag_runas", "xp=8", "xp-orbes=15");
        t("mag_invocacion", "Invocación", G, 4, Material.ENDER_EYE, "Llamar a fuerzas de otros planos.", "mag_rituales", "buff:SLOW_FALLING=1");
        t("mag_archimago", "Archimago", G, 5, Material.END_CRYSTAL, "Maestros de todas las artes arcanas.", "mag_astral,mag_invocacion", "xp=10", "xp-orbes=20");
        t("mag_trascendencia", "Trascendencia", G, 6, Material.DRAGON_EGG, "Habéis superado los límites de lo mortal.", "mag_archimago", "xp=15", "buff:CONDUIT_POWER=1");

        // ---------------- Logística (9)
        t("log_caminos", "Caminos Empedrados", L, 1, Material.DIRT_PATH, "Viajar es más rápido.", "", "buff:SPEED=1");
        t("log_mapas", "Cartografía", L, 1, Material.MAP, "Mapas detallados de la región.", "", "miembros=2");
        t("log_puentes", "Puentes", L, 2, Material.OAK_PLANKS, "Salvar ríos y barrancos.", "log_caminos", "buff:JUMP_BOOST=1");
        t("log_campamentos", "Campamentos", L, 2, Material.CAMPFIRE, "Más espacio para nuevos miembros.", "log_mapas", "miembros=3");
        t("log_navegacion", "Navegación", L, 3, Material.OAK_BOAT, "Dominio de los ríos y los mares.", "log_puentes", "buff:DOLPHINS_GRACE=1");
        t("log_correo", "Correo Real", L, 3, Material.ENDER_PEARL, "Mensajeros que cuestan menos.", "log_campamentos", "miembros=3", "costo=5");
        t("log_postas", "Postas", L, 4, Material.HAY_BLOCK, "Relevos de caballos por todo el reino.", "log_navegacion", "buff:SPEED=2");
        t("log_caravanas", "Caravanas", L, 4, Material.LEAD, "Convoyes que enriquecen vuestras zonas.", "log_correo", "zona=10");
        t("log_red", "Red Imperial", L, 5, Material.ELYTRA, "Una red de caminos que une todo.", "log_postas,log_caravanas", "miembros=5", "buff:SLOW_FALLING=1");

        // ---------------- Gobierno (9)
        t("gob_consejo", "Consejo", P, 1, Material.LECTERN, "Los líderes deliberan.", "", "costo=5");
        t("gob_heraldos", "Heraldos", P, 1, Material.GOAT_HORN, "Noticias que corren rápido.", "", "investigacion=5");
        t("gob_embajada", "Embajada", P, 2, Material.RED_BANNER, "Podéis aceptar vuestro primer vasallo.", "gob_consejo", "vasallos=1");
        t("gob_leyes", "Código de Leyes", P, 2, Material.KNOWLEDGE_BOOK, "Orden y menos gastos.", "gob_heraldos", "mantenimiento=5");
        t("gob_espias", "Red de Espías", P, 3, Material.PLAYER_HEAD, "Ojos en todas partes.", "gob_embajada", "tributo=10");
        t("gob_tratados", "Tratados", P, 3, Material.FEATHER, "Diplomáticos expertos.", "gob_leyes", "costo=5", "investigacion=5");
        t("gob_feudalismo", "Feudalismo", P, 4, Material.GOLDEN_HORSE_ARMOR, "Señores y vasallos.", "gob_espias", "vasallos=1", "tributo=15");
        t("gob_diplomacia", "Gran Diplomacia", P, 4, Material.WHITE_BANNER, "Investigáis dos cosas a la vez.", "gob_tratados", "slots=1");
        t("gob_corona", "La Corona", P, 6, Material.GOLDEN_HELMET, "Vuestro gremio gobierna de verdad.", "gob_feudalismo,gob_diplomacia", "vasallos=2", "tributo=25", "slots=1", "investigacion=10");

        // ---------------- Slimefun (8, opcional)
        t("slf_fundamentos", "Fundamentos de Slimefun", S, 1, Material.SLIME_BALL, "Desbloquea para todo el gremio las investigaciones básicas.", "", "zona=3");
        t("slf_metalurgia", "Metalurgia", S, 2, Material.IRON_INGOT, "Moliendas, fundiciones y bateo de oro.", "slf_fundamentos", "zona=5");
        t("slf_armaduras", "Armaduras Especiales", S, 2, Material.GOLDEN_CHESTPLATE, "Armaduras y vidrio endurecido.", "slf_fundamentos", "def-terr=3");
        t("slf_mecanica", "Mecánica", S, 3, Material.PISTON, "Hachas, bancos mágicos y utilidades.", "slf_metalurgia", "zona=8");
        t("slf_energia", "Energía", S, 4, Material.REDSTONE_BLOCK, "Generadores y redes de energía.", "slf_mecanica,slf_armaduras", "zona=10");
        t("slf_automatizacion", "Automatización", S, 5, Material.HOPPER, "Máquinas que trabajan por ti.", "slf_energia", "zona=15", "mantenimiento=5");
        t("slf_ingenieria", "Ingeniería Avanzada", S, 5, Material.COMPARATOR, "Tecnología de punta.", "slf_energia", "minerales=10");
        t("slf_maestria", "Maestría Slimefun", S, 6, Material.NETHER_STAR, "La cima de la tecnología.", "slf_automatizacion,slf_ingenieria", "zona=20", "xp=10");
    }
}
