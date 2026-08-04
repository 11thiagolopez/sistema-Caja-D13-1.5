package com.thiago.escenasFX.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Nunca expone passwordHash. */
@Getter
@AllArgsConstructor
public class EmpleadoResponse {
    private Integer idEmpleado;
    private String nombre;
    private String usuario;
    private String rol;
    private String email;
    private BigDecimal comision;
    private boolean activo;
}
