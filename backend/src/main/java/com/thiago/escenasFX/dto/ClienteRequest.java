package com.thiago.escenasFX.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClienteRequest {

    // Consumidor Final con documento puede no traerlo. Lo valida ClienteService, según la condición.
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
