package com.thiago.escenasFX.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConfirmarDescuentoRequest {

    @NotNull
    private Integer idVenta;

    @NotBlank
    private String codigo;
}
