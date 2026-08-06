package com.thiago.escenasFX.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

/**
 * Edición en línea desde la tabla de Productos: cada campo es opcional, solo se actualiza el
 * que venga no-nulo (semántica PATCH). No incluye rubro/marca-código/proveedor porque esos
 * forman parte de codigoInterno o de la resolución de catálogos — cambiarlos ahí rompería esa
 * identidad; para eso sigue existiendo el alta de un producto nuevo.
 */
@Getter
@Setter
public class ProductoUpdateRequest {

    private String descripcion;

    private String marca;

    private BigDecimal precioVenta;

    @Min(0)
    private Integer stockActual;
}
