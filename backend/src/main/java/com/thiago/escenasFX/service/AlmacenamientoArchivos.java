package com.thiago.escenasFX.service;

import java.io.InputStream;
import java.time.Duration;

/**
 * Dónde viven los archivos adjuntos (videos y audios). En producción, el Railway Storage Bucket
 * ({@link S3AlmacenamientoArchivos}); en el perfil demo y en los tests, memoria
 * ({@link MemoriaAlmacenamientoArchivos}). Se elige con {@code archivos.almacenamiento} (s3 | memoria).
 */
public interface AlmacenamientoArchivos {

    void guardar(String clave, InputStream contenido, long tamanioBytes, String tipoContenido);

    /** Link para que el navegador reproduzca el archivo directo, válido durante {@code vigencia}. */
    String urlTemporal(String clave, Duration vigencia);

    void borrar(String clave);
}
