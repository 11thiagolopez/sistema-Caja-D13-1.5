package com.thiago.escenasFX.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ResumenRangoDTO {
    private LocalDate desde;
    private LocalDate hasta;
    private ResumenDiaDTO total;
    private List<ResumenSesionDTO> sesiones;
}
