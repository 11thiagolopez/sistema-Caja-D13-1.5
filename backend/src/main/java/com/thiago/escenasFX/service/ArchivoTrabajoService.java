package com.thiago.escenasFX.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.thiago.escenasFX.dto.ArchivoTrabajoResponse;
import com.thiago.escenasFX.model.ArchivoTrabajo;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.Venta;
import com.thiago.escenasFX.repository.ArchivoTrabajoRepository;

/**
 * Videos y audios adjuntos a un trabajo a domicilio (las fotos van aparte, comprimidas en la base:
 * FotoTrabajoService). Mismas reglas que las fotos: los suben el jefe y el técnico asignado, el jefe
 * borra cualquiera y el técnico solo los suyos, y nada se toca con el trabajo terminado/cobrado/
 * eliminado. Quién puede tocar qué trabajo lo resuelve quien llama (VentaController /
 * MisTrabajosService).
 *
 * <p>El archivo va al bucket ({@link AlmacenamientoArchivos}); la base guarda solo la referencia. Uno
 * por pedido (un video puede pesar 100 MB) y la subida al bucket se hace FUERA de una transacción,
 * para no tener una conexión de la base tomada mientras viajan los bytes.
 */
@Service
public class ArchivoTrabajoService {

    private static final Logger log = LoggerFactory.getLogger(ArchivoTrabajoService.class);

    static final long MAX_BYTES_POR_ARCHIVO = 100L * 1024 * 1024;
    static final int MAX_VIDEOS_POR_TRABAJO = 5;
    // Los audios pesan poco (una nota de voz de WhatsApp, unos cientos de KB): el tope es solo de orden.
    static final int MAX_AUDIOS_POR_TRABAJO = 20;
    // Lo que dura el link para reproducir: alcanza para mirarlo entero, y si se vence se pide otro.
    static final Duration VIGENCIA_LINK = Duration.ofHours(2);

    private final ArchivoTrabajoRepository archivoRepo;
    private final AlmacenamientoArchivos almacenamiento;
    private final NotificacionTrabajoService notificacionTrabajoService;
    private final TransactionTemplate tx;

