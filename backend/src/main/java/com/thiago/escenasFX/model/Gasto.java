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

    @ManyToOne
    @JoinColumn(name = "id_empleado_registro")
    private Empleado empleadoRegistro;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn = LocalDateTime.now();
}
