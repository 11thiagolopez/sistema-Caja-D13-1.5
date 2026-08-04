package com.thiago.escenasFX.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * Datos para dar de alta un producto nuevo directamente desde un renglón de la grilla de
 * Compras (cuando el nombre tipeado no matchea ningún producto existente). precioCompra,
 * precioVenta y stock inicial salen de los otros campos del renglón (CompraItemRequest), no de
 * acá, para no duplicar esos valores.
 */
@Getter
@Setter
public class NuevoProductoEnCompraRequest {

    @NotBlank
    @Pattern(regexp = "\\d{2}")
    private String rubro;

    @NotBlank
    @Pattern(regexp = "\\d{2}")
    private String familia;

    @NotBlank
    private String marca;

    @NotBlank
    private String descripcion;

    private String codigoFabrica;
}
