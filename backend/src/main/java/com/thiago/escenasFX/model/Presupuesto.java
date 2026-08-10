package com.thiago.escenasFX.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

// Cotización informativa: a diferencia de Venta, nunca toca stock ni caja (ver PresupuestoService).
@Entity
@Table(name = "presupuestos")
@Getter
@Setter
public class Presupuesto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_presupuesto")
    private Integer idPresupuesto;

    private LocalDateTime fecha = LocalDateTime.now();

    @ManyToOne
    @JoinColumn(name = "id_empleado")
    private Empleado empleado;

    @Column(name = "cliente_nombre")
    private String clienteNombre;

    @Column(name = "cliente_email")
    private String clienteEmail;

    @Column(name = "cliente_telefono")
    private String clienteTelefono;

    @Column(name = "total_presupuesto", precision = 12, scale = 2)
    private BigDecimal totalPresupuesto = BigDecimal.ZERO;

    @Column(name = "enviado_por_email")
    private boolean enviadoPorEmail = false;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn = LocalDateTime.now();

    @OneToMany(mappedBy = "presupuesto", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DetallePresupuesto> detalles = new ArrayList<>();
}
