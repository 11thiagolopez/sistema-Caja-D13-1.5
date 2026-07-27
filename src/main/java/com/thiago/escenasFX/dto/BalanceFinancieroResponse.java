package com.thiago.escenasFX.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BalanceFinancieroResponse {
    private LocalDate desde;
    private LocalDate hasta;
    private BigDecimal ingresosPorVentas;
    private BigDecimal costoMercaderia;
    private BigDecimal gastosOperativos;
    private BigDecimal gananciaNeta;
}
