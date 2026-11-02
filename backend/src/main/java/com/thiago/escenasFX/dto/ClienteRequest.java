package com.thiago.escenasFX.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClienteRequest {

    @NotBlank
    private String nombre;

    // 80 CUIT | 96 DNI | 99 sin documento.
    @NotNull
    private Integer docTipo;

    private String docNro;

    private Integer condicionIva;

    private String telefono;
    private String email;
    private String direccion;
}
