package com.thiago.escenasFX.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LoginResponse {
    private Integer idEmpleado;
    private String nombre;
    private String usuario;
    private String rol;
    private String token;
}
