package com.thiago.escenasFX.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MarcaRankingDTO {
    private String marca;
    private long cantidadVendida;
    private BigDecimal totalFacturado;
}
