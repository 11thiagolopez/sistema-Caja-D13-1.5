package com.thiago.escenasFX.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.thiago.escenasFX.exception.AuthenticationFailedException;

/**
 * Throttling de fuerza bruta contra /api/auth/login: sin esto, no había ningún límite a la
 * cantidad de intentos de usuario/contraseña que se podían probar. Estado en memoria (no en
 * base) — se resetea si el backend se reinicia, aceptable para un solo proceso como este; no
 * pensado para múltiples instancias detrás de un balanceador.
 */
@Service
public class LoginAttemptService {

    private static final int MAX_INTENTOS = 5;
    private static final Duration VENTANA_BLOQUEO = Duration.ofMinutes(15);

    private record Intentos(int fallos, LocalDateTime bloqueadoHasta) {
    }

    private final ConcurrentHashMap<String, Intentos> porUsuario = new ConcurrentHashMap<>();

    void verificarNoBloqueado(String usuario) {
        Intentos actual = porUsuario.get(normalizar(usuario));
        if (actual != null && actual.bloqueadoHasta() != null) {
            if (actual.bloqueadoHasta().isAfter(LocalDateTime.now())) {
                throw new AuthenticationFailedException(
                    "Demasiados intentos fallidos; esperá unos minutos antes de volver a intentar");
            }
            porUsuario.remove(normalizar(usuario));
        }
    }

    void registrarFallo(String usuario) {
        porUsuario.compute(normalizar(usuario), (k, actual) -> {
            int fallos = (actual != null ? actual.fallos() : 0) + 1;
            LocalDateTime bloqueadoHasta = fallos >= MAX_INTENTOS ? LocalDateTime.now().plus(VENTANA_BLOQUEO) : null;
            return new Intentos(fallos, bloqueadoHasta);
        });
    }

    void registrarExito(String usuario) {
        porUsuario.remove(normalizar(usuario));
    }

    private String normalizar(String usuario) {
        return usuario == null ? "" : usuario.trim().toLowerCase();
    }
}
