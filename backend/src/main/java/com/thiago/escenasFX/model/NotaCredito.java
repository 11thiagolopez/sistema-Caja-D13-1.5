package com.thiago.escenasFX.model;

import java.math.BigDecimal;
import java.time.LocalDate;
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

/**
 * Nota de crédito sobre una Venta ya CONFIRMADA — nunca modifica la venta original, es un
 * documento aparte vinculado a ella (mismo espíritu que FacturaFiscal respecto de Venta, pero no
 * puede reusar esa tabla: FacturaFiscal.venta es @OneToOne con FK única por venta).
 *
 * Fase 1 (actual): siempre interna (estado=EMITIDA, sin CAE, sin llamada a AFIP), aunque la venta
 * original tenga factura fiscal (esFiscal queda guardado para que la fase 2 sepa qué notas de
 * crédito quedaron pendientes de emitir ante ARCA).
 */
@Entity
@Table(name = "notas_credito")
@Getter
@Setter
public class NotaCredito {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_nota_credito")
    private Integer idNotaCredito;

    @ManyToOne
    @JoinColumn(name = "id_venta_original", nullable = false)
    private Venta ventaOriginal;

    @ManyToOne
    @JoinColumn(name = "id_cliente", nullable = false)
    private Cliente cliente;

    private LocalDateTime fecha = LocalDateTime.now();

    // DEVOLUCION_MERCADERIA | ERROR_TIPEO | DESCUENTO_POSTERIOR | ANULACION_TOTAL
    @Column(nullable = false)
    private String motivo;

    // TOTAL | PARCIAL
    @Column(nullable = false)
    private String alcance;

    @Column(name = "monto_total", precision = 12, scale = 2, nullable = false)
    private BigDecimal montoTotal;

    // true si la venta original tenía FacturaFiscal EMITIDA al momento de crear esta nota —
    // informativo en esta fase, la fase 2 lo usa para saber qué notas de crédito quedan
    // pendientes de emitir ante ARCA.
    @Column(name = "es_fiscal", nullable = false)
    private boolean esFiscal = false;

    // EMITIDA (interna, o ya con CAE) | PENDIENTE_FISCAL (esFiscal, falta llamar a ARCA) | ERROR
    // (ARCA rechazó o falló la conexión; reintentable). NotaCreditoService.crear() decide el
    // valor inicial según esFiscal.
    @Column(nullable = false)
    private String estado = "EMITIDA";

    // Campos de la fase fiscal (NotaCreditoFiscalService) — null mientras esFiscal=false o
    // todavía no se llamó a ARCA. Documento propio, nunca reutiliza la fila de FacturaFiscal de
    // la venta original (ver comentario de clase).
    @Column(name = "punto_venta")
    private Integer puntoVenta;

    @Column(name = "tipo_comprobante")
    private Integer tipoComprobante;

    private Integer numero;

    private String cae;

    @Column(name = "cae_vencimiento")
    private LocalDate caeVencimiento;

    @Column(name = "error_detalle")
    private String errorDetalle;

    @ManyToOne
    @JoinColumn(name = "id_empleado")
    private Empleado empleado;

    @OneToMany(mappedBy = "notaCredito", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DetalleNotaCredito> detalles = new ArrayList<>();
}
