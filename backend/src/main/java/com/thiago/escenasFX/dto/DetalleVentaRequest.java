package com.thiago.escenasFX.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DetalleVentaRequest {

    @NotNull
    private Integer idProducto;

    @Min(1)
    private int cantidad;

    @NotNull
    private BigDecimal precioUnitario;
}
