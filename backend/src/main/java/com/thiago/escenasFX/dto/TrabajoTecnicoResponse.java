package com.thiago.escenasFX.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.thiago.escenasFX.model.Venta;

/**
 * Lo que ve el técnico de un trabajo suyo. Lista blanca a propósito, en vez de reusar
 * {@link VentaResponse}: el contacto con el cliente lo maneja el jefe (decisión del dueño), así que
 * acá no van el teléfono ni el email del cliente, ni los datos de facturación/cuenta corriente. Un
 * campo nuevo en VentaResponse no se filtra solo hasta el técnico: hay que agregarlo acá a mano.
 * El precio sí va (Fabián lo ve, confirmado por el dueño).
 */
public record TrabajoTecnicoResponse(
        Integer idVenta,
        String estado,
        String estadoTrabajo,
        String clienteNombre,
        String direccionTrabajo,
        String descripcionTrabajo,
        String observacionesTecnico,
        LocalDate fechaProgramada,
        LocalTime horaDesde,
        LocalTime horaHasta,
        BigDecimal totalVenta,
        List<Item> detalles) {

    public record Item(String descripcion, Integer cantidad, BigDecimal precioUnitario, BigDecimal subtotal) {
    }

    public static TrabajoTecnicoResponse de(Venta venta) {
        List<Item> items = venta.getDetalles().stream()
            .map(d -> new Item(
                d.getDescripcion() != null || d.getProducto() == null ? d.getDescripcion() : d.getProducto().getDescripcion(),
                d.getCantidad(), d.getPrecioUnitario(), d.getSubtotal()))
            .toList();
        return new TrabajoTecnicoResponse(venta.getIdVenta(), venta.getEstado(), venta.getEstadoTrabajo(),
            venta.getClienteNombre(), venta.getDireccionTrabajo(), venta.getDescripcionTrabajo(),
            venta.getObservacionesTecnico(), venta.getFechaProgramada(), venta.getHoraDesde(),
            venta.getHoraHasta(), venta.getTotalVenta(), items);
    }
}
