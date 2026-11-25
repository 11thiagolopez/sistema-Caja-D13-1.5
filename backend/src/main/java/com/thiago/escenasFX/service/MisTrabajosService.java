package com.thiago.escenasFX.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.NotaTrabajo;
import com.thiago.escenasFX.model.Venta;
import com.thiago.escenasFX.repository.NotaTrabajoRepository;
import com.thiago.escenasFX.repository.VentaRepository;

/**
 * "Mis trabajos" del técnico: ver y actualizar únicamente los trabajos que tiene asignados. A
 * diferencia de VentaService (el flujo del jefe, que puede operar cualquier trabajo), cada método
 * acá valida que el trabajo sea del técnico que lo pide — nunca confía en el id de la URL solo.
 */
@Service
public class MisTrabajosService {

    // AGENDADO lo fija el jefe al crear el ticket; TERMINADO (que bloquea el trabajo),
    // CERRADO_Y_COBRADO y CANCELADO también son del jefe. El "Terminado" del técnico es
    // TERMINADO_TECNICO: solo un aviso para el jefe, que después confirma con su propio Terminado.
    static final Set<String> ESTADOS_PERMITIDOS_TECNICO =
        Set.of("EN_CAMINO", "EN_CURSO", VentaService.ESTADO_TERMINADO_TECNICO);

    private static final Map<String, String> TIPO_AVISO_ADMIN = Map.of(
        "EN_CAMINO", "EN_CAMINO",
        "EN_CURSO", "LLEGO",
        VentaService.ESTADO_TERMINADO_TECNICO, "TERMINADO");

    private final VentaRepository ventaRepo;
    private final NotaTrabajoRepository notaTrabajoRepo;
    private final HistorialEstadoTrabajoService historialEstadoService;
    private final NotificacionTrabajoService notificacionTrabajoService;

    public MisTrabajosService(VentaRepository ventaRepo, NotaTrabajoRepository notaTrabajoRepo,
            HistorialEstadoTrabajoService historialEstadoService,
            NotificacionTrabajoService notificacionTrabajoService) {
        this.ventaRepo = ventaRepo;
        this.notaTrabajoRepo = notaTrabajoRepo;
        this.historialEstadoService = historialEstadoService;
        this.notificacionTrabajoService = notificacionTrabajoService;
    }

    @Transactional(readOnly = true)
    public List<Venta> listar(Empleado tecnico) {
        return ventaRepo.buscarTrabajosDelTecnico(tecnico.getIdEmpleado());
    }

    @Transactional(readOnly = true)
    public Venta obtener(Integer idVenta, Empleado tecnico) {
        return obtenerPropio(idVenta, tecnico);
    }

    @Transactional(readOnly = true)
    public List<NotaTrabajo> listarNotas(Integer idVenta, Empleado tecnico) {
        obtenerPropio(idVenta, tecnico);
        return notaTrabajoRepo.findByVentaIdVentaOrderByCreadoEnAsc(idVenta);
    }

    @Transactional
    public Venta cambiarEstado(Integer idVenta, String nuevoEstado, Empleado tecnico) {
        if (!ESTADOS_PERMITIDOS_TECNICO.contains(nuevoEstado)) {
            throw new IllegalArgumentException("Estado inválido: " + nuevoEstado);
        }
        Venta venta = obtenerPropioConLock(idVenta, tecnico);
        exigirNoCerrado(venta);

        String estadoAnterior = venta.getEstadoTrabajo();
        venta.setEstadoTrabajo(nuevoEstado);
        Venta guardada = ventaRepo.save(venta);
        historialEstadoService.registrar(guardada, estadoAnterior, nuevoEstado, tecnico, null);

        String tipoAviso = TIPO_AVISO_ADMIN.get(nuevoEstado);
        String texto = tipoAviso.equals("TERMINADO")
            ? tecnico.getNombre() + " avisó que terminó el trabajo #" + guardada.getIdVenta() + " ("
                + descripcionParaAviso(guardada) + "): revisalo y marcalo Terminado para cobrarlo"
            : tecnico.getNombre() + (tipoAviso.equals("LLEGO") ? " llegó a " : " está en camino a ")
                + descripcionParaAviso(guardada);
        notificacionTrabajoService.avisarATodosLosAdmin(tipoAviso, texto, guardada);

        return guardada;
    }

    @Transactional
    public NotaTrabajo agregarNota(Integer idVenta, String texto, Empleado tecnico) {
        Venta venta = obtenerPropio(idVenta, tecnico);
        exigirNoCerrado(venta);

        NotaTrabajo nota = new NotaTrabajo();
        nota.setVenta(venta);
        nota.setEmpleado(tecnico);
        nota.setTexto(texto);
        NotaTrabajo guardada = notaTrabajoRepo.save(nota);

        notificacionTrabajoService.avisarATodosLosAdmin("NOTA", "Nota en #" + venta.getIdVenta() + ": " + texto,
            venta);

        return guardada;
    }

    /** Un trabajo propio y todavía abierto: para lo que escribe el técnico fuera de estado/notas (fotos). */
    @Transactional(readOnly = true)
    public Venta obtenerAbierto(Integer idVenta, Empleado tecnico) {
        Venta venta = obtenerPropio(idVenta, tecnico);
        exigirNoCerrado(venta);
        return venta;
    }

    private String descripcionParaAviso(Venta venta) {
        return venta.getDireccionTrabajo() != null ? venta.getDireccionTrabajo() : "sin dirección cargada";
    }

    private Venta obtenerPropio(Integer idVenta, Empleado tecnico) {
        Venta venta = ventaRepo.findById(idVenta)
            .orElseThrow(() -> new IllegalArgumentException("Trabajo no existe: " + idVenta));
        exigirPropio(venta, tecnico);
        return venta;
    }

    // Con lock: para que cambiarEstado no pise un cambio concurrente (ej. dos toques rápidos del
    // mismo botón) ni corra contra un trabajo que el jefe está cerrando en ese mismo instante.
    private Venta obtenerPropioConLock(Integer idVenta, Empleado tecnico) {
        Venta venta = ventaRepo.buscarPorIdConLock(idVenta)
            .orElseThrow(() -> new IllegalArgumentException("Trabajo no existe: " + idVenta));
        exigirPropio(venta, tecnico);
        return venta;
    }

    private void exigirPropio(Venta venta, Empleado tecnico) {
        boolean esSuyo = "DOMICILIO".equals(venta.getTipoVenta()) && venta.getEmpleadoTecnico() != null
            && venta.getEmpleadoTecnico().getIdEmpleado().equals(tecnico.getIdEmpleado());
        if (!esSuyo) {
            throw new AccessDeniedException("Este trabajo no está asignado a tu usuario");
        }
    }

    // Terminado por el jefe, cobrado o cancelado: el técnico ya no cambia el estado ni agrega notas o
    // fotos (decisión del dueño: "queda todo bloqueado").
    private void exigirNoCerrado(Venta venta) {
        if (VentaService.estaBloqueado(venta)) {
            throw new IllegalStateException(
                "El trabajo #" + venta.getIdVenta() + " ya está cerrado: no se puede actualizar");
        }
    }
}
