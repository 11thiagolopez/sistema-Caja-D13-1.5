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
@Table(name = "detalle_notas_credito")
@Getter
@Setter
public class DetalleNotaCredito {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_detalle")
    private Integer idDetalle;

    @ManyToOne
    @JoinColumn(name = "id_nota_credito")
    private NotaCredito notaCredito;

    @ManyToOne
    @JoinColumn(name = "id_detalle_venta_original", nullable = false)
    private DetalleVenta detalleVentaOriginal;

    // Snapshot del producto de la línea original (null en líneas sin producto — mano de obra).
    @ManyToOne
    @JoinColumn(name = "id_producto")
    private Producto producto;

    private String descripcion;

    // Cantidad acreditada en ESTA nota (<= remanente de la línea original, ver
    // NotaCreditoService.sumarCantidadAcreditada).
    private int cantidad;

    @Column(name = "precio_unitario", precision = 12, scale = 2)
    private BigDecimal precioUnitario;

    @Column(precision = 12, scale = 2)
    private BigDecimal subtotal;
}
