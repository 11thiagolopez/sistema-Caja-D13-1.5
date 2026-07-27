package com.thiago.escenasFX.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConfirmarRetiroRequest {

    @NotNull
    private Integer idSolicitud;

    @NotBlank
    private String codigo;
}
