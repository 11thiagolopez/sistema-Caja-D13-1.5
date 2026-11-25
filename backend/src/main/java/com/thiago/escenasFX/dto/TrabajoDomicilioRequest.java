package com.thiago.escenasFX.dto;

import java.time.LocalDate;
import java.time.LocalTime;
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

    // AGENDADO | EN_CAMINO | EN_CURSO | TERMINADO — "CERRADO_Y_COBRADO" lo fija el backend cuando
    // cerrar=true, y "CANCELADO" se fija solo al anular el trabajo (VentaController endpoint aparte).
    private String estadoTrabajo;

    // Día de la agenda. Sin valor: un trabajo nuevo va a hoy y uno existente conserva el que tenía.
    private LocalDate fechaProgramada;

    // Franja horaria agendada, ambos opcionales.
    private LocalTime horaDesde;
    private LocalTime horaHasta;

    // Instrucciones del jefe para el técnico (ej. "tocar timbre del portero").
    private String observacionesTecnico;

    // EFECTIVO | TRANSFERENCIA | TARJETA. Obligatorio si cerrar=true: la caja suma por medio de pago.
    private String medioPago;

    @Valid
    private List<DetalleVentaRequest> detalles;

    // true = "Cerrar y cobrar" (exige al menos un ítem), false = "Guardar borrador".
    private boolean cerrar;
}
