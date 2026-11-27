package com.thiago.escenasFX.dto;

import java.time.LocalDateTime;

/**
 * Datos de un video o audio adjunto, sin el archivo: para reproducirlo se pide un link temporal aparte
 * ({@link UrlArchivoResponse}). {@code tipoContenido} empieza con "video/" o "audio/". {@code momento}:
 * null = adjunto; "ANTES"/"DESPUES" = comprobante del técnico.
 */
public record ArchivoTrabajoResponse(Integer idArchivo, Integer idEmpleado, String nombreEmpleado,
        LocalDateTime creadaEn, String momento, Long tamanioBytes, String tipoContenido) {
}
