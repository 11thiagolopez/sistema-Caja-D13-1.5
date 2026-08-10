package com.thiago.escenasFX.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EnviarComprobanteRequest {

    @NotBlank
    @Email
    private String email;
}
