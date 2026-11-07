package com.bossforge.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Permite pedirle a un jugador que escriba algo por chat y recibirlo
 * como callback, en vez de construir un GUI de texto (Minecraft no
 * tiene uno nativo salvo el yunque, limitado a un formato de texto).
 * Usado para renombrar obeliscos/jefes desde la GUI.
 */
public class EntradaTextoManager {
    private record Solicitud(String promptDescripcion, Consumer<String> alRecibir) { }

    private final Map<UUID, Solicitud> pendientes = new ConcurrentHashMap<>();

    public void solicitar(UUID jugador, String descripcionPrompt, Consumer<String> alRecibir) {
        pendientes.put(jugador, new Solicitud(descripcionPrompt, alRecibir));
    }

    public boolean tieneSolicitudPendiente(UUID jugador) {
        return pendientes.containsKey(jugador);
    }

    public String descripcionPendiente(UUID jugador) {
        Solicitud s = pendientes.get(jugador);
        return s != null ? s.promptDescripcion() : null;
    }

    /** Consume la solicitud pendiente (si la hay) y ejecuta su callback con el texto recibido. */
    public boolean resolver(UUID jugador, String texto) {
        Solicitud s = pendientes.remove(jugador);
        if (s == null) return false;
        s.alRecibir().accept(texto);
        return true;
    }

    public void cancelar(UUID jugador) {
        pendientes.remove(jugador);
    }
}
