package com.thiago.escenasFX.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class NotificacionResponse {
    private Integer idNotificacion;
    private String tipo;
    private String texto;
    private Integer idVenta;
    private boolean leida;
    private LocalDateTime creadaEn;
}
