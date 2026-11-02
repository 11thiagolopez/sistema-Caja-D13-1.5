package com.thiago.escenasFX.model;

import java.math.BigDecimal;
import java.time.LocalDate;
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
@Table(name = "gastos")
@Getter
@Setter
public class Gasto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_gasto")
    private Integer idGasto;

    private String nombre; // ej. "Pago de luz"

    @Column(precision = 12, scale = 2)
    private BigDecimal importe;

    private LocalDate fecha;

    private String categoria;

    @Column(name = "medio_pago")
    private String medioPago; // EFECTIVO | TRANSFERENCIA; null en gastos anteriores a este campo

    private String origen; // CAJA | EXTERNO; null en gastos anteriores a este campo

    @ManyToOne
    @JoinColumn(name = "id_empleado_registro")
    private Empleado empleadoRegistro;

    // La caja abierta cuando se registró el gasto: es el turno en cuyo arqueo figura.
    @ManyToOne
    @JoinColumn(name = "id_sesion")
    private SesionCaja sesion;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn = LocalDateTime.now();
}
