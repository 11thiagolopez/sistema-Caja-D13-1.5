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

@Entity
@Table(name = "movimientos_caja")
@Getter
@Setter
public class MovimientoCaja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_movimiento")
    private Integer idMovimiento;

    private LocalDateTime fecha = LocalDateTime.now();

    @ManyToOne
    @JoinColumn(name = "id_empleado")
    private Empleado empleado;

    private String tipo; // "RETIRO"

    @Column(name = "medio_pago")
    private String medioPago;

    @Column(precision = 12, scale = 2)
    private BigDecimal monto;

    private String motivo;
}
