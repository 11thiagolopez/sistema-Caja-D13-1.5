package com.thiago.escenasFX.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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

/**
 * Ledger de movimientos de la cuenta corriente de un cliente — es dinero, así que el saldo nunca
 * se guarda como un número mutable: se recalcula siempre sumando CREDITO menos DEBITO de esta
 * tabla (ver ClienteService.calcularSaldo), para poder auditar siempre de dónde salió el saldo.
 */
@Entity
@Table(name = "movimientos_cuenta_corriente_cliente")
@Getter
@Setter
public class MovimientoCuentaCorrienteCliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_movimiento")
    private Integer idMovimiento;

    @ManyToOne
    @JoinColumn(name = "id_cliente", nullable = false)
    private Cliente cliente;

    private LocalDateTime fecha = LocalDateTime.now();

    // CREDITO (a favor del cliente: nota de crédito o pago) | DEBITO (saldo aplicado en una venta o
    // venta en cuenta corriente). Saldo negativo = el cliente debe.
    @Column(nullable = false)
    private String tipo;

    @Column(precision = 12, scale = 2, nullable = false)
    private BigDecimal monto; // siempre positivo; el signo lo da "tipo"

    // Set en movimientos CREDITO originados por una nota de crédito.
    @ManyToOne
    @JoinColumn(name = "id_nota_credito")
    private NotaCredito notaCredito;

    // La venta que originó el movimiento: saldo aplicado, cargo en cuenta corriente o su reversión.
    @ManyToOne
    @JoinColumn(name = "id_venta_aplicacion")
    private Venta ventaAplicacion;

    private String descripcion;

    // Solo en los pagos del cliente, que son dinero real y entran al arqueo.
    @Column(name = "medio_pago")
    private String medioPago;

    @ManyToOne
    @JoinColumn(name = "id_sesion")
    private SesionCaja sesion;

    @ManyToOne
    @JoinColumn(name = "id_empleado")
    private Empleado empleado;
}
