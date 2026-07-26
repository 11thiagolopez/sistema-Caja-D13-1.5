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

@Entity
@Table(name = "ventas")
@Getter
@Setter
public class Venta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_venta")
    private Integer idVenta;

    private LocalDateTime fecha = LocalDateTime.now();

    @ManyToOne
    @JoinColumn(name = "id_empleado")
    private Empleado empleado;

    // Turno/caja bajo el que se registró la venta (nullable: puede no haber una caja abierta).
    // Permite el arqueo por turno individual además del arqueo por rango de fechas.
    @ManyToOne
    @JoinColumn(name = "id_sesion")
    private SesionCaja sesion;

    @Column(name = "medio_pago")
    private String medioPago;

    @Column(name = "tipo_comprobante")
    private String tipoComprobante;

    @Column(name = "total_venta", precision = 12, scale = 2)
    private BigDecimal totalVenta = BigDecimal.ZERO;

    @Column(nullable = false)
    private String estado = "CONFIRMADA"; // CONFIRMADA | PENDIENTE_AUTORIZACION

    @Column(precision = 12, scale = 2, nullable = false)
    private BigDecimal descuento = BigDecimal.ZERO;

    @Column(name = "motivo_descuento")
    private String motivoDescuento;

    @Column(name = "otp_hash")
    private String otpHash;

    @Column(name = "otp_expira_en")
    private LocalDateTime otpExpiraEn;

    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DetalleVenta> detalles = new ArrayList<>();
}
