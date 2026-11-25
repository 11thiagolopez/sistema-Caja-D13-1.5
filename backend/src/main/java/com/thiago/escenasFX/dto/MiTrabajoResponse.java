package com.thiago.escenasFX.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Vista de un trabajo a domicilio para el técnico asignado — mismos datos que VentaResponse, más sus notas. */
@Getter
@AllArgsConstructor
public class MiTrabajoResponse {
    private VentaResponse trabajo;
    private List<NotaTrabajoResponse> notas;
}
