package com.thiago.escenasFX.model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * La comisión que cobra el posnet por medio de pago (TARJETA_CREDITO, TARJETA_DEBITO). La edita el
 * ADMIN; si un medio no tiene fila, RecargoService usa el valor por defecto.
 */
@Entity
@Table(name = "recargos_medio_pago")
@Getter
@Setter
public class RecargoMedioPago {

    @Id
    @Column(name = "medio_pago")
    private String medioPago;

    @Column(precision = 5, scale = 2, nullable = false)
    private BigDecimal porcentaje;
}
