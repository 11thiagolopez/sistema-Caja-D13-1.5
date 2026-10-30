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
    private List<MovimientoCuentaCorrienteProveedorResponse> movimientos;
}
