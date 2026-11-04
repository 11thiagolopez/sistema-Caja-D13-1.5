package com.thiago.escenasFX.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PagoClienteRequest {

    @NotNull
    @Positive
    private BigDecimal monto;

    // EFECTIVO | TRANSFERENCIA | TARJETA
    @NotBlank
    private String medioPago;

    private String descripcion;
}
