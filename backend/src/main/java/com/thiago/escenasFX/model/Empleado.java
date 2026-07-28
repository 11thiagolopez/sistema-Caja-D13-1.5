package com.thiago.escenasFX.model;

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
}
