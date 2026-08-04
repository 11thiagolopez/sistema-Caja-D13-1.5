package com.thiago.escenasFX.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ComisionEmpleadoDTO {
    private Integer idEmpleado;
    private String nombreEmpleado;
    private BigDecimal comisionPorcentaje;
    private BigDecimal gananciaGenerada;
    private BigDecimal comisionCalculada;
    private long cantidadVentas;
}
