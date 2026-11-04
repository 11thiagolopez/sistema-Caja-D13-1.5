package com.thiago.escenasFX.controller;

import java.util.List;

import com.thiago.escenasFX.dto.DetalleVentaResponse;
import com.thiago.escenasFX.dto.FacturaFiscalResponse;
import com.thiago.escenasFX.dto.VentaResponse;
import com.thiago.escenasFX.model.DetalleVenta;
import com.thiago.escenasFX.model.FacturaFiscal;
import com.thiago.escenasFX.model.Venta;

/**
 * Compartido entre VentaController y CajaController para no serializar las entidades JPA
 * directamente (Venta.detalles <-> DetalleVenta.venta es un ciclo bidireccional).
 */
final class VentaMapper {

    static final String ESTADO_FACTURADA = "FACTURADA";
    static final String ESTADO_SIN_FACTURAR = "SIN_FACTURAR";

    private VentaMapper() {
    }

    /** Sin datos de facturación (null): para el arqueo y los trabajos, que no los usan. */
    static VentaResponse toResponse(Venta venta) {
        return armar(venta, null, null, null);
    }

    static VentaResponse toResponse(Venta venta, FacturaFiscal factura, boolean tieneNotaCredito) {
        boolean facturada = factura != null && "EMITIDA".equals(factura.getEstado());
        return armar(venta, facturada ? ESTADO_FACTURADA : ESTADO_SIN_FACTURAR,
            factura != null ? toFacturaResponse(factura) : null, tieneNotaCredito);
    }

    static FacturaFiscalResponse toFacturaResponse(FacturaFiscal f) {
        return new FacturaFiscalResponse(f.getIdFactura(), f.getVenta().getIdVenta(), f.getPuntoVenta(),
            f.getTipoComprobante(), f.getNumero(), f.getClienteDocTipo(), f.getClienteDocNro(), f.getClienteNombre(),
            f.getCondicionIvaReceptor(), f.getCae(), f.getCaeVencimiento(), f.getImporte(), f.getEstado(), f.getErrorDetalle());
    }

    private static VentaResponse armar(Venta venta, String estadoFactura, FacturaFiscalResponse factura,
            Boolean tieneNotaCredito) {
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
            venta.getClienteEmail(),
            venta.isComprobanteEnviadoPorEmail(),
            venta.getTipoVenta(),
            venta.getClienteNombre(),
            venta.getClienteTelefono(),
            venta.getDireccionTrabajo(),
            venta.getDescripcionTrabajo(),
            venta.getEstadoTrabajo(),
            venta.getFechaProgramada(),
            venta.getEmpleadoTecnico() != null ? venta.getEmpleadoTecnico().getIdEmpleado() : null,
            venta.getEmpleadoTecnico() != null ? venta.getEmpleadoTecnico().getNombre() : null,
            venta.getComisionTecnico(),
            venta.getCliente() != null ? venta.getCliente().getIdCliente() : null,
            venta.getCliente() != null ? venta.getCliente().getNombre() : null,
            venta.getMontoSaldoAplicado(),
            detalles,
            estadoFactura,
            factura,
            tieneNotaCredito);
    }

    private static DetalleVentaResponse toResponse(DetalleVenta d) {
        return new DetalleVentaResponse(
            d.getIdDetalle(),
            d.getProducto() != null ? d.getProducto().getIdProducto() : null,
            d.getDescripcion(),
            d.getTipo(),
            d.getCantidad(),
            d.getPrecioUnitario(),
            d.getSubtotal(),
            d.getProducto() != null ? d.getProducto().getProveedor() : null);
    }
}
