package com.thiago.escenasFX.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SolicitudRetiroResponse {
    private Integer idSolicitud;
    private BigDecimal monto;
    private String motivo;
    private String medioPago;
    private String estado;
    private LocalDateTime otpExpiraEn;
}
