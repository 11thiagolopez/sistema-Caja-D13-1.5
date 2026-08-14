package com.thiago.escenasFX.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Cotización del dólar oficial VENTA (Banco Nación) usada para dolarizar productos. Una fila por
 * consulta exitosa (a la API o cargada a mano) — no se pisa la anterior, así queda historial de
 * qué cotización se usó cada día.
 */
@Entity
@Table(name = "cotizaciones_dolar")
@Getter
@Setter
public class CotizacionDolar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cotizacion")
    private Integer idCotizacion;

    private LocalDate fecha;

    @Column(name = "valor_venta", precision = 12, scale = 2)
    private BigDecimal valorVenta;

    // 'dolarapi.com' | 'dolar-bna' | 'MANUAL'
    private String fuente;

    private boolean manual;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn = LocalDateTime.now();
}
