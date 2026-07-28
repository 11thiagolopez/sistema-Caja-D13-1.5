package com.thiago.escenasFX.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VentaRequest {

    @NotNull
    private Integer idEmpleado;

    @NotBlank
    private String medioPago;

    private String tipoComprobante;

    @NotEmpty
    @Valid
    private List<DetalleVentaRequest> detalles;

    // Descuento manual opcional: si es > 0, la venta queda PENDIENTE_AUTORIZACION y dispara
    // un OTP por email al ADMIN (ver VentaService.registrarVenta). Requiere motivoDescuento.
    private BigDecimal descuento;

    private String motivoDescuento;
}
