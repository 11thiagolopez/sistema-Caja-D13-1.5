package com.thiago.escenasFX.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ProveedorResponse {
    private Integer idProveedor;
    private String nombre;
    private String contacto;
    private String telefono;
    private String email;
    private boolean activo;
}
