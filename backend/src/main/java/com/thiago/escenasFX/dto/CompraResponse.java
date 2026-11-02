package com.thiago.escenasFX.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CompraResponse {
    private Integer idCompra;
    private LocalDate fecha;
    private Integer idProveedor;
    private String nombreProveedor;
    private String medioPago;
    private String origen;
    private BigDecimal totalCompra;
    private BigDecimal montoPagado;
    private BigDecimal saldoPendiente;
    private List<CompraItemResponse> items;
}
