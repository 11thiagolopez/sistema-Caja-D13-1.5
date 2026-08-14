package com.thiago.escenasFX.model;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

/**
 * Reemplaza la tabla {@code sesiones} que en el sistema original vivía en el SQLite local:
 * registra la apertura/cierre de la caja diaria y el monto inicial declarado.
 * Requiere crear la tabla "sesiones_caja" en Supabase antes de levantar la app
 * (ver plan-migracion.md, sección 8).
 */
@Entity
@Table(name = "sesiones_caja")
@Getter
@Setter
public class SesionCaja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_sesion")
    private Integer idSesion;

    private LocalDate fecha = LocalDate.now();

    @Column(name = "monto_inicial", precision = 12, scale = 2)
    private BigDecimal montoInicial;

    private String estado = "ABIERTA"; // ABIERTA | CERRADA

    // Cotización USD venta usada para el recálculo masivo de precios al abrir esta sesión
    // (dolarización). Nullable: sesiones de antes de esta funcionalidad no la tienen.
    @Column(name = "cotizacion_usd_venta", precision = 12, scale = 2)
    private BigDecimal cotizacionUsdVenta;

    // No persistido: cantidad de productos repriceados en esta apertura (dolarización), sólo
    // presente en el objeto que devuelve CajaService.abrirSesion para informar al ADMIN.
    @Transient
    private int productosActualizados;

    @ManyToOne
    @JoinColumn(name = "id_empleado_apertura")
    private Empleado empleadoApertura;
}
