package com.thiago.escenasFX.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CambiarEstadoTrabajoRequest {

    // EN_CAMINO | EN_CURSO | TERMINADO — lo valida MisTrabajosService, no esta clase: el técnico
    // nunca puede fijar AGENDADO, CERRADO_Y_COBRADO ni CANCELADO.
    @NotBlank
    private String estado;
}
