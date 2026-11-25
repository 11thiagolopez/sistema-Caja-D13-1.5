package com.thiago.escenasFX.dto;

import java.time.LocalDateTime;

/**
 * Datos de una foto sin el contenido: la imagen se pide aparte, por su id. {@code momento}: null =
 * foto del jefe/cliente; "ANTES" o "DESPUES" = foto del técnico.
 */
public record FotoTrabajoResponse(Integer idFoto, Integer idEmpleado, String nombreEmpleado, LocalDateTime creadaEn,
        String momento) {
}
