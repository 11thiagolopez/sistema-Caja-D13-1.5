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
    private String estado = "CONFIRMADA"; // CONFIRMADA | PENDIENTE_AUTORIZACION | EN_PROGRESO | ANULADA

    @Column(precision = 12, scale = 2, nullable = false)
    private BigDecimal descuento = BigDecimal.ZERO;

    @Column(name = "motivo_descuento")
    private String motivoDescuento;

    // Qué ADMIN aprobó o rechazó el descuento, y cuándo (null si la venta no pasó por autorización).
    @ManyToOne
    @JoinColumn(name = "id_empleado_resolutor")
    private Empleado resueltoPor;

    @Column(name = "resuelto_en")
    private LocalDateTime resueltoEn;

    // Se completa recién al enviar el comprobante por email (no se pide al registrar la venta).
    @Column(name = "cliente_email")
    private String clienteEmail;

    @Column(name = "comprobante_enviado_por_email")
    private boolean comprobanteEnviadoPorEmail = false;

    // MOSTRADOR (venta de catálogo, flujo de Cobros) | DOMICILIO (trabajo a domicilio).
    @Column(name = "tipo_venta", nullable = false)
    private String tipoVenta = "MOSTRADOR";

    // Campos de trabajo a domicilio: solo se completan cuando tipoVenta = "DOMICILIO".
    @Column(name = "cliente_nombre")
    private String clienteNombre;

    @Column(name = "cliente_telefono")
    private String clienteTelefono;

    @Column(name = "direccion_trabajo")
    private String direccionTrabajo;

    @Column(name = "descripcion_trabajo")
    private String descripcionTrabajo;

    // AGENDADO | EN_CURSO | COMPLETADO | COBRADO
    @Column(name = "estado_trabajo")
    private String estadoTrabajo;

    // El día de la agenda en el que se hace el trabajo. La plata se cuenta por "fecha", que al
    // cerrar y cobrar pasa a ser el momento del cobro.
    @Column(name = "fecha_programada")
    private LocalDate fechaProgramada;

    @ManyToOne
    @JoinColumn(name = "id_empleado_tecnico")
    private Empleado empleadoTecnico;

    // El % del técnico sobre la mano de obra, guardado al cobrar el trabajo.
    @Column(name = "comision_tecnico", precision = 5, scale = 2)
    private BigDecimal comisionTecnico;

    // Nullable: ninguna venta existente lo tiene todavía. Lo usa Nota de Crédito para "heredar"
    // el cliente de la venta cuando esté disponible, y Cobros lo pide cuando se paga (total o
    // parcialmente) con el saldo de cuenta corriente del cliente (ver montoSaldoAplicado).
    @ManyToOne
    @JoinColumn(name = "id_cliente")
    private Cliente cliente;

    // Cuánto de esta venta se pagó con el saldo a favor del cliente (null/no aplica si no se usó
    // saldo). VentaService.registrarVenta lo valida contra el saldo disponible y crea el
    // MovimientoCuentaCorrienteCliente DEBITO correspondiente.
    @Column(name = "monto_saldo_aplicado", precision = 12, scale = 2)
    private BigDecimal montoSaldoAplicado;

    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DetalleVenta> detalles = new ArrayList<>();
}
