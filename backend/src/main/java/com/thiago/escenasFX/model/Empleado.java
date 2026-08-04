package com.thiago.escenasFX.model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "empleados")
@Getter
@Setter
public class Empleado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_empleado")
    private Integer idEmpleado;

    private String nombre;
    private String usuario;

    @Column(name = "password_hash")
    private String passwordHash;

    private String rol; // ADMIN | VENDEDOR

    // Usado para enviar los OTP de retiro de caja / descuento de venta a los ADMIN.
    private String email;

    // % que se lleva el vendedor sobre la ganancia (venta - costo) de lo que vende, no sobre el
    // total facturado. Null = sin comisión asignada.
    @Column(precision = 5, scale = 2)
    private BigDecimal comision;

    // Baja lógica: no se borra para no romper ventas/sesiones de caja históricas que ya lo
    // referencian.
    private boolean activo = true;
}
