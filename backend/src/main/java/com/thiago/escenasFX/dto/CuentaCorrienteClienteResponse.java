package com.thiago.escenasFX.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CuentaCorrienteClienteResponse {
    private Integer idCliente;
    private String nombreCliente;
    private BigDecimal saldoActual;
    private List<MovimientoCuentaCorrienteClienteResponse> movimientos;
}
