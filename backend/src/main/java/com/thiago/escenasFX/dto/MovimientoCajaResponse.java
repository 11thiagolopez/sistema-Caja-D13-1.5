package com.thiago.escenasFX.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MovimientoCajaResponse {
    private Integer idMovimiento;
    private LocalDateTime fecha;
    private String tipo;
    private String medioPago;
    private BigDecimal monto;
    private String motivo;
}
