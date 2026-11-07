package com.bossforge.util;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Toda la logica de "¿este item cumple el requisito?" vive aqui, en
 * un solo sitio. Se apoya en ItemStack#isSimilar(ItemStack), que
 * Bukkit ya implementa comparando material + TODO el ItemMeta
 * (nombre personalizado, lore, encantamientos, custom model data,
 * etc.) ignorando solo la cantidad — exactamente el comportamiento
 * pedido: un item editado a mano (con nombre/encantamiento) en el
 * cofre de configuracion NUNCA lo satisface un item vanilla igual
 * pero sin esos datos, porque sus ItemMeta ya no son iguales.
 */
public class ItemMatchUtil {
    private ItemMatchUtil() { }

    public static boolean esCoincidencia(ItemStack depositado, ItemStack requerido) {
        if (depositado == null || requerido == null) return false;
        return depositado.isSimilar(requerido);
    }

    public static int cantidadDisponible(ItemStack[] contenido, ItemStack requerido) {
        int total = 0;
        for (ItemStack item : contenido) {
            if (esCoincidencia(item, requerido)) total += item.getAmount();
        }
        return total;
    }

    public static boolean tieneSuficiente(ItemStack[] contenido, ItemStack requerido) {
        return cantidadDisponible(contenido, requerido) >= requerido.getAmount();
    }

    /** Devuelve los requisitos que AUN faltan (total o parcialmente) en el contenido dado. */
    public static List<ItemStack> requisitosFaltantes(ItemStack[] contenido, List<ItemStack> requisitos) {
        List<ItemStack> faltantes = new ArrayList<>();
        for (ItemStack requerido : requisitos) {
            if (!tieneSuficiente(contenido, requerido)) faltantes.add(requerido);
        }
        return faltantes;
    }

    public static boolean cumpleTodosLosRequisitos(ItemStack[] contenido, List<ItemStack> requisitos) {
        return requisitosFaltantes(contenido, requisitos).isEmpty();
    }

    /**
     * Retira de 'inventarioDeposito' exactamente las cantidades
     * requeridas (nunca de mas). Asume que cumpleTodosLosRequisitos ya
     * devolvio true — llamarlo sin esa comprobacion previa puede dejar
     * el inventario a medio consumir.
     */
    public static void consumirRequisitos(Inventory inventarioDeposito, List<ItemStack> requisitos) {
        for (ItemStack requerido : requisitos) {
            int restante = requerido.getAmount();
            ItemStack[] contenido = inventarioDeposito.getContents();
            for (int i = 0; i < contenido.length && restante > 0; i++) {
                ItemStack actual = contenido[i];
                if (!esCoincidencia(actual, requerido)) continue;

                int aTomar = Math.min(restante, actual.getAmount());
                int nuevaCantidad = actual.getAmount() - aTomar;
                inventarioDeposito.setItem(i, nuevaCantidad <= 0 ? null : conCantidad(actual, nuevaCantidad));
                restante -= aTomar;
            }
        }
    }

    private static ItemStack conCantidad(ItemStack original, int cantidad) {
        ItemStack copia = original.clone();
        copia.setAmount(cantidad);
        return copia;
    }
}
