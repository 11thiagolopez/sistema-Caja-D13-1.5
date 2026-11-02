package com.thiago.escenasFX.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MovimientoProveedorArqueoResponse {
    private Integer idMovimiento;
    private LocalDateTime fecha;
    private String nombreProveedor;
    private String descripcion;
    private String medioPago;
    private String origen;
    private BigDecimal monto;
    private boolean restaDelSaldoEsperado;
}
