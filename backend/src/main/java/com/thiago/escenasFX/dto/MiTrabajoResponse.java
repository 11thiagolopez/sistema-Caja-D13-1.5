package com.thiago.escenasFX.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Vista de un trabajo a domicilio para el técnico asignado — sin datos de contacto del cliente (ver TrabajoTecnicoResponse), más sus notas y fotos. */
@Getter
@AllArgsConstructor
public class MiTrabajoResponse {
    private TrabajoTecnicoResponse trabajo;
    private List<NotaTrabajoResponse> notas;
    private List<FotoTrabajoResponse> fotos;
}
