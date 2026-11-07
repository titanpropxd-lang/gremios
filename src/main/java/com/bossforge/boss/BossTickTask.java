package com.bossforge.boss;

import org.bukkit.scheduler.BukkitRunnable;

public class BossTickTask extends BukkitRunnable {
    private final BossManager bossManager;

    public BossTickTask(BossManager bossManager) {
        this.bossManager = bossManager;
    }

    @Override
    public void run() {
        bossManager.tick();
    }
}
