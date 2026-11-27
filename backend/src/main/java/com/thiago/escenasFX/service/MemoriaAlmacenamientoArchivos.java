package com.thiago.escenasFX.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Adjuntos en memoria, para el perfil demo y los tests (sin bucket real). Se pierden al reiniciar.
 * El "link temporal" es un data: URL con el archivo entero: sirve para probar, no para producción.
 */
@Service
@ConditionalOnProperty(name = "archivos.almacenamiento", havingValue = "memoria")
public class MemoriaAlmacenamientoArchivos implements AlmacenamientoArchivos {

    private record Guardado(byte[] bytes, String tipo) {
    }

    private final Map<String, Guardado> archivos = new ConcurrentHashMap<>();

    @Override
    public void guardar(String clave, InputStream contenido, long tamanioBytes, String tipoContenido) {
        try {
            archivos.put(clave, new Guardado(contenido.readAllBytes(), tipoContenido));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String urlTemporal(String clave, Duration vigencia) {
        Guardado g = archivos.get(clave);
        if (g == null) {
            throw new IllegalArgumentException("El archivo ya no existe");
        }
        return "data:" + g.tipo() + ";base64," + Base64.getEncoder().encodeToString(g.bytes());
    }

    @Override
    public void borrar(String clave) {
        archivos.remove(clave);
    }

    /** Para los tests: cuántos archivos quedaron guardados. */
    public int cantidad() {
        return archivos.size();
    }
}
