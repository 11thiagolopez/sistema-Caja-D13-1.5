package com.thiago.escenasFX.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Vista de un trabajo a domicilio para el técnico asignado — sin datos de contacto del cliente (ver TrabajoTecnicoResponse), más sus notas, fotos y archivos adjuntos (videos y audios). */
@Getter
@AllArgsConstructor
public class MiTrabajoResponse {
    private TrabajoTecnicoResponse trabajo;
    private List<NotaTrabajoResponse> notas;
    private List<FotoTrabajoResponse> fotos;
    private List<ArchivoTrabajoResponse> archivos;
}
