package com.thiago.escenasFX.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Factura fiscal (ARCA/AFIP, WSFEv1) asociada a una Venta — acción aparte ("Facturar"), no
 * reemplaza el comprobante interno. D13 es Monotributo: siempre Factura C (tipoComprobante = 11,
 * sin discriminar IVA). Dominio propio con su propio ciclo de vida (llamada externa que puede
 * fallar y reintentarse) — no se agregó como columnas de Venta a propósito, mismo criterio que
 * CotizacionDolar.
 */
@Entity
@Table(name = "facturas_fiscales")
@Getter
@Setter
public class FacturaFiscal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_factura")
    private Integer idFactura;

    @OneToOne
    @JoinColumn(name = "id_venta", nullable = false)
    private Venta venta;

    @Column(name = "punto_venta", nullable = false)
    private Integer puntoVenta;

    // Código de comprobante AFIP: 11 = Factura C.
    @Column(name = "tipo_comprobante", nullable = false)
    private Integer tipoComprobante;

    // Null hasta que ARCA confirma el número (se calcula a partir de FECompUltimoAutorizado justo
    // antes de pedir el CAE, no antes).
    private Integer numero;

    // 80 = CUIT | 96 = DNI | 99 = Consumidor Final.
    @Column(name = "cliente_doc_tipo", nullable = false)
    private Integer clienteDocTipo;

    @Column(name = "cliente_doc_nro")
    private String clienteDocNro;

    private String cae;

    @Column(name = "cae_vencimiento")
    private LocalDate caeVencimiento;

    @Column(precision = 12, scale = 2)
    private BigDecimal importe;

    // PENDIENTE (creada, todavía no se le pidió el CAE a ARCA) | EMITIDA | ERROR.
    @Column(nullable = false)
    private String estado = "PENDIENTE";

    @Column(name = "error_detalle")
    private String errorDetalle;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn = LocalDateTime.now();
}
