package com.thiago.escenasFX.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GastoResponse {
    private Integer idGasto;
    private String nombre;
    private BigDecimal importe;
    private LocalDate fecha;
    private String categoria;
    private String empleadoRegistroNombre;
    private LocalDateTime creadoEn;
}
