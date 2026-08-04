package com.thiago.escenasFX.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProveedorRequest {

    @NotBlank
    private String nombre;

    private String contacto;
    private String telefono;
    private String email;
}
