package com.thiago.escenasFX.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thiago.escenasFX.dto.DetalleVentaRequest;
import com.thiago.escenasFX.dto.TrabajoDomicilioRequest;
import com.thiago.escenasFX.model.Cliente;
import com.thiago.escenasFX.model.DetalleVenta;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.MovimientoCuentaCorrienteCliente;
import com.thiago.escenasFX.model.Producto;
import com.thiago.escenasFX.model.Venta;
import com.thiago.escenasFX.repository.ClienteRepository;
import com.thiago.escenasFX.repository.EmpleadoRepository;
import com.thiago.escenasFX.repository.FacturaFiscalRepository;
import com.thiago.escenasFX.repository.MovimientoCuentaCorrienteClienteRepository;
import com.thiago.escenasFX.repository.NotaCreditoRepository;
import com.thiago.escenasFX.repository.ProductoRepository;
import com.thiago.escenasFX.repository.SesionCajaRepository;
import com.thiago.escenasFX.repository.VentaRepository;

@Service
public class VentaService {

    private static final String NOMBRE_LOCAL = "D13 Distribuidora";
    private static final String MEDIO_PAGO_SALDO = "SALDO_CUENTA_CORRIENTE";
    private static final String MEDIO_PAGO_CUENTA_CORRIENTE = "CUENTA_CORRIENTE";
    // Cobros ya no acepta "TARJETA" a secas: crédito y débito llevan recargos distintos. Trabajos a
    // domicilio y pagos de clientes siguen usando "TARJETA" (MEDIOS_DE_COBRO), sin recargo.
    private static final List<String> MEDIOS_PAGO_VALIDOS = List.of(
        "EFECTIVO", "TRANSFERENCIA", RecargoService.TARJETA_CREDITO, RecargoService.TARJETA_DEBITO,
        MEDIO_PAGO_CUENTA_CORRIENTE, MEDIO_PAGO_SALDO);
    private static final Set<String> MEDIOS_DE_COBRO = Set.of("EFECTIVO", "TRANSFERENCIA", "TARJETA");
    // "CERRADO_Y_COBRADO" lo fija guardarTrabajoDomicilio solo con cerrar=true, y "CANCELADO" solo
    // anularTrabajoDomicilio: ninguno de los dos se acepta en el estadoTrabajo libre del request.
    // TERMINADO lo fija solo el jefe y BLOQUEA el trabajo (no se edita más; queda "Cerrar y cobrar" o
    // "Reabrir"). TERMINADO_TECNICO es el aviso del técnico ("terminé"): solo información para el jefe,
    // no bloquea nada. Decisión del dueño, plan-migracion.md sección 37.
    static final String ESTADO_TERMINADO = "TERMINADO";
    static final String ESTADO_TERMINADO_TECNICO = "TERMINADO_TECNICO";
    private static final Set<String> ESTADOS_DE_BORRADOR =
        Set.of("AGENDADO", "EN_CAMINO", "EN_CURSO", ESTADO_TERMINADO_TECNICO, ESTADO_TERMINADO);
    static final String ESTADO_CERRADO_Y_COBRADO = "CERRADO_Y_COBRADO";
    static final String ESTADO_CANCELADO = "CANCELADO";

    private final VentaRepository ventaRepo;
    private final ProductoRepository productoRepo;
    private final EmpleadoRepository empleadoRepo;
    private final EmailService emailService;
    private final SesionCajaRepository sesionRepo;
    private final PdfService pdfService;
    private final NotificacionService notificacionService;
    private final ClienteRepository clienteRepo;
    private final MovimientoCuentaCorrienteClienteRepository movimientoCuentaCorrienteRepo;
    private final FacturaFiscalRepository facturaRepo;
    private final NotaCreditoRepository notaCreditoRepo;
    private final RecargoService recargoService;
    private final HistorialEstadoTrabajoService historialEstadoService;
    private final NotificacionTrabajoService notificacionTrabajoService;

    public VentaService(VentaRepository ventaRepo, ProductoRepository productoRepo,
            EmpleadoRepository empleadoRepo, EmailService emailService,
            SesionCajaRepository sesionRepo, PdfService pdfService, NotificacionService notificacionService,
            ClienteRepository clienteRepo, MovimientoCuentaCorrienteClienteRepository movimientoCuentaCorrienteRepo,
            FacturaFiscalRepository facturaRepo, NotaCreditoRepository notaCreditoRepo,
            RecargoService recargoService, HistorialEstadoTrabajoService historialEstadoService,
            NotificacionTrabajoService notificacionTrabajoService) {
        this.recargoService = recargoService;
        this.facturaRepo = facturaRepo;
        this.notaCreditoRepo = notaCreditoRepo;
        this.ventaRepo = ventaRepo;
        this.productoRepo = productoRepo;
        this.empleadoRepo = empleadoRepo;
        this.emailService = emailService;
        this.sesionRepo = sesionRepo;
        this.pdfService = pdfService;
        this.notificacionService = notificacionService;
        this.clienteRepo = clienteRepo;
        this.movimientoCuentaCorrienteRepo = movimientoCuentaCorrienteRepo;
        this.historialEstadoService = historialEstadoService;
        this.notificacionTrabajoService = notificacionTrabajoService;
    }

