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
@Table(name = "compra_items")
@Getter
@Setter
public class CompraItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_item")
    private Integer idItem;

    @ManyToOne
    @JoinColumn(name = "id_compra")
    private Compra compra;

    @ManyToOne
    @JoinColumn(name = "id_producto")
    private Producto producto;

    private int cantidad;

    @Column(name = "precio_compra_unitario", precision = 12, scale = 2)
    private BigDecimal precioCompraUnitario;

    @Column(name = "precio_venta_unitario", precision = 12, scale = 2)
    private BigDecimal precioVentaUnitario;

    @Column(precision = 12, scale = 2)
    private BigDecimal subtotal;
}
