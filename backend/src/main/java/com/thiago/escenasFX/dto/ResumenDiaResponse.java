package com.thiago.escenasFX.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ResumenDiaResponse {
    private List<VentaResponse> ventas;
    private List<MovimientoCajaResponse> retiros;

    private BigDecimal montoInicial;

    private BigDecimal ventasEfectivo;
    private BigDecimal ventasTransferencia;
    private BigDecimal ventasTarjeta;

    private BigDecimal retirosEfectivo;
    private BigDecimal retirosTransferencia;

    private BigDecimal efectivoFinal;
    private BigDecimal totalDigital;
    private BigDecimal cajaTotalDelDia;
    private BigDecimal ventasPagadasConSaldo;

    private List<MovimientoProveedorArqueoResponse> movimientosProveedor;
    private BigDecimal pagosProveedorEfectivoCaja;
    private BigDecimal pagosProveedorTransferenciaCaja;

    private List<GastoArqueoResponse> gastos;
    private BigDecimal gastosEfectivoCaja;
    private BigDecimal gastosTransferenciaCaja;

    private BigDecimal totalTrabajosDomicilio;
    private BigDecimal totalDelDia;

    private BigDecimal pagosClienteEfectivo;
    private BigDecimal pagosClienteTransferencia;
    private BigDecimal pagosClienteTarjeta;
    private BigDecimal ventasEnCuentaCorriente;
}
