package com.thiago.escenasFX.controller;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import com.thiago.escenasFX.model.FotoTrabajo;

/** Lo común a las fotos de trabajos en los dos controllers (jefe y técnico). */
final class FotosHttp {

    private FotosHttp() {
    }

    static List<byte[]> leer(List<MultipartFile> archivos) {
        return archivos.stream().map(archivo -> {
            try {
                return archivo.getBytes();
            } catch (IOException e) {
                throw new UncheckedIOException("No se pudo leer la foto subida", e);
            }
        }).toList();
    }

    // Una foto nunca cambia (no hay edición, solo alta y baja), así que el navegador la puede
    // guardar en su caché privada sin volver a pedirla cada vez que se abre el trabajo.
    static ResponseEntity<byte[]> responder(FotoTrabajo foto) {
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(foto.getTipoContenido()))
            .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate())
            .body(foto.getContenido());
    }
}
