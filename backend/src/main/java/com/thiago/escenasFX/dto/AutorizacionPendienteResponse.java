package com.thiago.escenasFX.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AutorizacionPendienteResponse {
    private String tipo; // RETIRO | DESCUENTO
    private Integer id; // idSolicitud o idVenta, según tipo
    private BigDecimal monto;
    private String motivo;
    private String solicitante;
    private LocalDateTime fecha;
    private String medioPago;
    private BigDecimal totalVenta; // solo DESCUENTO: total ya con el descuento aplicado
}
