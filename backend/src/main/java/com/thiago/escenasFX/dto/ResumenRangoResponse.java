package com.thiago.escenasFX.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ResumenRangoResponse {
    private LocalDate desde;
    private LocalDate hasta;
    private ResumenDiaResponse total;
    private List<ResumenSesionResponse> sesiones;
}
