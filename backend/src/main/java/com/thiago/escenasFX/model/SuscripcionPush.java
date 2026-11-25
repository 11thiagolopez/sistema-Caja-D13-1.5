package com.thiago.escenasFX.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Un dispositivo (ADMIN o TECNICO) que quiere recibir avisos. El aviso de peticiones nuevas
 * (PushService.avisarPeticionNueva) sigue yendo sin contenido y no necesita p256dh/auth; los
 * avisos de trabajos a domicilio (PushService.avisarConContenido) sí, porque van cifrados
 * (RFC 8291) con la clave pública y el secreto que el navegador generó al suscribirse. Nulables:
 * una suscripción vieja, de antes de que existiera el push con contenido, los tiene en null hasta
 * que ese dispositivo vuelva a activar las notificaciones.
 */
@Entity
@Table(name = "suscripciones_push")
@Getter
@Setter
public class SuscripcionPush {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_suscripcion")
    private Integer idSuscripcion;

    @Column(nullable = false, unique = true)
    private String endpoint;

    private String p256dh;

    private String auth;

    @ManyToOne
    @JoinColumn(name = "id_empleado", nullable = false)
    private Empleado empleado;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn = LocalDateTime.now();
}
