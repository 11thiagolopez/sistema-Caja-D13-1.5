package com.thiago.escenasFX.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DetalleDevolucionProveedorRequest {

    @NotNull
    private Integer idProducto;

    @Min(1)
    private int cantidad;

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal precioUnitario;
}