    /**
     * El stock se valida y descuenta siempre en este método para las líneas con un producto real,
     * incluso si la venta queda PENDIENTE_AUTORIZACION por un descuento manual (se "reserva" de
     * inmediato; no hay liberación automática de stock si nadie la aprueba ni la rechaza). Las líneas
     * manuales (trabajos sin producto, ej. "Apertura de cerradura") no tocan stock.
     *
     * Un descuento cargado por un ADMIN no necesita autorización: la venta queda
     * CONFIRMADA de una.
     */
    @Transactional
    public Venta registrarVenta(Venta venta, boolean autorizadoPorAdmin) {
        // Vincula la venta a la caja abierta en este momento, si hay una (sin filtrar por fecha:
        // una sesión de un día anterior que nunca se cerró sigue "ABIERTA"). Permite el arqueo por
        // turno; no es obligatorio tener una caja abierta para vender (no se pidió esa regla).
        sesionRepo.findByEstado("ABIERTA").ifPresent(venta::setSesion);

        BigDecimal totalBruto = BigDecimal.ZERO;

        for (DetalleVenta d : venta.getDetalles()) {
            if (d.getProducto() != null) {
                Producto p = productoRepo.buscarPorIdConLock(d.getProducto().getIdProducto())
                    .orElseThrow(() -> new IllegalArgumentException("Producto no existe"));

                if (p.getStockActual() < d.getCantidad()) {
                    throw new IllegalStateException("Stock insuficiente para " + p.getDescripcion());
                }

                int stockAntes = p.getStockActual();
                p.setStockActual(p.getStockActual() - d.getCantidad());
                productoRepo.save(p);
                notificacionService.evaluarYNotificarStockBajo(p, stockAntes, p.getStockActual());
                d.setProducto(p);
                d.setDescripcion(p.getDescripcion());
            }
            // Ítem manual: la descripción ya viene cargada desde el request, sin producto ni
            // validación de stock.

            BigDecimal subtotal = d.getPrecioUnitario().multiply(BigDecimal.valueOf(d.getCantidad()));
            d.setSubtotal(subtotal);
            d.setVenta(venta);

            totalBruto = totalBruto.add(subtotal);
        }

        BigDecimal descuento = venta.getDescuento() != null ? venta.getDescuento() : BigDecimal.ZERO;
        if (descuento.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El descuento no puede ser negativo");
        }
        if (descuento.compareTo(totalBruto) > 0) {
            throw new IllegalArgumentException("El descuento no puede ser mayor al total de la venta");
        }
        if (descuento.compareTo(BigDecimal.ZERO) > 0
                && (venta.getMotivoDescuento() == null || venta.getMotivoDescuento().isBlank())) {
            throw new IllegalArgumentException("Un descuento manual requiere indicar un motivo");
        }

        venta.setDescuento(descuento);
        venta.setTotalVenta(totalBruto.subtract(descuento));

        validarMedioPago(venta);
        resolverCliente(venta);
        validarMedioSegunCliente(venta);
        BigDecimal montoSaldoAplicado = validarYAplicarSaldoCliente(venta);
        aplicarRecargoTarjeta(venta, montoSaldoAplicado);

        boolean requiereAutorizacion = descuento.compareTo(BigDecimal.ZERO) > 0 && !autorizadoPorAdmin;
        venta.setEstado(requiereAutorizacion ? "PENDIENTE_AUTORIZACION" : "CONFIRMADA");

        Venta guardada = ventaRepo.save(venta);
        registrarDebitoCuentaCorrienteSiCorresponde(guardada, montoSaldoAplicado);
        registrarCargoCuentaCorrienteSiCorresponde(guardada);
        return guardada;
    }

    // El recargo se calcula acá, nunca se confía en el frontend. Solo sobre la parte que pasa por el
    // posnet: lo pagado con saldo a favor no lleva comisión. Va después del descuento.
    private void aplicarRecargoTarjeta(Venta venta, BigDecimal montoSaldoAplicado) {
        venta.setRecargoTarjeta(null);
        venta.setPorcentajeRecargo(null);
        if (!RecargoService.llevaRecargo(venta.getMedioPago())) {
            return;
        }
        BigDecimal porcentaje = recargoService.porcentajeDe(venta.getMedioPago());
        BigDecimal recargo = RecargoService.calcularRecargo(venta.getTotalVenta().subtract(montoSaldoAplicado), porcentaje);
        if (recargo.signum() > 0) {
            venta.setRecargoTarjeta(recargo);
            venta.setPorcentajeRecargo(porcentaje);
        }
    }

    // El backend aceptaba cualquier texto: una venta en CUENTA_CORRIENTE se habría guardado sin cargarle
    // nada a nadie. Null solo es posible desde código interno: el request lo exige con @NotBlank.
    private void validarMedioPago(Venta venta) {
        if (venta.getMedioPago() == null) {
            return;
        }
        String medio = venta.getMedioPago().trim().toUpperCase();
        if (!MEDIOS_PAGO_VALIDOS.contains(medio)) {
            throw new IllegalArgumentException("Medio de pago inválido: " + venta.getMedioPago());
        }
        venta.setMedioPago(medio);
    }

    // Lockea al cliente: dos ventas simultáneas no pueden gastar el mismo saldo.
    private void resolverCliente(Venta venta) {
        if (venta.getCliente() == null) {
            return;
        }
        Integer idCliente = venta.getCliente().getIdCliente();
        Cliente cliente = clienteRepo.buscarPorIdConLock(idCliente)
            .orElseThrow(() -> new IllegalArgumentException("Cliente no existe: " + idCliente));
        if (!cliente.isActivo()) {
            throw new IllegalStateException("El cliente " + cliente.getNombre() + " está dado de baja");
        }
        venta.setCliente(cliente);
    }

