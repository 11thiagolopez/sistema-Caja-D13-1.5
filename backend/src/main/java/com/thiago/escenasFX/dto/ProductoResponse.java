package com.thiago.escenasFX.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ProductoResponse {
    private Integer idProducto;
    private String rubro;
    private String familia;
    private String marca;
    private String numeroMarca;
    private String correlativo;
    private String codigoInterno;
    private String proveedor;
    private String codigoFabrica;
    private String descripcion;
    private BigDecimal precioVenta;
    private BigDecimal precioCompra;
    private BigDecimal precioVentaUsd;
    private BigDecimal precioCompraUsd;
    private int stockActual;
    private boolean activo;
}
