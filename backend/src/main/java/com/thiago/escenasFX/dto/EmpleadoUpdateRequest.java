package com.thiago.escenasFX.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** Igual que EmpleadoRequest pero con password opcional: solo se re-hashea si viene no vacío. */
@Getter
@Setter
public class EmpleadoUpdateRequest {

    @NotBlank
    private String nombre;

    @NotBlank
    private String usuario;

    private String password;

    private String email;

    @NotBlank
    @Pattern(regexp = "ADMIN|VENDEDOR")
    private String rol;

    @DecimalMin(value = "0")
    @DecimalMax(value = "100")
    private BigDecimal comision;
}
