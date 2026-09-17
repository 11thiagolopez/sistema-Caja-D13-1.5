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
    // Texto libre de Producto.proveedor (no el FK proveedorRef, que ni el resto del backend usa
    // hoy — ver el comentario en Producto). Indexado acá, no solo en Productos, para que si una
    // venta falla o hay un reclamo se pueda identificar rápido a qué proveedor corresponde cada
    // producto vendido sin tener que ir a buscarlo a la pantalla de Productos. Null en ítems
    // manuales sin producto de catálogo (tipo SERVICIO, o un ítem con descripción libre).
    private String proveedorProducto;
}
