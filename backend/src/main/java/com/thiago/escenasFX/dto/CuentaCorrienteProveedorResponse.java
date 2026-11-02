package com.thiago.escenasFX.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CuentaCorrienteProveedorResponse {
    private Integer idProveedor;
    private String nombreProveedor;
    // Cuánto le debemos al proveedor hoy (DEBE_COMPRA - HABER_PAGO - HABER_DEVOLUCION). Si da
    // negativo, el proveedor nos debe a nosotros (devoluciones/pagos de más).
    private BigDecimal saldoPendiente;
    // De la más vieja a la más nueva: es el orden en que se les aplica un pago.
    private List<CompraPendienteResponse> comprasPendientes;
    private List<MovimientoCuentaCorrienteProveedorResponse> movimientos;
}
