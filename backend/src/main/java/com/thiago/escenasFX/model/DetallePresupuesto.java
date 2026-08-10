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
@Table(name = "detalle_presupuestos")
@Getter
@Setter
public class DetallePresupuesto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_detalle")
    private Integer idDetalle;

    @ManyToOne
    @JoinColumn(name = "id_presupuesto")
    private Presupuesto presupuesto;

    // Opcional: los ítems manuales (trabajos sin precio fijo, ej. "Apertura de cerradura") no
    // están vinculados a ningún producto del catálogo.
    @ManyToOne
    @JoinColumn(name = "id_producto")
    private Producto producto;

    // Snapshot de la descripción al momento de crear el presupuesto: si viene de un producto del
    // catálogo se copia de ahí, si es manual la escribe quien lo carga. No depende de que el
    // producto siga existiendo o de que no le cambien la descripción después.
    private String descripcion;

    private int cantidad;

    @Column(name = "precio_unitario", precision = 12, scale = 2)
    private BigDecimal precioUnitario;

    @Column(precision = 12, scale = 2)
    private BigDecimal subtotal;
}
