package com.thiago.escenasFX.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GastoRequest {

    @NotNull
    private Integer idEmpleado;

    @NotBlank
    private String nombre;

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal importe;

    @NotNull
    private LocalDate fecha;

    private String categoria;
}
