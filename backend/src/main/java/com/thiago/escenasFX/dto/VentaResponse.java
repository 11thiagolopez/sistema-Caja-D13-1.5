package com.thiago.escenasFX.dto;

import java.math.BigDecimal;
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
    private Integer idEmpleadoTecnico;
    private String nombreTecnico;
    // Cliente del catálogo nuevo (cuenta corriente) — distinto de clienteNombre/clienteTelefono
    // arriba, que son texto libre de Trabajo a domicilio. Null si la venta no tiene cliente
    // vinculado (hoy el caso normal en Cobros, que todavía no lo pide).
    private Integer idCliente;
    private String nombreCliente;
    private BigDecimal montoSaldoAplicado;
    private List<DetalleVentaResponse> detalles;
}
