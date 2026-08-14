package com.thiago.escenasFX.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CotizacionResponse {
    private BigDecimal valorVenta;
    private LocalDate fecha;
    private String fuente;
    private boolean manual;
}
