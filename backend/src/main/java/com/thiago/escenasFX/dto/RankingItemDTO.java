package com.thiago.escenasFX.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** valor es el dato a graficar según el orden pedido: igual a monto o a unidades. */
@Getter
@AllArgsConstructor
public class RankingItemDTO {
    private String etiqueta;
    private BigDecimal valor;
    private BigDecimal monto;
    private long unidades;
}
