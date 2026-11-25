package com.thiago.escenasFX.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NotaTrabajoRequest {

    @NotBlank
    private String texto;
}
