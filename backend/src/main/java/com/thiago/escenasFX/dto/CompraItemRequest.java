package com.thiago.escenasFX.dto;

import java.math.BigDecimal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * O trae idProducto (repone stock/precio de un producto existente) o trae nuevoProducto (lo da
 * de alta en el momento) — CompraService.registrarCompra valida que venga exactamente uno de
 * los dos.
 */
@Getter
@Setter
public class CompraItemRequest {

    private Integer idProducto;

    @Valid
    private NuevoProductoEnCompraRequest nuevoProducto;

    @NotNull
    @Min(1)
    private Integer cantidad;

    @NotNull
    private BigDecimal precioCompraUnitario;

    private BigDecimal precioVentaUnitario;
}
