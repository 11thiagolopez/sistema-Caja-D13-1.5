package com.thiago.escenasFX.model;

import java.math.BigDecimal;
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
 * Solicitud de retiro de caja pendiente de que un ADMIN la apruebe desde el sistema. El
 * {@link MovimientoCaja} real solo se crea al aprobarla (ver CajaService.aprobarRetiro).
 */
@Entity
@Table(name = "solicitudes_retiro")
@Getter
@Setter
public class SolicitudRetiro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_solicitud")
    private Integer idSolicitud;

    @Column(precision = 12, scale = 2, nullable = false)
    private BigDecimal monto;

    @Column(nullable = false)
    private String motivo;

    @Column(name = "medio_pago", nullable = false)
    private String medioPago;

    @ManyToOne
    @JoinColumn(name = "id_empleado_solicitante")
    private Empleado empleadoSolicitante;

    @Column(nullable = false)
    private String estado = "PENDIENTE"; // PENDIENTE | CONFIRMADA | RECHAZADA (EXPIRADA: histórico, del flujo viejo con código)

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn = LocalDateTime.now();

    // Qué ADMIN aprobó o rechazó la solicitud, y cuándo.
    @ManyToOne
    @JoinColumn(name = "id_empleado_resolutor")
    private Empleado resueltoPor;

    @Column(name = "resuelto_en")
    private LocalDateTime resueltoEn;
}
