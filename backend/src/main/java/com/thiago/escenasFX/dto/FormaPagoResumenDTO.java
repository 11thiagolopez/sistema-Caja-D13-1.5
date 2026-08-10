package com.thiago.escenasFX.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FormaPagoResumenDTO {
    private String medioPago;
    private long cantidadVentas;
    private BigDecimal totalFacturado;
}
