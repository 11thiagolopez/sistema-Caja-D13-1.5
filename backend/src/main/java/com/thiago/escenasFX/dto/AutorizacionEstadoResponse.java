package com.thiago.escenasFX.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AutorizacionEstadoResponse {
    private String tipo; // RETIRO | DESCUENTO
    private Integer id;
    private String estado; // PENDIENTE | APROBADA | RECHAZADA
}
