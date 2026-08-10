package com.thiago.escenasFX.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * O trae idProducto (línea de catálogo) o trae descripcion (ítem manual, ej. "Apertura de
 * cerradura") — PresupuestoService.crear valida que venga al menos uno de los dos.
 */
@Getter
@Setter
public class DetallePresupuestoRequest {

    private Integer idProducto;

    private String descripcion;

    @NotNull
    @Min(1)
    private Integer cantidad;

    @NotNull
    private BigDecimal precioUnitario;
}
