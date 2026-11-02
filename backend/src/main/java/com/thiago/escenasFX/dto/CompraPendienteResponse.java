package com.thiago.escenasFX.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CompraPendienteResponse {
    private Integer idCompra;
    private LocalDate fecha;
    private BigDecimal totalCompra;
    // Lo que falta pagar de esta compra, ya descontado el crédito general de devoluciones.
    private BigDecimal saldoPendiente;
}
