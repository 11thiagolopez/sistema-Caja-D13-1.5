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
 * La "campanita": queda igual se haya podido mandar el push o no (PushService es best-effort),
 * así nunca se pierde un aviso por una suscripción vencida o sin claves de cifrado.
 */
@Entity
@Table(name = "notificaciones")
@Getter
@Setter
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_notificacion")
    private Integer idNotificacion;

    // Destinatario.
    @ManyToOne
    @JoinColumn(name = "id_empleado", nullable = false)
    private Empleado empleado;

    // TRABAJO_NUEVO | REPROGRAMADO | CANCELADO | EN_CAMINO | LLEGO | TERMINADO | NOTA
    private String tipo;

    private String texto;

    @ManyToOne
    @JoinColumn(name = "id_venta")
    private Venta venta;

    private boolean leida = false;

    @Column(name = "creada_en")
    private LocalDateTime creadaEn = LocalDateTime.now();
}
