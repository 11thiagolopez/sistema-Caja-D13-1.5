package com.thiago.escenasFX.dto;

import java.math.BigDecimal;
import java.util.List;

import com.thiago.escenasFX.model.MovimientoCaja;
import com.thiago.escenasFX.model.Venta;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ResumenDiaDTO {

    private List<Venta> ventas;
    private List<MovimientoCaja> retiros;

    private BigDecimal montoInicial;

    private BigDecimal ventasEfectivo;
    private BigDecimal ventasTransferencia;
    private BigDecimal ventasTarjeta;

    private BigDecimal retirosEfectivo;
    private BigDecimal retirosTransferencia;

    private BigDecimal efectivoFinal;
    private BigDecimal totalDigital;
    private BigDecimal cajaTotalDelDia;
}
