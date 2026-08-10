package com.thiago.escenasFX.controller;

import java.util.List;

import com.thiago.escenasFX.dto.DetallePresupuestoResponse;
import com.thiago.escenasFX.dto.PresupuestoResponse;
import com.thiago.escenasFX.model.DetallePresupuesto;
import com.thiago.escenasFX.model.Presupuesto;

/**
 * Igual que CompraMapper/VentaMapper: evita serializar las entidades JPA directamente
 * (Presupuesto.detalles <-> DetallePresupuesto.presupuesto es un ciclo bidireccional).
 */
final class PresupuestoMapper {

    private PresupuestoMapper() {
    }

    static PresupuestoResponse toResponse(Presupuesto presupuesto) {
        List<DetallePresupuestoResponse> detalles = presupuesto.getDetalles().stream()
            .map(PresupuestoMapper::toResponse)
            .toList();

        return new PresupuestoResponse(
            presupuesto.getIdPresupuesto(),
            presupuesto.getFecha(),
            presupuesto.getEmpleado() != null ? presupuesto.getEmpleado().getIdEmpleado() : null,
            presupuesto.getEmpleado() != null ? presupuesto.getEmpleado().getNombre() : null,
            presupuesto.getClienteNombre(),
            presupuesto.getClienteEmail(),
            presupuesto.getClienteTelefono(),
            presupuesto.getTotalPresupuesto(),
            presupuesto.isEnviadoPorEmail(),
            detalles);
    }

    private static DetallePresupuestoResponse toResponse(DetallePresupuesto d) {
        return new DetallePresupuestoResponse(
            d.getProducto() != null ? d.getProducto().getIdProducto() : null,
            d.getDescripcion(),
            d.getCantidad(),
            d.getPrecioUnitario(),
            d.getSubtotal());
    }
}
