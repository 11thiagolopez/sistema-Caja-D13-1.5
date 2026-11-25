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
 * Notas sueltas de un trabajo a domicilio (ej. "falta un repuesto", "el cliente no estaba"), las
 * carga el jefe o el técnico. Es una lista append-only, no un campo que se pisa.
 */
@Entity
@Table(name = "notas_trabajo")
@Getter
@Setter
public class NotaTrabajo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_nota")
    private Integer idNota;

    @ManyToOne
    @JoinColumn(name = "id_venta", nullable = false)
    private Venta venta;

    @ManyToOne
    @JoinColumn(name = "id_empleado", nullable = false)
    private Empleado empleado;

    private String texto;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn = LocalDateTime.now();
}
