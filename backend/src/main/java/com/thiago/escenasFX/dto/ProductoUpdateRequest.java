package com.thiago.escenasFX.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
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

    @DecimalMin(value = "0.01")
    private BigDecimal precioVenta;

    @DecimalMin(value = "0.01")
    private BigDecimal precioCompra;

    @Min(0)
    private Integer stockActual;

    // A diferencia de rubro/marca-código, el código de fábrica sí se puede cargar o corregir
    // después del alta: muchos productos se dan de alta sin haber escaneado todavía el envoltorio,
    // o el dato se tipeó mal. String vacío ("") limpia el código de fábrica (el producto vuelve a
    // usar codigoInterno como codigoBarras) — ver ProductoService.actualizar.
    private String codigoFabrica;
}
