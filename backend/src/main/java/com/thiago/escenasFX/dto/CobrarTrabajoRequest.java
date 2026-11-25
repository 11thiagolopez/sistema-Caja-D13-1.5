package com.thiago.escenasFX.dto;

import jakarta.validation.constraints.NotBlank;

/** "Cerrar y cobrar" de un trabajo ya Terminado: los ítems no se tocan, solo se elige el medio. */
public record CobrarTrabajoRequest(@NotBlank String medioPago) {
}
