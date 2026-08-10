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
    private List<DetalleVentaResponse> detalles;
}
