package com.thiago.escenasFX.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class VentaResponse {
    private Integer idVenta;
    private LocalDateTime fecha;
    private Integer idEmpleado;
    private String medioPago;
    private String tipoComprobante;
    private BigDecimal totalVenta;
    private BigDecimal descuento;
    private String estado;
    private String clienteEmail;
    private boolean comprobanteEnviadoPorEmail;
    private String tipoVenta;
    private String clienteNombre;
    private String clienteTelefono;
    private String direccionTrabajo;
    private String descripcionTrabajo;
    private String estadoTrabajo;
    private LocalDate fechaProgramada;
    private Integer idEmpleadoTecnico;
    private String nombreTecnico;
    private BigDecimal comisionTecnico;
    // Cliente del catálogo (cuenta corriente, facturación) — distinto de clienteNombre/clienteTelefono
    // arriba, que son texto libre de Trabajo a domicilio. Null si la venta no tiene cliente vinculado.
    private Integer idCliente;
    private String nombreCliente;
    private BigDecimal montoSaldoAplicado;
    // Recargo de tarjeta (crédito/débito en Cobros): fuera de totalVenta. totalCobrado = lo que pagó
    // el cliente, que es por lo que salen ticket y factura.
    private BigDecimal recargoTarjeta;
    private BigDecimal porcentajeRecargo;
    private BigDecimal totalCobrado;
    private List<DetalleVentaResponse> detalles;
    // SIN_FACTURAR | FACTURADA. Los tres de facturación son null en el arqueo y los trabajos, que no
    // los usan (ver VentaConFacturacion). Con un intento rechazado, sigue SIN_FACTURAR y factura trae
    // el error para reintentar.
    private String estadoFactura;
    private FacturaFiscalResponse factura;
    private Boolean tieneNotaCredito;
}
