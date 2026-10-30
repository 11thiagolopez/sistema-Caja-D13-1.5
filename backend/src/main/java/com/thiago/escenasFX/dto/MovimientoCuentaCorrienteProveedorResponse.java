package com.thiago.escenasFX.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MovimientoCuentaCorrienteProveedorResponse {
    private Integer idMovimiento;
    private LocalDateTime fecha;
    private String tipo;
    private BigDecimal monto;
    private String medioPago;
    private String descripcion;
    private Integer idCompra;
    private Integer idDevolucion;
}
