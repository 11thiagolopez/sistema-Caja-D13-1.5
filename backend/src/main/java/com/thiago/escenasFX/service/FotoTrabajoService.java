package com.thiago.escenasFX.service;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thiago.escenasFX.dto.FotoTrabajoResponse;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.FotoTrabajo;
import com.thiago.escenasFX.model.Venta;
import com.thiago.escenasFX.repository.FotoTrabajoRepository;

/**
 * Fotos de un trabajo a domicilio, compartidas entre el jefe y el técnico asignado. Quién puede
 * tocar qué trabajo lo resuelve quien llama (VentaController para ADMIN, MisTrabajosService para
 * el técnico, que valida que el trabajo sea suyo): acá llega una {@link Venta} ya autorizada.
 *
 * <p>Se suben varias de una vez en un solo pedido, para que el otro reciba un único aviso ("subió
 * 3 fotos") y no uno por foto.
 */
@Service
public class FotoTrabajoService {

    // El frontend las achica a ~1600px en JPEG antes de subir (unos 200-400 KB): este tope es solo
    // para frenar a quien suba un original sin comprimir, no el tamaño esperado.
    static final int MAX_BYTES_POR_FOTO = 3 * 1024 * 1024;
    static final int MAX_FOTOS_POR_TRABAJO = 30;
    static final java.util.Set<String> MOMENTOS = java.util.Set.of("ANTES", "DESPUES");

    private final FotoTrabajoRepository fotoRepo;
    private final NotificacionTrabajoService notificacionTrabajoService;

    public FotoTrabajoService(FotoTrabajoRepository fotoRepo, NotificacionTrabajoService notificacionTrabajoService) {
        this.fotoRepo = fotoRepo;
        this.notificacionTrabajoService = notificacionTrabajoService;
    }

    @Transactional(readOnly = true)
    public List<FotoTrabajoResponse> listar(Integer idVenta) {
        return fotoRepo.listarDelTrabajo(idVenta);
    }

    /**
     * null = adjunto (lo que mandó el cliente, o lo que suma cualquiera para dar contexto); "ANTES" /
     * "DESPUES" = comprobante del técnico. Al jefe se le ignora: lo suyo siempre es adjunto. Un valor
     * que no sea ninguno de esos es un error (no se adivina).
     */
    static String normalizarMomento(String momento, boolean subidoPorAdmin) {
        if (subidoPorAdmin || momento == null || momento.isBlank()) {
            return null;
        }
        if (!MOMENTOS.contains(momento)) {
            throw new IllegalArgumentException("Momento inválido: " + momento + " (ANTES, DESPUES o ninguno)");
        }
        return momento;
    }

    /** " de antes", " de después" o "" (adjunto), para los avisos. */
    static String textoMomento(String momento) {
        if ("ANTES".equals(momento)) {
            return " de antes";
        }
        return "DESPUES".equals(momento) ? " de después" : "";
    }

    /**
     * @param subidaPorAdmin true si la sube el jefe (avisa al técnico asignado y a los otros
     *     dispositivos del jefe); false si la sube el técnico (avisa a los ADMIN).
     * @param momento null = adjunto; "ANTES"/"DESPUES" solo para el técnico (al jefe se le ignora).
     */
    @Transactional
    public List<FotoTrabajoResponse> subir(Venta venta, Empleado autor, List<byte[]> imagenes, boolean subidaPorAdmin,
            String momento) {
        if (imagenes.isEmpty()) {
            throw new IllegalArgumentException("No se recibió ninguna foto");
        }
        momento = normalizarMomento(momento, subidaPorAdmin);
        if (VentaService.estaBloqueado(venta)) {
            throw new IllegalStateException("El trabajo #" + venta.getIdVenta()
                + " está terminado, cobrado o eliminado: no se le pueden agregar fotos");
        }
        long yaTiene = fotoRepo.countByVenta_IdVenta(venta.getIdVenta());
        if (yaTiene + imagenes.size() > MAX_FOTOS_POR_TRABAJO) {
            throw new IllegalArgumentException("Un trabajo puede tener hasta " + MAX_FOTOS_POR_TRABAJO
                + " fotos (ya tiene " + yaTiene + ")");
        }

        for (byte[] imagen : imagenes) {
            if (imagen.length > MAX_BYTES_POR_FOTO) {
                throw new IllegalArgumentException("Una de las fotos pesa más de 3 MB");
            }
            String tipo = detectarTipo(imagen);
            if (tipo == null) {
                throw new IllegalArgumentException("Solo se pueden subir fotos (JPG, PNG o WEBP)");
            }
            FotoTrabajo foto = new FotoTrabajo();
            foto.setVenta(venta);
            foto.setEmpleado(autor);
            foto.setContenido(imagen);
            foto.setTipoContenido(tipo);
            foto.setMomento(momento);
            fotoRepo.save(foto);
        }

        String cantidad = imagenes.size() == 1 ? "una foto" : imagenes.size() + " fotos";
        if (subidaPorAdmin) {
            if (venta.getEmpleadoTecnico() != null) {
                notificacionTrabajoService.avisarATecnico(venta.getEmpleadoTecnico(), "FOTO",
                    "Hay " + cantidad + " nueva" + (imagenes.size() == 1 ? "" : "s") + " en el trabajo de "
                        + descripcion(venta), venta);
            }
            notificacionTrabajoService.avisarCambioDelJefe("Se adjunt" + (imagenes.size() == 1 ? "ó " : "aron ")
                + cantidad + " en #" + venta.getIdVenta() + " (" + descripcion(venta) + ")", venta);
        } else {
            notificacionTrabajoService.avisarATodosLosAdmin("FOTO",
                autor.getNombre() + " subió " + cantidad + textoMomento(momento) + " en #" + venta.getIdVenta() + " (" + descripcion(venta) + ")",
                venta);
        }
        return fotoRepo.listarDelTrabajo(venta.getIdVenta());
    }

    @Transactional(readOnly = true)
    public FotoTrabajo obtener(Integer idVenta, Integer idFoto) {
        return fotoRepo.findByIdFotoAndVenta_IdVenta(idFoto, idVenta)
            .orElseThrow(() -> new IllegalArgumentException("Foto no existe: " + idFoto));
    }

    /** El jefe borra cualquiera; el técnico, solo las que subió él. */
    @Transactional
    public void borrar(Integer idVenta, Integer idFoto, Empleado quien, boolean esAdmin) {
        FotoTrabajo foto = obtener(idVenta, idFoto);
        if (VentaService.estaBloqueado(foto.getVenta())) {
            throw new IllegalStateException("El trabajo #" + idVenta + " está terminado, cobrado o eliminado: no se le pueden borrar fotos");
        }
        if (!esAdmin && !foto.getEmpleado().getIdEmpleado().equals(quien.getIdEmpleado())) {
            throw new AccessDeniedException("Solo podés borrar las fotos que subiste vos");
        }
        fotoRepo.delete(foto);
    }

    /**
     * Tipo real según los primeros bytes, sin confiar en el Content-Type que manda el navegador
     * (la foto se sirve después con este tipo). Null si no es JPG, PNG ni WEBP.
     */
    static String detectarTipo(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "image/png";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private static String descripcion(Venta venta) {
        return venta.getDireccionTrabajo() != null ? venta.getDireccionTrabajo() : "#" + venta.getIdVenta();
    }
}
