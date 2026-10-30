package com.thiago.escenasFX.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MovimientoCuentaCorrienteClienteResponse {
    private Integer idMovimiento;
    private LocalDateTime fecha;
    private String tipo;
    private BigDecimal monto;
    private String descripcion;
    private Integer idNotaCredito;
    private Integer idVentaAplicacion;
}
