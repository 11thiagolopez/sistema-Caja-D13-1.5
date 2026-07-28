package com.thiago.escenasFX.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ProductoRankingDTO {
    private Integer idProducto;
    private String descripcion;
    private long cantidadVendida;
    private BigDecimal totalFacturado;
}
