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
 * Solicitud de retiro de caja pendiente de autorización por OTP. El {@link MovimientoCaja}
 * real solo se crea cuando la solicitud se confirma con el código enviado por email
 * (ver CajaService.confirmarRetiro).
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

    @Column(name = "otp_hash", nullable = false)
    private String otpHash;

    @Column(name = "otp_expira_en", nullable = false)
    private LocalDateTime otpExpiraEn;

    @Column(nullable = false)
    private String estado = "PENDIENTE"; // PENDIENTE | CONFIRMADA | EXPIRADA

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn = LocalDateTime.now();
}
