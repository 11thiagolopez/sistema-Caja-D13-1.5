package com.thiago.escenasFX.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ClienteResponse {
    private Integer idCliente;
    private String nombre;
    private Integer docTipo;
    private String docNro;
    private Integer condicionIva;
    private String telefono;
    private String email;
    private String direccion;
    private boolean activo;
}
