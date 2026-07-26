package com.thiago.escenasFX.controller;

import java.util.List;

import com.thiago.escenasFX.dto.DetalleVentaResponse;
import com.thiago.escenasFX.dto.VentaResponse;
import com.thiago.escenasFX.model.DetalleVenta;
import com.thiago.escenasFX.model.Venta;

/**
 * Compartido entre VentaController y CajaController para no serializar las entidades JPA
 * directamente (Venta.detalles <-> DetalleVenta.venta es un ciclo bidireccional).
 */
final class VentaMapper {

    private VentaMapper() {
    }

    static VentaResponse toResponse(Venta venta) {
        List<DetalleVentaResponse> detalles = venta.getDetalles().stream()
            .map(VentaMapper::toResponse)
            .toList();

        return new VentaResponse(
            venta.getIdVenta(),
            venta.getFecha(),
            venta.getEmpleado() != null ? venta.getEmpleado().getIdEmpleado() : null,
            venta.getMedioPago(),
            venta.getTipoComprobante(),
            venta.getTotalVenta(),
            venta.getDescuento(),
            venta.getEstado(),
            detalles);
    }

    private static DetalleVentaResponse toResponse(DetalleVenta d) {
        return new DetalleVentaResponse(
            d.getIdDetalle(),
            d.getProducto().getIdProducto(),
            d.getProducto().getDescripcion(),
            d.getCantidad(),
            d.getPrecioUnitario(),
            d.getSubtotal());
    }
}
