package com.thiago.escenasFX.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DetalleVentaResponse {
    private Integer idDetalle;
    private Integer idProducto;
    private String descripcionProducto;
    private String tipo;
    private int cantidad;
    private BigDecimal precioUnitario;
    private BigDecimal subtotal;
}
