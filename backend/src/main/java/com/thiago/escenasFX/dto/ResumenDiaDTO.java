package com.thiago.escenasFX.dto;

import java.math.BigDecimal;
import java.util.List;

import com.thiago.escenasFX.model.Gasto;
import com.thiago.escenasFX.model.MovimientoCaja;
import com.thiago.escenasFX.model.MovimientoCuentaCorrienteProveedor;
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

    // Informativo, no suma a cajaTotalDelDia: cuánto de las ventas del período se pagó con saldo
    // de cuenta corriente (total o parcialmente) en vez de dinero real.
    private BigDecimal ventasPagadasConSaldo;

    // Todo pago a proveedor y compra a crédito del período, salgan o no de la caja. Solo los
    // pagos con origen CAJA de abajo descuentan algo.
    private List<MovimientoCuentaCorrienteProveedor> movimientosProveedor;
    private BigDecimal pagosProveedorEfectivoCaja;
    private BigDecimal pagosProveedorTransferenciaCaja;

    // Igual que los movimientos de proveedor: figuran todos, solo descuentan los de origen CAJA.
    private List<Gasto> gastos;
    private BigDecimal gastosEfectivoCaja;
    private BigDecimal gastosTransferenciaCaja;

    // Fuera del arqueo: lo cobrado en trabajos a domicilio, y eso más lo cobrado en mostrador.
    private BigDecimal totalTrabajosDomicilio;
    private BigDecimal totalDelDia;

    // Lo que los clientes pagaron de su cuenta corriente: dinero real, entra al arqueo por su medio.
    private BigDecimal pagosClienteEfectivo;
    private BigDecimal pagosClienteTransferencia;
    private BigDecimal pagosClienteTarjeta;

    // Informativo, no suma a cajaTotalDelDia: lo vendido en cuenta corriente, que todavía no se cobró.
    private BigDecimal ventasEnCuentaCorriente;
}
