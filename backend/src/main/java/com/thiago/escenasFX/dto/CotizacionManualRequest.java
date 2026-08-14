package com.thiago.escenasFX.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CotizacionManualRequest {

    @NotNull
    @Positive
    private BigDecimal valorVenta;
}
