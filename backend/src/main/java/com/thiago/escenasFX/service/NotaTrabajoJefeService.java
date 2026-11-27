package com.thiago.escenasFX.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.NotaTrabajo;
import com.thiago.escenasFX.model.Venta;
import com.thiago.escenasFX.repository.NotaTrabajoRepository;

/**
 * Notas que deja el jefe en un trabajo (la tabla de permisos del documento del dueño: "Agregar
 * notas: Jefe sí, Técnico sí"). Misma lista que las del técnico (notas_trabajo); el aviso va al
 * técnico asignado, el camino inverso de MisTrabajosService.agregarNota.
 */
@Service
public class NotaTrabajoJefeService {

    private final VentaService ventaService;
    private final NotaTrabajoRepository notaTrabajoRepo;
    private final NotificacionTrabajoService notificacionTrabajoService;

    public NotaTrabajoJefeService(VentaService ventaService, NotaTrabajoRepository notaTrabajoRepo,
            NotificacionTrabajoService notificacionTrabajoService) {
        this.ventaService = ventaService;
        this.notaTrabajoRepo = notaTrabajoRepo;
        this.notificacionTrabajoService = notificacionTrabajoService;
    }

    @Transactional
    public NotaTrabajo agregar(Integer idVenta, String texto, Empleado jefe) {
        Venta venta = ventaService.obtenerTrabajo(idVenta);
        if (VentaService.estaBloqueado(venta)) {
            throw new IllegalStateException("El trabajo #" + idVenta
                + " está terminado, cobrado o eliminado: no se le pueden agregar notas");
        }
        NotaTrabajo nota = new NotaTrabajo();
        nota.setVenta(venta);
        nota.setEmpleado(jefe);
        nota.setTexto(texto);
        NotaTrabajo guardada = notaTrabajoRepo.save(nota);

        if (venta.getEmpleadoTecnico() != null) {
            notificacionTrabajoService.avisarATecnico(venta.getEmpleadoTecnico(), "NOTA",
                "Nota del jefe en #" + venta.getIdVenta() + ": " + texto, venta);
        }
        notificacionTrabajoService.avisarCambioDelJefe("Nota en " + ResumenTrabajo.nombre(venta) + ": " + texto, venta);
        return guardada;
    }
}
