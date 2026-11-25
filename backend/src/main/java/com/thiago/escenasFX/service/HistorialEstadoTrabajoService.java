package com.thiago.escenasFX.service;

import org.springframework.stereotype.Service;

import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.HistorialEstadoTrabajo;
import com.thiago.escenasFX.model.Venta;
import com.thiago.escenasFX.repository.HistorialEstadoTrabajoRepository;

/**
 * Compartido entre VentaService (flujo del jefe) y MisTrabajosService (flujo del técnico): ambos
 * necesitan dejar la misma fila de auditoría cuando cambia el estado de un trabajo.
 */
@Service
public class HistorialEstadoTrabajoService {

    private final HistorialEstadoTrabajoRepository historialEstadoRepo;

    public HistorialEstadoTrabajoService(HistorialEstadoTrabajoRepository historialEstadoRepo) {
        this.historialEstadoRepo = historialEstadoRepo;
    }

    /** detalle: null salvo para una reprogramación, donde describe el cambio de fecha/horario. */
    public void registrar(Venta venta, String estadoAnterior, String estadoNuevo, Empleado empleado,
            String detalle) {
        HistorialEstadoTrabajo historial = new HistorialEstadoTrabajo();
        historial.setVenta(venta);
        historial.setEstadoAnterior(estadoAnterior);
        historial.setEstadoNuevo(estadoNuevo);
        historial.setEmpleado(empleado);
        historial.setDetalle(detalle);
        historialEstadoRepo.save(historial);
    }

    /** El estado que tenía el trabajo justo antes de la última vez que pasó a {@code estado}; null si nunca pasó. */
    public String estadoAntesDe(Integer idVenta, String estado) {
        String anterior = null;
        for (HistorialEstadoTrabajo h : historialEstadoRepo.findByVentaIdVentaOrderByCreadoEnAsc(idVenta)) {
            if (estado.equals(h.getEstadoNuevo())) {
                anterior = h.getEstadoAnterior();
            }
        }
        return anterior;
    }
}
