package com.thiago.escenasFX.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * O trae idProducto (línea de catálogo, descuenta stock) o trae descripcion (ítem manual/trabajo,
 * ej. "Apertura de cerradura", no toca stock) — VentaController.registrar valida que venga al
 * menos uno de los dos.
 */
@Getter
@Setter
public class DetalleVentaRequest {

    private Integer idProducto;

    private String descripcion;

    // ARTICULO | COPIA — categoría informativa, no afecta el cálculo de costo en Reportes.
    private String tipo;

    @Min(1)
    private int cantidad;

    @NotNull
    private BigDecimal precioUnitario;
}
