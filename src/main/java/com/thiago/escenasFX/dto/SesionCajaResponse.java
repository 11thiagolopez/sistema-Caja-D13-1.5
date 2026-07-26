package com.thiago.escenasFX.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SesionCajaResponse {
    private Integer idSesion;
    private LocalDate fecha;
    private BigDecimal montoInicial;
    private String estado;
}
