package com.thiago.escenasFX.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CompraRequest {

    @NotNull
    private Integer idEmpleado;

    @NotNull
    private LocalDate fecha;

    @NotBlank
    private String proveedorNombre;

    @NotBlank
    private String medioPago;

    // CAJA | EXTERNO. Obligatorio si se paga algo al registrar la compra; se ignora si no.
    private String origen;

    @NotEmpty
    @Valid
    private List<CompraItemRequest> items;

    // Cuenta corriente de proveedor, nuevo: ausente o null = se paga todo de una (comportamiento
    // de siempre); un valor menor al total deja el resto como deuda pendiente con el proveedor,
    // pagable después con POST /api/compras/{id}/pagos.
    private BigDecimal montoPagado;
}
