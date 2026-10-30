package com.thiago.escenasFX.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DetalleNotaCreditoRequest {

    @NotNull
    private Integer idDetalleVentaOriginal;

    @Min(1)
    private int cantidad;
}
