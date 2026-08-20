package com.thiago.escenasFX.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FacturaFiscalResponse {
    private Integer idFactura;
    private Integer idVenta;
    private Integer puntoVenta;
    private Integer tipoComprobante;
    private Integer numero;
    private Integer clienteDocTipo;
    private String clienteDocNro;
    private String cae;
    private LocalDate caeVencimiento;
    private BigDecimal importe;
    private String estado;
    private String errorDetalle;
}
