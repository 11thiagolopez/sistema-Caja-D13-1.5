package com.thiago.escenasFX.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PresupuestoRequest {

    @NotNull
    private Integer idEmpleado;

    @NotBlank
    private String clienteNombre;

    @Email
    private String clienteEmail;

    private String clienteTelefono;

    @NotEmpty
    @Valid
    private List<DetallePresupuestoRequest> detalles;
}
