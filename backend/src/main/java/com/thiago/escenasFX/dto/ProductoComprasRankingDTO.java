package com.thiago.escenasFX.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ProductoComprasRankingDTO {
    private Integer idProducto;
    private String descripcion;
    private long cantidadComprada;
    private BigDecimal totalPagado;
}
