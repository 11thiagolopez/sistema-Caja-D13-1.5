package com.thiago.escenasFX.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class NotaCreditoResponse {
    private Integer idNotaCredito;
    private Integer idVentaOriginal;
    private Integer idCliente;
    private String nombreCliente;
    private LocalDateTime fecha;
    private String motivo;
    private String alcance;
    private BigDecimal montoTotal;
    private boolean esFiscal;
    // EMITIDA | PENDIENTE_FISCAL (esFiscal=true, falta llamar a ARCA) | ERROR (reintentable).
    private String estado;
    private Integer puntoVenta;
    private Integer tipoComprobante;
    private Integer numero;
    private String cae;
    private LocalDate caeVencimiento;
    private String errorDetalle;
    private List<DetalleNotaCreditoResponse> detalles;
}
