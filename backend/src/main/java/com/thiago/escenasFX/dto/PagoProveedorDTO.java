package com.thiago.escenasFX.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PagoProveedorDTO {
    private Integer idProveedor;
    private String nombreProveedor;
    private BigDecimal totalPagado;
    private long cantidadCompras;
}
