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
 * Una fila por cada cambio de estado (o reprogramación, con estadoAnterior == estadoNuevo y el
 * cambio descripto en detalle) de un trabajo a domicilio. Sirve para auditoría y, más adelante,
 * para calcular el tiempo promedio por trabajo.
 */
@Entity
@Table(name = "historial_estado_trabajo")
@Getter
@Setter
public class HistorialEstadoTrabajo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_historial")
    private Integer idHistorial;

    @ManyToOne
    @JoinColumn(name = "id_venta", nullable = false)
    private Venta venta;

    @Column(name = "estado_anterior")
    private String estadoAnterior;

    @Column(name = "estado_nuevo", nullable = false)
    private String estadoNuevo;

    @ManyToOne
    @JoinColumn(name = "id_empleado", nullable = false)
    private Empleado empleado;

    private String detalle;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn = LocalDateTime.now();
}
