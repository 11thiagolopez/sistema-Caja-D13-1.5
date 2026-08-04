package com.thiago.escenasFX.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CompraItemResponse {
    private Integer idItem;
    private Integer idProducto;
    private String descripcionProducto;
    private String marcaProducto;
    private int cantidad;
    private BigDecimal precioCompraUnitario;
    private BigDecimal precioVentaUnitario;
    private BigDecimal subtotal;
}
