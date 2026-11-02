package com.thiago.escenasFX.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PuntoGraficoDTO {
    private String etiqueta;
    private BigDecimal valor;
}
