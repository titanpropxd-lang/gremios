package com.bossforge;

import com.bossforge.boss.BossManager;
import com.bossforge.boss.BossTemplateManager;
import com.bossforge.habilidades.HabilidadBossRegistry;
import com.bossforge.integration.MythicHook;
import com.bossforge.obelisk.ObeliskManager;
import com.bossforge.obelisk.VinculacionPendienteManager;
import com.bossforge.util.EntradaTextoManager;
import org.bukkit.plugin.java.JavaPlugin;

/** Agrupa todos los servicios del plugin para pasarlos de una vez a cada GUI/listener. */
public class BossForgeContext {
    public final JavaPlugin plugin;
    public final ObeliskManager obeliskManager;
    public final BossTemplateManager bossTemplateManager;
    public final BossManager bossManager;
    public final HabilidadBossRegistry habilidadRegistry;
    public final EntradaTextoManager entradaTexto;
    public final VinculacionPendienteManager vinculacionPendiente;
    public final MythicHook mythic;

    public BossForgeContext(JavaPlugin plugin, ObeliskManager obeliskManager, BossTemplateManager bossTemplateManager,
                             BossManager bossManager, HabilidadBossRegistry habilidadRegistry,
                             EntradaTextoManager entradaTexto, VinculacionPendienteManager vinculacionPendiente, MythicHook mythic) {
        this.plugin = plugin;
        this.obeliskManager = obeliskManager;
        this.bossTemplateManager = bossTemplateManager;
        this.bossManager = bossManager;
        this.habilidadRegistry = habilidadRegistry;
        this.entradaTexto = entradaTexto;
        this.vinculacionPendiente = vinculacionPendiente;
        this.mythic = mythic;
    }
}
