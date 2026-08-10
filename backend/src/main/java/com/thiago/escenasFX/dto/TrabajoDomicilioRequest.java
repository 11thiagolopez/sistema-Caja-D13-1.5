package com.thiago.escenasFX.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * A diferencia de VentaRequest, "detalles" NO es obligatorio: se puede guardar un trabajo recién
 * agendado sin artículos ni mano de obra todavía (VentaService.guardarTrabajoDomicilio valida que
 * sí haya al menos uno si cerrar=true).
 */
@Getter
@Setter
public class TrabajoDomicilioRequest {

    // Presente = actualizar un trabajo existente; ausente = crear uno nuevo.
    private Integer idVenta;

    @NotNull
    private Integer idEmpleado;

    private Integer idEmpleadoTecnico;

    @NotBlank
    private String clienteNombre;

    private String clienteTelefono;
    private String direccionTrabajo;
    private String descripcionTrabajo;

    // AGENDADO | EN_CURSO | COMPLETADO — "COBRADO" lo fija el backend cuando cerrar=true.
    private String estadoTrabajo;

    @Valid
    private List<DetalleVentaRequest> detalles;

    // true = "Cerrar y cobrar" (exige al menos un ítem), false = "Guardar borrador".
    private boolean cerrar;
}
