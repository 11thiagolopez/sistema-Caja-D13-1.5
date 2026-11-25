package com.thiago.escenasFX.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TecnicoRequest {

    @NotBlank
    private String nombre;

    // % de la mano de obra de cada trabajo que se lleva el técnico.
    @NotNull
    @DecimalMin(value = "0")
    @DecimalMax(value = "100")
    private BigDecimal comision;

    // Ambos opcionales: sin ellos, el técnico sigue sin acceso al sistema (usuario/contraseña al
    // azar, como hasta ahora). Se completan solo para el técnico al que se le da acceso real
    // (ej. Fabián, para "Mis trabajos" desde el celular).
    private String usuario;
    private String password;
}
