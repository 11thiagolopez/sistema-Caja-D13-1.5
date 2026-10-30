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
 * Ledger de cuenta corriente con un proveedor — "cuánto le debemos". DEBE_COMPRA suma deuda,
 * HABER_PAGO y HABER_DEVOLUCION la restan. El saldo (CuentaCorrienteProveedorService o
 * ProveedorService.calcularSaldo, según dónde termine viviendo) se recalcula siempre sumando
 * este historial — mismo criterio que MovimientoCuentaCorrienteCliente, nunca un número suelto.
 */
@Entity
@Table(name = "movimientos_cuenta_corriente_proveedor")
@Getter
@Setter
public class MovimientoCuentaCorrienteProveedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_movimiento")
    private Integer idMovimiento;

    @ManyToOne
    @JoinColumn(name = "id_proveedor", nullable = false)
    private Proveedor proveedor;

    // Set en DEBE_COMPRA y en un HABER_PAGO contra una compra puntual.
    @ManyToOne
    @JoinColumn(name = "id_compra")
    private Compra compra;

    private LocalDateTime fecha = LocalDateTime.now();

    // DEBE_COMPRA (suma deuda, al registrar la compra) | HABER_PAGO (pago contra una compra
    // puntual) | HABER_DEVOLUCION (mercadería devuelta al proveedor).
    @Column(nullable = false)
    private String tipo;

    @Column(precision = 12, scale = 2, nullable = false)
    private BigDecimal monto; // siempre positivo; el signo lo da "tipo"

    // Solo tiene sentido en HABER_PAGO.
    @Column(name = "medio_pago")
    private String medioPago;

    // Set en HABER_DEVOLUCION.
    @ManyToOne
    @JoinColumn(name = "id_devolucion")
    private DevolucionProveedor devolucion;

    private String descripcion;

    @ManyToOne
    @JoinColumn(name = "id_empleado")
    private Empleado empleado;
}
