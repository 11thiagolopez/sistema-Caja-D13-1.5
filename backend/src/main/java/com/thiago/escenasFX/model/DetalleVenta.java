package com.thiago.escenasFX.model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "detalle_ventas")
@Getter
@Setter
public class DetalleVenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_detalle")
    private Integer idDetalle;

    @ManyToOne
    @JoinColumn(name = "id_venta")
    private Venta venta;

    // Opcional: los ítems manuales (trabajos sin precio fijo, ej. "Apertura de cerradura") no
    // están vinculados a ningún producto del catálogo, y no descuentan stock.
    @ManyToOne
    @JoinColumn(name = "id_producto")
    private Producto producto;

    // Snapshot de la descripción al momento de la venta (copiada del producto si viene del
    // catálogo, o escrita a mano si es un ítem manual).
    private String descripcion;

    // ARTICULO | COPIA — categoría informativa (copia de llave vs. artículo), no afecta el
    // cálculo de costo/ganancia en Reportes.
    @Column(nullable = false)
    private String tipo = "ARTICULO";

    private int cantidad;

    @Column(name = "precio_unitario", precision = 12, scale = 2)
    private BigDecimal precioUnitario;

    @Column(precision = 12, scale = 2)
    private BigDecimal subtotal;
}
