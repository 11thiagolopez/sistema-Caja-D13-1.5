package com.thiago.escenasFX.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PresupuestoResponse {
    private Integer idPresupuesto;
    private LocalDateTime fecha;
    private Integer idEmpleado;
    private String nombreEmpleado;
    private String clienteNombre;
    private String clienteEmail;
    private String clienteTelefono;
    private BigDecimal totalPresupuesto;
    private boolean enviadoPorEmail;
    private List<DetallePresupuestoResponse> detalles;
}
