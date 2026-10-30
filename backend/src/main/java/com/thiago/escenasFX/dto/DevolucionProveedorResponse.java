package com.thiago.escenasFX.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DevolucionProveedorResponse {
    private Integer idDevolucion;
    private Integer idProveedor;
    private String nombreProveedor;
    private Integer idCompraOriginal;
    private LocalDateTime fecha;
    private String motivo;
    private BigDecimal montoTotal;
    private List<DetalleDevolucionProveedorResponse> detalles;
}