    private void validarMedioSegunCliente(Venta venta) {
        BigDecimal montoSaldoAplicado = venta.getMontoSaldoAplicado() != null ? venta.getMontoSaldoAplicado() : BigDecimal.ZERO;
        if (MEDIO_PAGO_SALDO.equals(venta.getMedioPago()) && montoSaldoAplicado.compareTo(venta.getTotalVenta()) != 0) {
            throw new IllegalArgumentException("Pagar con saldo requiere que el saldo aplicado cubra el total de la venta");
        }
        if (!MEDIO_PAGO_CUENTA_CORRIENTE.equals(venta.getMedioPago())) {
            return;
        }
        if (venta.getCliente() == null) {
            throw new IllegalArgumentException("Para vender en cuenta corriente hay que indicar un cliente");
        }
        // El cargo ya descuenta lo que tenga a favor: aplicar saldo además lo contaría dos veces.
        if (montoSaldoAplicado.compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalArgumentException("Una venta en cuenta corriente no admite pagar además con saldo");
        }
    }

    private void registrarCargoCuentaCorrienteSiCorresponde(Venta guardada) {
        if (!MEDIO_PAGO_CUENTA_CORRIENTE.equals(guardada.getMedioPago())
                || guardada.getTotalVenta().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        MovimientoCuentaCorrienteCliente cargo = new MovimientoCuentaCorrienteCliente();
        cargo.setCliente(guardada.getCliente());
        cargo.setTipo("DEBITO");
        cargo.setMonto(guardada.getTotalVenta());
        cargo.setVentaAplicacion(guardada);
        cargo.setDescripcion("Venta en cuenta corriente #" + guardada.getIdVenta());
        cargo.setEmpleado(guardada.getEmpleado());
        movimientoCuentaCorrienteRepo.save(cargo);
    }

    /**
     * Cuenta corriente (saldo a favor acreditado por una Nota de Crédito): valida que haya un
     * cliente, que el monto pedido no supere el total de la venta ni el saldo disponible
     * (lockeando la fila del cliente para que dos ventas simultáneas no puedan gastar el mismo
     * saldo dos veces — mismo motivo que ProductoRepository.buscarPorIdConLock para stock), y si
     * cubre el total completo fuerza medioPago="SALDO_CUENTA_CORRIENTE" sin confiar en lo que
     * mandó el frontend. Devuelve el monto validado (o ZERO si no se pidió saldo) para que el
     * caller lo use después de guardar la venta — el movimiento de cuenta corriente necesita el
     * id de la venta ya persistida.
     */
    private BigDecimal validarYAplicarSaldoCliente(Venta venta) {
        BigDecimal montoSaldoAplicado = venta.getMontoSaldoAplicado();
        if (montoSaldoAplicado == null || montoSaldoAplicado.compareTo(BigDecimal.ZERO) <= 0) {
            venta.setMontoSaldoAplicado(null);
            return BigDecimal.ZERO;
        }
        if (venta.getCliente() == null) {
            throw new IllegalArgumentException("Para pagar con el saldo de cuenta corriente hay que indicar un cliente");
        }
        if (montoSaldoAplicado.compareTo(venta.getTotalVenta()) > 0) {
            throw new IllegalArgumentException("El saldo aplicado no puede ser mayor al total de la venta");
        }

        BigDecimal saldoDisponible = calcularSaldoCliente(venta.getCliente().getIdCliente());
        if (montoSaldoAplicado.compareTo(saldoDisponible) > 0) {
            throw new IllegalStateException("El cliente no tiene saldo suficiente (disponible: $"
                + saldoDisponible + ", solicitado: $" + montoSaldoAplicado + ")");
        }

        venta.setMontoSaldoAplicado(montoSaldoAplicado);
        if (montoSaldoAplicado.compareTo(venta.getTotalVenta()) == 0) {
            venta.setMedioPago(MEDIO_PAGO_SALDO);
        }
        return montoSaldoAplicado;
    }

    // Misma fórmula que ClienteService.calcularSaldo (CREDITO menos DEBITO del historial) —
    // duplicada a propósito para no crear una dependencia de VentaService hacia ClienteService
    // por una suma de 5 líneas; el lock pessimista de arriba es lo que evita la condición de
    // carrera, no esta consulta en sí.
    private BigDecimal calcularSaldoCliente(Integer idCliente) {
        BigDecimal saldo = BigDecimal.ZERO;
        for (MovimientoCuentaCorrienteCliente m : movimientoCuentaCorrienteRepo.findByClienteIdClienteOrderByFechaDesc(idCliente)) {
            saldo = "CREDITO".equals(m.getTipo()) ? saldo.add(m.getMonto()) : saldo.subtract(m.getMonto());
        }
        return saldo;
    }

    private void registrarDebitoCuentaCorrienteSiCorresponde(Venta guardada, BigDecimal montoSaldoAplicado) {
        if (montoSaldoAplicado.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        MovimientoCuentaCorrienteCliente movimiento = new MovimientoCuentaCorrienteCliente();
        movimiento.setCliente(guardada.getCliente());
        movimiento.setTipo("DEBITO");
        movimiento.setMonto(montoSaldoAplicado);
        movimiento.setVentaAplicacion(guardada);
        movimiento.setDescripcion("Pago con saldo de cuenta corriente — venta #" + guardada.getIdVenta());
        movimiento.setEmpleado(guardada.getEmpleado());
        movimientoCuentaCorrienteRepo.save(movimiento);
    }

    public List<Venta> listarDescuentosPendientes() {
        return ventaRepo.findByEstadoOrderByFechaAsc("PENDIENTE_AUTORIZACION");
    }

    @Transactional
    public Venta aprobarDescuento(Integer idVenta, Empleado admin) {
        Venta venta = obtenerPendienteConLock(idVenta);
        venta.setEstado("CONFIRMADA");
        venta.setResueltoPor(admin);
        venta.setResueltoEn(LocalDateTime.now());
        return ventaRepo.save(venta);
    }

    /**
     * Rechazar anula la venta entera (decisión del dueño): devuelve el stock reservado y el saldo
     * de cuenta corriente que se haya usado, y el vendedor la vuelve a cobrar sin descuento. La
     * fila queda en ANULADA para que siga auditable; caja, reportes, facturas y notas de crédito
     * solo miran ventas CONFIRMADA, así que no cuenta en ningún lado.
     */
    @Transactional
    public Venta rechazarDescuento(Integer idVenta, Empleado admin) {
        Venta venta = obtenerPendienteConLock(idVenta);

        devolverStock(venta);

        BigDecimal saldoAplicado = venta.getMontoSaldoAplicado();
        if (saldoAplicado != null && saldoAplicado.compareTo(BigDecimal.ZERO) > 0) {
            MovimientoCuentaCorrienteCliente devolucion = new MovimientoCuentaCorrienteCliente();
            devolucion.setCliente(venta.getCliente());
            devolucion.setTipo("CREDITO");
            devolucion.setMonto(saldoAplicado);
            devolucion.setVentaAplicacion(venta);
            devolucion.setDescripcion("Devolución de saldo por venta anulada #" + venta.getIdVenta());
            devolucion.setEmpleado(admin);
            movimientoCuentaCorrienteRepo.save(devolucion);
        }
        if (MEDIO_PAGO_CUENTA_CORRIENTE.equals(venta.getMedioPago())
                && venta.getTotalVenta().compareTo(BigDecimal.ZERO) > 0) {
            MovimientoCuentaCorrienteCliente reversion = new MovimientoCuentaCorrienteCliente();
            reversion.setCliente(venta.getCliente());
            reversion.setTipo("CREDITO");
            reversion.setMonto(venta.getTotalVenta());
            reversion.setVentaAplicacion(venta);
            reversion.setDescripcion("Reversión del cargo por venta anulada #" + venta.getIdVenta());
            reversion.setEmpleado(admin);
            movimientoCuentaCorrienteRepo.save(reversion);
        }

        venta.setEstado("ANULADA");
        venta.setResueltoPor(admin);
        venta.setResueltoEn(LocalDateTime.now());
        return ventaRepo.save(venta);
    }

    private void devolverStock(Venta venta) {
        for (DetalleVenta detalle : venta.getDetalles()) {
            if (detalle.getProducto() != null) {
                Producto p = productoRepo.buscarPorIdConLock(detalle.getProducto().getIdProducto())
                    .orElseThrow(() -> new IllegalArgumentException("Producto no existe"));
                p.setStockActual(p.getStockActual() + detalle.getCantidad());
                productoRepo.save(p);
            }
        }
    }

    // Con lock: sin esto, dos ADMIN resolviendo a la vez podían devolver dos veces el stock y el saldo.
    private Venta obtenerPendienteConLock(Integer idVenta) {
        Venta venta = ventaRepo.buscarPorIdConLock(idVenta)
            .orElseThrow(() -> new IllegalArgumentException("Venta no existe: " + idVenta));
        if (!"PENDIENTE_AUTORIZACION".equals(venta.getEstado())) {
            throw new IllegalStateException("La venta no tiene un descuento pendiente de autorización");
        }
        return venta;
    }

    /**
     * Igual espíritu que PresupuestoService.enviarPorEmail: arma el mismo comprobante branded en
     * PDF (logo, dirección, teléfono) y lo manda adjunto. A diferencia del presupuesto, el email
     * del cliente no se pide al registrar la venta — se guarda recién acá, la primera vez que se
     * envía (y puede reenviarse después a la misma casilla).
     */
    @Transactional
    public Venta enviarComprobantePorEmail(Integer idVenta, String email) {
        Venta venta = ventaRepo.findById(idVenta)
            .orElseThrow(() -> new IllegalArgumentException("Venta no existe: " + idVenta));

        byte[] pdf = pdfService.generarPdf(construirHtmlComprobante(venta));

        String titulo = "DOMICILIO".equals(venta.getTipoVenta()) ? "Remito de trabajo" : "Comprobante de venta";
        String asunto = titulo + " #" + venta.getIdVenta() + " - " + NOMBRE_LOCAL;
        String cuerpo = "Te enviamos tu comprobante en PDF adjunto. Gracias por tu compra — " + NOMBRE_LOCAL + ".";
        emailService.enviarConAdjuntoPdf(email, asunto, cuerpo, "comprobante-" + venta.getIdVenta() + ".pdf", pdf);

        venta.setClienteEmail(email);
        venta.setComprobanteEnviadoPorEmail(true);
        return ventaRepo.save(venta);
    }

    public byte[] generarPdf(Integer idVenta) {
        return pdfService.generarPdf(construirHtmlComprobante(obtenerPorId(idVenta)));
    }

    /**
     * Orden de trabajo para la impresora térmica (Etapa 1, Parte 5): lo que el técnico se lleva
     * impreso. Se imprime a pedido desde la pantalla del jefe, nunca automática. Un trabajo
     * eliminado (ANULADA) no tiene orden: no hay nada que hacer en el domicilio.
     */
    public byte[] generarOrdenTrabajoPdf(Integer idVenta) {
        Venta venta = obtenerTrabajo(idVenta);
        if ("ANULADA".equals(venta.getEstado())) {
            throw new IllegalStateException("El trabajo #" + idVenta + " está eliminado: no tiene orden para imprimir");
        }
        List<ComprobanteHtmlBuilder.Linea> lineas = LineasComprobante.de(venta).stream()
            .map(l -> new ComprobanteHtmlBuilder.Linea(l.descripcion(), l.cantidad(), l.precioUnitario(), l.subtotal()))
            .toList();
        return pdfService.generarPdf(
            OrdenTrabajoHtmlBuilder.construir(venta, lineas, venta.totalCobrado(), pdfService.logoDataUri()));
    }

    /** Una venta que tiene que ser un trabajo a domicilio (400 si es una venta de mostrador). */
    public Venta obtenerTrabajo(Integer idVenta) {
        Venta venta = obtenerPorId(idVenta);
        if (!"DOMICILIO".equals(venta.getTipoVenta())) {
            throw new IllegalArgumentException("La venta #" + idVenta + " no es un trabajo a domicilio");
        }
        return venta;
    }

    public Venta obtenerPorId(Integer idVenta) {
        return ventaRepo.findById(idVenta)
            .orElseThrow(() -> new IllegalArgumentException("Venta no existe: " + idVenta));
    }

    /**
     * Un mismo comprobante branded para venta de mostrador (ticket) y trabajo a domicilio
     * (remito, más completo: cliente, dirección, descripción del trabajo, técnico). Usa
     * {@link TicketHtmlBuilder} (formato angosto 58mm) en vez de {@link ComprobanteHtmlBuilder}
     * (que sigue usando Presupuestos) porque este comprobante es justamente lo que se imprime en
     * la Xprinter térmica del mostrador — ver TicketHtmlBuilder para el detalle del bug que
     * arregla.
     */
    private String construirHtmlComprobante(Venta venta) {
        // Con tarjeta, los precios ya incluyen el recargo repartido (sin renglón aparte): ver LineasComprobante.
        List<ComprobanteHtmlBuilder.Linea> lineas = LineasComprobante.de(venta).stream()
            .map(l -> new ComprobanteHtmlBuilder.Linea(l.descripcion(), l.cantidad(), l.precioUnitario(), l.subtotal()))
            .toList();

        boolean esDomicilio = "DOMICILIO".equals(venta.getTipoVenta());

        List<String> info;
        if (esDomicilio) {
            info = new ArrayList<>();
            info.add("Cliente: " + venta.getClienteNombre()
                + (venta.getClienteTelefono() != null ? " — Tel: " + venta.getClienteTelefono() : ""));
            if (venta.getDireccionTrabajo() != null) {
                info.add("Dirección: " + venta.getDireccionTrabajo());
            }
            if (venta.getDescripcionTrabajo() != null) {
                info.add("Trabajo: " + venta.getDescripcionTrabajo());
            }
            if (venta.getEmpleadoTecnico() != null) {
                info.add("Técnico: " + venta.getEmpleadoTecnico().getNombre());
            }
        } else {
            info = venta.getDescuento() != null && venta.getDescuento().compareTo(BigDecimal.ZERO) > 0
                ? List.of("Medio de pago: " + etiquetaMedioPago(venta.getMedioPago()), "Descuento: $" + venta.getDescuento())
                : List.of("Medio de pago: " + etiquetaMedioPago(venta.getMedioPago()));
        }

        return TicketHtmlBuilder.construir(info, lineas, venta.totalCobrado(),
            "Comprobante interno, no válido como factura fiscal.", pdfService.logoDataUri());
    }

    private static String etiquetaMedioPago(String medio) {
        if (medio == null) {
            return "—";
        }
        return switch (medio) {
            case "EFECTIVO" -> "Efectivo";
            case "TRANSFERENCIA" -> "Transferencia";
            case "TARJETA" -> "Tarjeta";
            case RecargoService.TARJETA_CREDITO -> "Tarjeta de crédito";
            case RecargoService.TARJETA_DEBITO -> "Tarjeta de débito";
            case MEDIO_PAGO_CUENTA_CORRIENTE -> "Cuenta corriente";
            case MEDIO_PAGO_SALDO -> "Saldo de cuenta corriente";
            default -> medio;
        };
    }

    /**
     * Crea o actualiza un trabajo a domicilio. A diferencia de registrarVenta (Cobros), acepta
     * cero ítems (un trabajo recién agendado puede no tener artículos ni mano de obra todavía) y
     * permite reabrir uno existente: en ese caso primero devuelve el stock de sus líneas viejas
     * con producto antes de reemplazarlas, para no descuadrar el inventario al editar cantidades.
     * Los artículos de un trabajo agendado quedan reservados desde que se cargan.
     *
     * Solo se edita lo que todavía no se cobró: volver a guardar un trabajo cobrado lo sacaría de
     * los reportes. Al cerrar, "fecha" pasa a ser el momento del cobro, porque los reportes cuentan
     * por esa fecha y un trabajo puede cobrarse días después de cargarlo. No se vincula a la sesión
     * de caja: la plata de los trabajos no pasa por el arqueo (decisión del dueño).
     */
    @Transactional
    public Venta guardarTrabajoDomicilio(TrabajoDomicilioRequest req, Empleado empleado) {
        if (req.getEstadoTrabajo() != null && !ESTADOS_DE_BORRADOR.contains(req.getEstadoTrabajo())) {
            throw new IllegalArgumentException("Estado de trabajo inválido: " + req.getEstadoTrabajo()
                + ". Un trabajo queda cobrado solo con \"Cerrar y cobrar\"");
        }
        if (req.getMedioPago() != null && !MEDIOS_DE_COBRO.contains(req.getMedioPago())) {
            throw new IllegalArgumentException("Medio de pago inválido: " + req.getMedioPago());
        }

        Venta venta;
        boolean esNueva = req.getIdVenta() == null;
        String estadoAnterior;
        LocalDate fechaAnterior;
        LocalTime horaDesdeAnterior;
        LocalTime horaHastaAnterior;
        Empleado tecnicoAnterior;
        if (!esNueva) {
            venta = obtenerTrabajoEditableConLock(req.getIdVenta());
            estadoAnterior = venta.getEstadoTrabajo();
            fechaAnterior = venta.getFechaProgramada();
            horaDesdeAnterior = venta.getHoraDesde();
            horaHastaAnterior = venta.getHoraHasta();
            tecnicoAnterior = venta.getEmpleadoTecnico();
            devolverStock(venta);
            venta.getDetalles().clear();
        } else {
            venta = new Venta();
            venta.setTipoVenta("DOMICILIO");
            estadoAnterior = null;
            fechaAnterior = null;
            horaDesdeAnterior = null;
            horaHastaAnterior = null;
            tecnicoAnterior = null;
        }

        if (req.getFechaProgramada() != null) {
            venta.setFechaProgramada(req.getFechaProgramada());
        } else if (venta.getFechaProgramada() == null) {
            venta.setFechaProgramada(ZonaNegocio.hoy());
        }
        venta.setHoraDesde(req.getHoraDesde());
        venta.setHoraHasta(req.getHoraHasta());
        venta.setMedioPago(req.getMedioPago());
        venta.setEmpleado(empleado);
        venta.setClienteNombre(req.getClienteNombre());
        venta.setClienteTelefono(req.getClienteTelefono());
        venta.setDireccionTrabajo(req.getDireccionTrabajo());
        venta.setDescripcionTrabajo(req.getDescripcionTrabajo());
        venta.setObservacionesTecnico(req.getObservacionesTecnico());

        if (req.getIdEmpleadoTecnico() != null) {
            Empleado tecnico = empleadoRepo.findById(req.getIdEmpleadoTecnico())
                .orElseThrow(() -> new IllegalArgumentException("Técnico no existe: " + req.getIdEmpleadoTecnico()));
            venta.setEmpleadoTecnico(tecnico);
        } else {
            venta.setEmpleadoTecnico(null);
        }

        List<DetalleVentaRequest> items = req.getDetalles() != null ? req.getDetalles() : List.of();
        BigDecimal total = BigDecimal.ZERO;
        for (DetalleVentaRequest itemReq : items) {
            DetalleVenta detalle = new DetalleVenta();
            detalle.setCantidad(itemReq.getCantidad());
            detalle.setPrecioUnitario(itemReq.getPrecioUnitario());
            detalle.setTipo(itemReq.getTipo() != null ? itemReq.getTipo() : "ARTICULO");

            if (itemReq.getIdProducto() != null) {
                Producto p = productoRepo.buscarPorIdConLock(itemReq.getIdProducto())
                    .orElseThrow(() -> new IllegalArgumentException("Producto no existe: " + itemReq.getIdProducto()));
                if (p.getStockActual() < itemReq.getCantidad()) {
                    throw new IllegalStateException("Stock insuficiente para " + p.getDescripcion());
                }
                int stockAntes = p.getStockActual();
                p.setStockActual(p.getStockActual() - itemReq.getCantidad());
                productoRepo.save(p);
                notificacionService.evaluarYNotificarStockBajo(p, stockAntes, p.getStockActual());
                detalle.setProducto(p);
                detalle.setDescripcion(p.getDescripcion());
            } else {
                if (itemReq.getDescripcion() == null || itemReq.getDescripcion().isBlank()) {
                    throw new IllegalArgumentException(
                        "Cada línea del trabajo necesita un producto o una descripción manual");
                }
                detalle.setDescripcion(itemReq.getDescripcion());
            }

            BigDecimal subtotal = itemReq.getPrecioUnitario().multiply(BigDecimal.valueOf(itemReq.getCantidad()));
            detalle.setSubtotal(subtotal);
            detalle.setVenta(venta);
            venta.getDetalles().add(detalle);
            total = total.add(subtotal);
        }

        venta.setTotalVenta(total);
        venta.setDescuento(BigDecimal.ZERO);

        if (req.isCerrar()) {
            if (venta.getDetalles().isEmpty() || total.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Un trabajo no se puede cerrar y cobrar sin al menos un ítem");
            }
            if (req.getMedioPago() == null) {
                throw new IllegalArgumentException("Para cerrar y cobrar un trabajo hay que indicar el medio de pago");
            }
            venta.setFecha(LocalDateTime.now());
            venta.setComisionTecnico(venta.getEmpleadoTecnico() != null ? venta.getEmpleadoTecnico().getComision() : null);
            venta.setEstadoTrabajo(ESTADO_CERRADO_Y_COBRADO);
            venta.setEstado("CONFIRMADA");
        } else {
            venta.setEstadoTrabajo(req.getEstadoTrabajo() != null ? req.getEstadoTrabajo() : "AGENDADO");
            venta.setEstado("EN_PROGRESO");
        }

        // Recordatorio de 1h antes (RecordatorioTrabajoService): si cambió cuándo empieza o quién lo
        // hace, el aviso que ya se mandó no vale para el horario/técnico nuevo, hay que volver a darlo.
        boolean cambioTecnico = !java.util.Objects.equals(
            tecnicoAnterior != null ? tecnicoAnterior.getIdEmpleado() : null,
            venta.getEmpleadoTecnico() != null ? venta.getEmpleadoTecnico().getIdEmpleado() : null);
        if (esNueva || cambioTecnico || !java.util.Objects.equals(fechaAnterior, venta.getFechaProgramada())
                || !java.util.Objects.equals(horaDesdeAnterior, venta.getHoraDesde())) {
            venta.setRecordatorioEnviado(false);
        }

        Venta guardada = ventaRepo.save(venta);

        if (!guardada.getEstadoTrabajo().equals(estadoAnterior)) {
            historialEstadoService.registrar(guardada, estadoAnterior, guardada.getEstadoTrabajo(), empleado, null);
        } else if (!esNueva && huboReprogramacion(fechaAnterior, horaDesdeAnterior, horaHastaAnterior, guardada)) {
            String detalle = "Reprogramado: %s %s-%s -> %s %s-%s".formatted(fechaAnterior, horaDesdeAnterior,
                horaHastaAnterior, guardada.getFechaProgramada(), guardada.getHoraDesde(), guardada.getHoraHasta());
            historialEstadoService.registrar(guardada, estadoAnterior, guardada.getEstadoTrabajo(), empleado, detalle);
        }

        avisarTecnicoSiCorresponde(guardada, esNueva, tecnicoAnterior, fechaAnterior, horaDesdeAnterior,
            horaHastaAnterior);

        return guardada;
    }

    /**
     * Al técnico le interesan tres cosas: que le asignaron un trabajo nuevo (o se lo reasignaron:
     * desde su lado es la misma novedad), que se reprogramó, o que se lo cancelaron (esto último lo
     * dispara anularTrabajoDomicilio, no este método). Un cambio de técnico ya cuenta como "nuevo
     * trabajo" para el que entra, así que no avisa reprogramación aparte en ese mismo guardado.
     */
    private void avisarTecnicoSiCorresponde(Venta guardada, boolean esNueva, Empleado tecnicoAnterior,
            LocalDate fechaAnterior, LocalTime horaDesdeAnterior, LocalTime horaHastaAnterior) {
        Empleado tecnico = guardada.getEmpleadoTecnico();
        if (tecnico == null) {
            return;
        }
        boolean esTecnicoNuevo = esNueva
            || tecnicoAnterior == null || !tecnicoAnterior.getIdEmpleado().equals(tecnico.getIdEmpleado());
        if (esTecnicoNuevo) {
            notificacionTrabajoService.avisarATecnico(tecnico, "TRABAJO_NUEVO",
                "Nuevo trabajo: " + descripcionParaAviso(guardada), guardada);
        } else if (huboReprogramacion(fechaAnterior, horaDesdeAnterior, horaHastaAnterior, guardada)) {
            notificacionTrabajoService.avisarATecnico(tecnico, "REPROGRAMADO",
                "Se reprogramó el trabajo #" + guardada.getIdVenta() + " para " + guardada.getFechaProgramada(),
                guardada);
        }
    }

    private String descripcionParaAviso(Venta venta) {
        return venta.getDireccionTrabajo() != null ? venta.getDireccionTrabajo() : "sin dirección cargada";
    }

    private boolean huboReprogramacion(LocalDate fechaAnterior, LocalTime horaDesdeAnterior,
            LocalTime horaHastaAnterior, Venta guardada) {
        return !java.util.Objects.equals(fechaAnterior, guardada.getFechaProgramada())
            || !java.util.Objects.equals(horaDesdeAnterior, guardada.getHoraDesde())
            || !java.util.Objects.equals(horaHastaAnterior, guardada.getHoraHasta());
    }


    /**
     * "Eliminar trabajo" en pantalla, cobrado o no: devuelve el stock y sale de la agenda y de los
     * reportes, que solo miran CONFIRMADA. La fila queda en ANULADA para que siga auditable. Con una
     * factura o nota de crédito no se puede: esos documentos ya existen y hay que resolverlos aparte.
     */
    @Transactional
    public Venta anularTrabajoDomicilio(Integer idVenta, Empleado admin) {
        Venta venta = obtenerTrabajoConLock(idVenta);
        if ("ANULADA".equals(venta.getEstado())) {
            throw new IllegalStateException("El trabajo #" + idVenta + " ya está eliminado");
        }
        exigirNoTerminado(venta);
        boolean tieneFactura = facturaRepo.findByVentaIdVenta(idVenta)
            .filter(factura -> !"ERROR".equals(factura.getEstado()))
            .isPresent();
        if (tieneFactura || notaCreditoRepo.existsByVentaOriginalIdVenta(idVenta)) {
            throw new IllegalStateException("El trabajo #" + idVenta
                + " tiene una factura o nota de crédito: no se puede eliminar");
        }
        String estadoAnterior = venta.getEstadoTrabajo();
        devolverStock(venta);
        venta.setEstado("ANULADA");
        venta.setEstadoTrabajo(ESTADO_CANCELADO);
        venta.setResueltoPor(admin);
        venta.setResueltoEn(LocalDateTime.now());
        Venta anulada = ventaRepo.save(venta);
        historialEstadoService.registrar(anulada, estadoAnterior, ESTADO_CANCELADO, admin, null);
        if (anulada.getEmpleadoTecnico() != null) {
            notificacionTrabajoService.avisarATecnico(anulada.getEmpleadoTecnico(), ESTADO_CANCELADO,
                "Se canceló el trabajo #" + anulada.getIdVenta(), anulada);
        }
        return anulada;
    }

    // Con lock: sin esto, dos ADMIN cerrando a la vez podían devolver dos veces el stock.
    private Venta obtenerTrabajoConLock(Integer idVenta) {
        Venta venta = ventaRepo.buscarPorIdConLock(idVenta)
            .orElseThrow(() -> new IllegalArgumentException("Trabajo no existe: " + idVenta));
        if (!"DOMICILIO".equals(venta.getTipoVenta())) {
            throw new IllegalArgumentException("La venta #" + idVenta + " no es un trabajo a domicilio");
        }
        return venta;
    }

    private Venta obtenerTrabajoEditableConLock(Integer idVenta) {
        Venta venta = obtenerTrabajoConLock(idVenta);
        if (!"EN_PROGRESO".equals(venta.getEstado())) {
            throw new IllegalStateException("El trabajo #" + idVenta + " ya está cobrado o anulado: no se puede modificar");
        }
        exigirNoTerminado(venta);
        return venta;
    }

    private static void exigirNoTerminado(Venta venta) {
        if (ESTADO_TERMINADO.equals(venta.getEstadoTrabajo())) {
            throw new IllegalStateException("El trabajo #" + venta.getIdVenta()
                + " está terminado: no se puede modificar. Cerralo y cobralo, o reabrilo si hay que corregir algo");
        }
    }

    /** Terminado por el jefe, cobrado o eliminado: nadie lo modifica (ni datos, ni notas, ni fotos). */
    public static boolean estaBloqueado(Venta venta) {
        return !"EN_PROGRESO".equals(venta.getEstado()) || ESTADO_TERMINADO.equals(venta.getEstadoTrabajo());
    }

    /**
     * "Cerrar y cobrar" de un trabajo que el jefe ya marcó Terminado: con los ítems tal cual
     * quedaron (no se pueden tocar), solo elige el medio de pago. Va a la caja aparte de trabajos,
     * igual que el cierre de siempre (sin sesión de caja de mostrador).
     */
    @Transactional
    public Venta cobrarTrabajoTerminado(Integer idVenta, String medioPago, Empleado admin) {
        Venta venta = obtenerTrabajoConLock(idVenta);
        if (!"EN_PROGRESO".equals(venta.getEstado()) || !ESTADO_TERMINADO.equals(venta.getEstadoTrabajo())) {
            throw new IllegalStateException("Solo se cobra así un trabajo terminado y todavía sin cobrar");
        }
        if (medioPago == null || !MEDIOS_DE_COBRO.contains(medioPago)) {
            throw new IllegalArgumentException("Medio de pago inválido: " + medioPago);
        }
        if (venta.getDetalles().isEmpty() || venta.getTotalVenta().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Un trabajo no se puede cerrar y cobrar sin al menos un ítem");
        }
        venta.setMedioPago(medioPago);
        venta.setFecha(LocalDateTime.now());
        venta.setComisionTecnico(venta.getEmpleadoTecnico() != null ? venta.getEmpleadoTecnico().getComision() : null);
        venta.setEstadoTrabajo(ESTADO_CERRADO_Y_COBRADO);
        venta.setEstado("CONFIRMADA");
        Venta cobrada = ventaRepo.save(venta);
        historialEstadoService.registrar(cobrada, ESTADO_TERMINADO, ESTADO_CERRADO_Y_COBRADO, admin, null);
        return cobrada;
    }

    /**
     * Deshace un "Terminado" puesto por error: vuelve al estado que tenía antes (según el
     * historial; si el técnico ya había avisado que terminó, queda en ese aviso) y avisa al técnico
     * que el trabajo volvió a estar abierto.
     */
    @Transactional
    public Venta reabrirTrabajo(Integer idVenta, Empleado admin) {
        Venta venta = obtenerTrabajoConLock(idVenta);
        if (!"EN_PROGRESO".equals(venta.getEstado()) || !ESTADO_TERMINADO.equals(venta.getEstadoTrabajo())) {
            throw new IllegalStateException("Solo se puede reabrir un trabajo terminado y todavía sin cobrar");
        }
        String estadoPrevio = historialEstadoService.estadoAntesDe(idVenta, ESTADO_TERMINADO);
        venta.setEstadoTrabajo(estadoPrevio != null && !ESTADO_TERMINADO.equals(estadoPrevio) ? estadoPrevio : "AGENDADO");
        Venta reabierta = ventaRepo.save(venta);
        historialEstadoService.registrar(reabierta, ESTADO_TERMINADO, reabierta.getEstadoTrabajo(), admin, "Reabierto");
        if (reabierta.getEmpleadoTecnico() != null) {
            notificacionTrabajoService.avisarATecnico(reabierta.getEmpleadoTecnico(), "REABIERTO",
                "Se reabrió el trabajo #" + reabierta.getIdVenta() + " (" + descripcionParaAviso(reabierta) + ")", reabierta);
        }
        return reabierta;
    }
}
