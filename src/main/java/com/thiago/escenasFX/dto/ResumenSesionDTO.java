package com.thiago.escenasFX.dto;

import com.thiago.escenasFX.model.SesionCaja;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ResumenSesionDTO {
    private SesionCaja sesion;
    private ResumenDiaDTO resumen;
}
