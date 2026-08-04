package com.thiago.escenasFX.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CompraRequest {

    @NotNull
    private Integer idEmpleado;

    @NotNull
    private LocalDate fecha;

    @NotBlank
    private String proveedorNombre;

    @NotBlank
    private String medioPago;

    @NotEmpty
    @Valid
    private List<CompraItemRequest> items;
}
