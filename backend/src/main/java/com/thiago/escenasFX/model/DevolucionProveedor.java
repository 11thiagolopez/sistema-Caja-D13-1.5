package com.thiago.escenasFX.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Devolución de mercadería a un proveedor — B2B interno, sin ARCA (espejo del lado compra de la
 * Nota de Crédito del lado venta). Reduce el stock devuelto y acredita (HABER_DEVOLUCION) la
 * cuenta corriente general del proveedor, no earmarked contra una compra puntual — el proveedor
 * queda "a favor" nuestro por ese monto para descontar de cualquier pago futuro.
 */
@Entity
@Table(name = "devoluciones_proveedor")
@Getter
@Setter
public class DevolucionProveedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_devolucion")
    private Integer idDevolucion;

    @ManyToOne
    @JoinColumn(name = "id_proveedor", nullable = false)
    private Proveedor proveedor;

    // Opcional, puramente informativa — la devolución no earmarked contra el saldo de una
    // compra puntual (ver comentario de clase).
    @ManyToOne
    @JoinColumn(name = "id_compra_original")
    private Compra compraOriginal;

    private LocalDateTime fecha = LocalDateTime.now();

    private String motivo;

    @Column(name = "monto_total", precision = 12, scale = 2, nullable = false)
    private BigDecimal montoTotal;

    @ManyToOne
    @JoinColumn(name = "id_empleado")
    private Empleado empleado;

    @OneToMany(mappedBy = "devolucion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DetalleDevolucionProveedor> detalles = new ArrayList<>();
}
