package com.thiago.escenasFX.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class NotaTrabajoResponse {
    private Integer idNota;
    private String texto;
    private String nombreEmpleado;
    private LocalDateTime creadoEn;
}
