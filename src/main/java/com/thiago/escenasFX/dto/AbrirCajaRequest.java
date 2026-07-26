package com.thiago.escenasFX.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AbrirCajaRequest {

    @NotNull
    private Integer idEmpleado;

    @NotNull
    @PositiveOrZero
    private BigDecimal montoInicial;
}
