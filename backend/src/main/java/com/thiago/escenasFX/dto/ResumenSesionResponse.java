package com.thiago.escenasFX.dto;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ResumenSesionResponse {
    private Integer idSesion;
    private LocalDate fecha;
    private String estado;
    private String empleadoApertura;
    private ResumenDiaResponse resumen;
}
