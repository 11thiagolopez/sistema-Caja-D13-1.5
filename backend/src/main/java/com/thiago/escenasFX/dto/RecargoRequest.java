package com.thiago.escenasFX.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RecargoRequest {

    @NotNull
    private BigDecimal porcentaje;
}
