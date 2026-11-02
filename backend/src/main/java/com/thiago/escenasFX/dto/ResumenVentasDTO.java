package com.thiago.escenasFX.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ResumenVentasDTO {
    private BigDecimal totalVendido;
    private long cantidadVentas;
    private BigDecimal ticketPromedio;
    private long unidadesVendidas;
}
