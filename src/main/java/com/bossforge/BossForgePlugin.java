package com.bossforge;

import com.bossforge.boss.BossEscudoManager;
import com.bossforge.boss.BossManager;
import com.bossforge.boss.BossTemplateManager;
import com.bossforge.boss.BossTickTask;
import com.bossforge.commands.BossForgeCommand;
import com.bossforge.habilidades.HabilidadBossRegistry;
import com.bossforge.integration.MythicHook;
import com.bossforge.listeners.ArenaListener;
import com.bossforge.listeners.BossCombateListener;
import com.bossforge.listeners.ChatInputListener;
import com.bossforge.listeners.GuiListener;
import com.bossforge.listeners.ObeliskInteractListener;
import com.bossforge.obelisk.ObeliskBeaconTask;
import com.bossforge.obelisk.ObeliskManager;
import com.bossforge.obelisk.VinculacionPendienteManager;
import com.bossforge.storage.BossStorage;
import com.bossforge.storage.ObeliskStorage;
import com.bossforge.util.EntradaTextoManager;
import org.bukkit.plugin.java.JavaPlugin;

public class BossForgePlugin extends JavaPlugin {
    private BossTickTask bossTickTask;
    private ObeliskBeaconTask obeliskBeaconTask;
    private BossManager bossManager;
    private ObeliskManager obeliskManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        BossEscudoManager escudoManager = new BossEscudoManager();
        HabilidadBossRegistry habilidadRegistry = new HabilidadBossRegistry(escudoManager);

        MythicHook mythicHook = new MythicHook(this);

        BossStorage bossStorage = new BossStorage(this);
        BossTemplateManager bossTemplateManager = new BossTemplateManager(bossStorage);

        this.bossManager = new BossManager(this, habilidadRegistry, escudoManager, mythicHook, bossTemplateManager);

        ObeliskStorage obeliskStorage = new ObeliskStorage(this);
        this.obeliskManager = new ObeliskManager(this, obeliskStorage, bossTemplateManager, bossManager, mythicHook);
        bossManager.setObeliskManager(obeliskManager);

        EntradaTextoManager entradaTexto = new EntradaTextoManager();
        VinculacionPendienteManager vinculacionPendiente = new VinculacionPendienteManager();

        BossForgeContext ctx = new BossForgeContext(this, obeliskManager, bossTemplateManager, bossManager,
            habilidadRegistry, entradaTexto, vinculacionPendiente, mythicHook);

        getServer().getPluginManager().registerEvents(new GuiListener(), this);
        getServer().getPluginManager().registerEvents(new ObeliskInteractListener(ctx), this);
        getServer().getPluginManager().registerEvents(new ChatInputListener(ctx), this);
        getServer().getPluginManager().registerEvents(new BossCombateListener(bossManager, this), this);
        getServer().getPluginManager().registerEvents(new ArenaListener(this, obeliskManager), this);

        var comando = getCommand("bossforge");
        if (comando != null) comando.setExecutor(new BossForgeCommand(ctx));

        long intervaloTicks = Math.max(1, getConfig().getInt("jefes.intervalo_revision_habilidades_ticks", 20));
        this.bossTickTask = new BossTickTask(bossManager);
        bossTickTask.runTaskTimer(this, intervaloTicks, intervaloTicks);

        this.obeliskBeaconTask = new ObeliskBeaconTask(obeliskManager);
        obeliskBeaconTask.runTaskTimer(this, 10L, 10L);

        getLogger().info("[BossForge] Plugin activado (" + obeliskManager.todos().size() + " obelisco(s) cargado(s)).");
    }

    @Override
    public void onDisable() {
        if (bossTickTask != null) bossTickTask.cancel();
        if (obeliskBeaconTask != null) obeliskBeaconTask.cancel();
        if (obeliskManager != null) obeliskManager.cancelarInvocaciones();
        if (bossManager != null) bossManager.cancelarTodosLosCombates();
        if (obeliskManager != null) obeliskManager.guardarTodo();
        getLogger().info("[BossForge] Plugin desactivado limpiamente.");
    }
}