    public ArchivoTrabajoService(ArchivoTrabajoRepository archivoRepo, AlmacenamientoArchivos almacenamiento,
            NotificacionTrabajoService notificacionTrabajoService, PlatformTransactionManager transactionManager) {
        this.archivoRepo = archivoRepo;
        this.almacenamiento = almacenamiento;
        this.notificacionTrabajoService = notificacionTrabajoService;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public List<ArchivoTrabajoResponse> listar(Integer idVenta) {
        return archivoRepo.listarDelTrabajo(idVenta);
    }

    /**
     * @param archivo el archivo subido (MultipartFile lo implementa); se lee dos veces: la cabecera
     *     para saber qué es y después entero hacia el bucket.
     * @param subidoPorAdmin true si lo sube el jefe (avisa al técnico y a los dispositivos del jefe);
     *     false si lo sube el técnico (avisa a los ADMIN).
     * @param momento null = adjunto; "ANTES"/"DESPUES" solo para el técnico (al jefe se le ignora).
     */
    public List<ArchivoTrabajoResponse> subir(Venta venta, Empleado autor, InputStreamSource archivo, long tamanioBytes,
            boolean subidoPorAdmin, String momento) {
        if (tamanioBytes <= 0) {
            throw new IllegalArgumentException("No se recibió ningún archivo");
        }
        momento = FotoTrabajoService.normalizarMomento(momento, subidoPorAdmin);
        if (VentaService.estaBloqueado(venta)) {
            throw new IllegalStateException("El trabajo #" + venta.getIdVenta()
                + " está terminado, cobrado o eliminado: no se le pueden agregar archivos");
        }
        if (tamanioBytes > MAX_BYTES_POR_ARCHIVO) {
            throw new IllegalArgumentException("El archivo pesa más de 100 MB");
        }
        String tipo = detectarTipo(leerCabecera(archivo));
        if (tipo == null) {
            throw new IllegalArgumentException("Solo se pueden adjuntar videos (MP4, MOV, WEBM) o audios "
                + "(OGG/OPUS de WhatsApp, M4A, MP3, AAC, WAV)");
        }
        boolean esVideo = tipo.startsWith("video/");
        long yaTiene = archivoRepo.countByVenta_IdVentaAndTipoContenidoStartingWith(venta.getIdVenta(),
            esVideo ? "video/" : "audio/");
        int maximo = esVideo ? MAX_VIDEOS_POR_TRABAJO : MAX_AUDIOS_POR_TRABAJO;
        if (yaTiene >= maximo) {
            throw new IllegalArgumentException("Un trabajo puede tener hasta " + maximo
                + (esVideo ? " videos" : " audios") + " (ya tiene " + yaTiene + ")");
        }

        String clave = "trabajos/" + venta.getIdVenta() + "/" + UUID.randomUUID() + extension(tipo);
        try (InputStream contenido = archivo.getInputStream()) {
            almacenamiento.guardar(clave, contenido, tamanioBytes, tipo);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo subido", e);
        }

        final String momentoFinal = momento;
        try {
            tx.executeWithoutResult(estado -> {
                ArchivoTrabajo nuevo = new ArchivoTrabajo();
                nuevo.setVenta(venta);
                nuevo.setEmpleado(autor);
                nuevo.setClaveArchivo(clave);
                nuevo.setTipoContenido(tipo);
                nuevo.setTamanioBytes(tamanioBytes);
                nuevo.setMomento(momentoFinal);
                archivoRepo.save(nuevo);
                avisar(venta, autor, subidoPorAdmin, momentoFinal, esVideo ? "un video" : "un audio");
            });
        } catch (RuntimeException e) {
            // Sin registro en la base el archivo quedaría huérfano en el bucket.
            borrarDelBucketSinFallar(clave);
            throw e;
        }
        return archivoRepo.listarDelTrabajo(venta.getIdVenta());
    }

    private void avisar(Venta venta, Empleado autor, boolean subidoPorAdmin, String momento, String queCosa) {
        String donde = " en #" + venta.getIdVenta() + " (" + descripcion(venta) + ")";
        if (subidoPorAdmin) {
            if (venta.getEmpleadoTecnico() != null) {
                notificacionTrabajoService.avisarATecnico(venta.getEmpleadoTecnico(), "FOTO",
                    "Hay " + queCosa + " nuevo en el trabajo de " + descripcion(venta), venta);
            }
            notificacionTrabajoService.avisarCambioDelJefe("Se adjuntó " + queCosa + donde, venta);
        } else {
            notificacionTrabajoService.avisarATodosLosAdmin("FOTO",
                autor.getNombre() + " subió " + queCosa + FotoTrabajoService.textoMomento(momento) + donde, venta);
        }
    }

    @Transactional(readOnly = true)
    public ArchivoTrabajo obtener(Integer idVenta, Integer idArchivo) {
        return archivoRepo.findByIdArchivoAndVenta_IdVenta(idArchivo, idVenta)
            .orElseThrow(() -> new IllegalArgumentException("Archivo no existe: " + idArchivo));
    }

    /** Link temporal para reproducirlo directo desde el bucket. */
    @Transactional(readOnly = true)
    public String urlParaVer(Integer idVenta, Integer idArchivo) {
        return almacenamiento.urlTemporal(obtener(idVenta, idArchivo).getClaveArchivo(), VIGENCIA_LINK);
    }

    /** El jefe borra cualquiera; el técnico, solo los que subió él. */
    public void borrar(Integer idVenta, Integer idArchivo, Empleado quien, boolean esAdmin) {
        String clave = tx.execute(estado -> {
            ArchivoTrabajo archivo = obtener(idVenta, idArchivo);
            if (VentaService.estaBloqueado(archivo.getVenta())) {
                throw new IllegalStateException("El trabajo #" + idVenta
                    + " está terminado, cobrado o eliminado: no se le pueden borrar archivos");
            }
            if (!esAdmin && !archivo.getEmpleado().getIdEmpleado().equals(quien.getIdEmpleado())) {
                throw new AccessDeniedException("Solo podés borrar los archivos que subiste vos");
            }
            archivoRepo.delete(archivo);
            return archivo.getClaveArchivo();
        });
        borrarDelBucketSinFallar(clave);
    }

    // Si el bucket falla al borrar, el archivo ya no aparece en el sistema: queda uno de más en el
    // bucket, que no rompe nada. Mejor eso que dejar el registro apuntando a algo a medio borrar.
    private void borrarDelBucketSinFallar(String clave) {
        try {
            almacenamiento.borrar(clave);
        } catch (RuntimeException e) {
            log.warn("No se pudo borrar {} del bucket: {}", clave, e.getMessage());
        }
    }

    private static byte[] leerCabecera(InputStreamSource archivo) {
        try (InputStream in = archivo.getInputStream()) {
            return in.readNBytes(12);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo subido", e);
        }
    }

    /**
     * Tipo real según los primeros bytes, sin confiar en lo que dice el navegador. Null si no es un
     * video ni un audio que se pueda reproducir.
     * <ul>
     * <li>"ftyp" en el byte 4: contenedor MP4. La marca que sigue dice si es un MOV de iPhone ("qt"),
     * un 3GP de Android, un audio M4A ("M4A "/"M4B ") o un video MP4.</li>
     * <li>Firma EBML: WEBM (se trata como video; el reproductor de video también pasa audio solo).</li>
     * <li>"OggS": OGG/OPUS, las notas de voz de WhatsApp.</li>
     * <li>"ID3" o sincronía MPEG: MP3; sincronía ADTS: AAC. "RIFF....WAVE": WAV.</li>
     * </ul>
     */
    static String detectarTipo(byte[] b) {
        if (b.length >= 12 && b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p') {
            if (b[8] == 'q' && b[9] == 't') {
                return "video/quicktime";
            }
            if (b[8] == '3' && b[9] == 'g') {
                return "video/3gpp";
            }
            if (b[8] == 'M' && b[9] == '4' && (b[10] == 'A' || b[10] == 'B')) {
                return "audio/mp4";
            }
            return "video/mp4";
        }
        if (b.length >= 4 && (b[0] & 0xFF) == 0x1A && (b[1] & 0xFF) == 0x45 && (b[2] & 0xFF) == 0xDF
                && (b[3] & 0xFF) == 0xA3) {
            return "video/webm";
        }
        if (b.length >= 4 && b[0] == 'O' && b[1] == 'g' && b[2] == 'g' && b[3] == 'S') {
            return "audio/ogg";
        }
        if (b.length >= 3 && b[0] == 'I' && b[1] == 'D' && b[2] == '3') {
            return "audio/mpeg";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'A' && b[10] == 'V' && b[11] == 'E') {
            return "audio/wav";
        }
        if (b.length >= 2 && (b[0] & 0xFF) == 0xFF) {
            int segundo = b[1] & 0xFF;
            if ((segundo & 0xF6) == 0xF0) {
                return "audio/aac";
            }
            if ((segundo & 0xE0) == 0xE0) {
                return "audio/mpeg";
            }
        }
        return null;
    }

    private static String extension(String tipo) {
        return switch (tipo) {
            case "video/quicktime" -> ".mov";
            case "video/3gpp" -> ".3gp";
            case "video/webm" -> ".webm";
            case "audio/mp4" -> ".m4a";
            case "audio/ogg" -> ".ogg";
            case "audio/mpeg" -> ".mp3";
            case "audio/aac" -> ".aac";
            case "audio/wav" -> ".wav";
            default -> ".mp4";
        };
    }

    private static String descripcion(Venta venta) {
        return venta.getDireccionTrabajo() != null ? venta.getDireccionTrabajo() : "#" + venta.getIdVenta();
    }
}
