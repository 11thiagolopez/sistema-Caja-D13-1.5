package com.thiago.escenasFX.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GastoArqueoResponse {
    private Integer idGasto;
    private LocalDateTime fecha;
    private String nombre;
    private String categoria;
    private String medioPago;
    private String origen;
    private BigDecimal importe;
    private boolean restaDelSaldoEsperado;
}
