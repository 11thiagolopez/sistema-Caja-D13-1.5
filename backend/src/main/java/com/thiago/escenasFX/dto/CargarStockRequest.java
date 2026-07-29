package com.thiago.escenasFX.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CargarStockRequest {

    @NotBlank
    private String codigo;

    @NotNull
    @Min(1)
    private Integer cantidad;
}
