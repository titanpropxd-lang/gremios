package com.bossforge.habilidades.impl;

import com.bossforge.habilidades.ContextoHabilidadBoss;
import com.bossforge.habilidades.HabilidadBossEjecutor;
import com.bossforge.model.HabilidadBossInstancia;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Skeleton;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public class InvocarEsqueletosArquerosHabilidad implements HabilidadBossEjecutor {
    @Override
    public void ejecutar(HabilidadBossInstancia instancia, ContextoHabilidadBoss ctx) {
        int cantidad = (int) Math.max(1, instancia.parametro("cantidad", 2));
        double radio = Math.max(1, instancia.parametro("radio", 6));
        LivingEntity boss = ctx.getBoss();

        for (int i = 0; i < cantidad; i++) {
            double angulo = Math.random() * Math.PI * 2;
            Vector desplazamiento = new Vector(Math.cos(angulo) * radio, 0, Math.sin(angulo) * radio);
            Location ubicacion = boss.getLocation().add(desplazamiento);
            ubicacion.setY(boss.getWorld().getHighestBlockYAt(ubicacion) + 1);

            var entidad = boss.getWorld().spawnEntity(ubicacion, EntityType.SKELETON);
            if (entidad instanceof Skeleton esqueleto) {
                esqueleto.getEquipment().setItemInMainHand(new ItemStack(org.bukkit.Material.BOW));
                esqueleto.setRemoveWhenFarAway(true);
            }
        }
    }
}
