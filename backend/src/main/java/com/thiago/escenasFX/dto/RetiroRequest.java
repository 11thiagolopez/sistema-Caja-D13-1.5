package com.thiago.escenasFX.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RetiroRequest {

    @NotNull
    private Integer idEmpleado;

    @NotNull
    @Positive
    private BigDecimal monto;

    @NotBlank
    private String motivo;

    @NotBlank
    private String medioPago;
}
