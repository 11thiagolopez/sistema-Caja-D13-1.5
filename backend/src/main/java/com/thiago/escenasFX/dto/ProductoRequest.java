package com.thiago.escenasFX.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductoRequest {

    @NotBlank
    @Pattern(regexp = "\\d{2}")
    private String rubro;

    @NotBlank
    @Pattern(regexp = "\\d{2}")
    private String familia;

    @NotBlank
    @Pattern(regexp = "\\d{2}")
    private String marca;

    @NotBlank
    private String proveedor;

    // Opcional: se puede tipear a mano o completar escaneando el código de barras del fabricante.
    private String codigoFabrica;

    @NotBlank
    private String descripcion;

    @NotNull
    private BigDecimal precioVenta;

    private BigDecimal precioCompra;

    @NotNull
    @Min(0)
    private Integer stockActual;
}
