package com.thiago.escenasFX.model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "productos")
@Getter
@Setter
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_producto")
    private Integer idProducto;

    private String rubro;
    private String familia;
    private String marca;
    private String correlativo;

    @Column(name = "codigo_interno")
    private String codigoInterno;

    private String proveedor;

    @Column(name = "codigo_fabrica")
    private String codigoFabrica;

    private String descripcion;

    @Column(name = "precio_venta", precision = 12, scale = 2)
    private BigDecimal precioVenta;

    // Costo de compra, usado para calcular "Costo de Mercadería" en el balance financiero.
    @Column(name = "precio_compra", precision = 12, scale = 2)
    private BigDecimal precioCompra;

    @Column(name = "stock_actual")
    private int stockActual;

    // Baja lógica: un producto eliminado deja de listarse/venderse pero no rompe la integridad
    // de las ventas históricas que ya lo referencian (DetalleVenta -> Producto no tiene cascade).
    private boolean activo = true;
}
