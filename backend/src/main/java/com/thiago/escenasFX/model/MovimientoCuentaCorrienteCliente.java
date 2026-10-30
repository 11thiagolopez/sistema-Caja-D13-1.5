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

    // CREDITO (a favor del cliente, ej. nota de crédito) | DEBITO (usado, ej. aplicado como pago
    // en una venta futura — fase 3).
    @Column(nullable = false)
    private String tipo;

    @Column(precision = 12, scale = 2, nullable = false)
    private BigDecimal monto; // siempre positivo; el signo lo da "tipo"

    // Set en movimientos CREDITO originados por una nota de crédito.
    @ManyToOne
    @JoinColumn(name = "id_nota_credito")
    private NotaCredito notaCredito;

    // Set en movimientos DEBITO: la venta en la que se aplicó el saldo (fase 3, sin usar todavía).
    @ManyToOne
    @JoinColumn(name = "id_venta_aplicacion")
    private Venta ventaAplicacion;

    private String descripcion;

    @ManyToOne
    @JoinColumn(name = "id_empleado")
    private Empleado empleado;
}
