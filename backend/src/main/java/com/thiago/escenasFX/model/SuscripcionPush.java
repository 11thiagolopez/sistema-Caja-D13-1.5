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
 * Un dispositivo de un ADMIN que quiere recibir el aviso de peticiones nuevas. Solo se guarda el
 * endpoint: las notificaciones van sin contenido (ver PushService), así que no hacen falta las
 * claves de cifrado de la suscripción.
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

    @ManyToOne
    @JoinColumn(name = "id_empleado", nullable = false)
    private Empleado empleado;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn = LocalDateTime.now();
}
