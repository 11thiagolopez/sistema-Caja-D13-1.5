package com.thiago.escenasFX.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SuscripcionPushRequest {

    @NotBlank
    private String endpoint;

    // Ambos requeridos para el push con contenido (RFC 8291); vienen de
    // PushSubscription.toJSON().keys en el navegador.
    @NotBlank
    private String p256dh;

    @NotBlank
    private String auth;
}
