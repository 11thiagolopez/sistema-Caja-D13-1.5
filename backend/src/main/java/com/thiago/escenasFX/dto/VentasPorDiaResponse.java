package com.thiago.escenasFX.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** mejorDia y peorDia son null si en el período no hubo ninguna venta. */
@Getter
@AllArgsConstructor
public class VentasPorDiaResponse {
    private List<PuntoGraficoDTO> puntos;
    private PuntoGraficoDTO mejorDia;
    private PuntoGraficoDTO peorDia;
}
