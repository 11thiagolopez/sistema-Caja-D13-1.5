package com.thiago.escenasFX.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {

    @NotBlank
    @Size(max = 100)
    private String usuario;

    @NotBlank
    @Size(max = 100)
    private String password;
}
