package com.thiago.escenasFX.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
import com.thiago.escenasFX.repository.MovimientoCuentaCorrienteClienteRepository;
import com.thiago.escenasFX.repository.ProductoRepository;
import com.thiago.escenasFX.repository.SesionCajaRepository;
import com.thiago.escenasFX.repository.VentaRepository;

@Service
public class VentaService {

    private static final String NOMBRE_LOCAL = "D13 Distribuidora";
    private static final String MEDIO_PAGO_SALDO = "SALDO_CUENTA_CORRIENTE";

    private final VentaRepository ventaRepo;
    private final ProductoRepository productoRepo;
    private final EmpleadoRepository empleadoRepo;
    private final EmailService emailService;
    private final SesionCajaRepository sesionRepo;
    private final PdfService pdfService;
    private final NotificacionService notificacionService;
    private final ClienteRepository clienteRepo;
    private final MovimientoCuentaCorrienteClienteRepository movimientoCuentaCorrienteRepo;

    public VentaService(VentaRepository ventaRepo, ProductoRepository productoRepo,
            EmpleadoRepository empleadoRepo, EmailService emailService,
            SesionCajaRepository sesionRepo, PdfService pdfService, NotificacionService notificacionService,
            ClienteRepository clienteRepo, MovimientoCuentaCorrienteClienteRepository movimientoCuentaCorrienteRepo) {
        this.ventaRepo = ventaRepo;
        this.productoRepo = productoRepo;
        this.empleadoRepo = empleadoRepo;
        this.emailService = emailService;
        this.sesionRepo = sesionRepo;
        this.pdfService = pdfService;
        this.notificacionService = notificacionService;
        this.clienteRepo = clienteRepo;
        this.movimientoCuentaCorrienteRepo = movimientoCuentaCorrienteRepo;
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

        BigDecimal montoSaldoAplicado = validarYAplicarSaldoCliente(venta);

        if (descuento.compareTo(BigDecimal.ZERO) > 0 && !autorizadoPorAdmin) {
            venta.setEstado("PENDIENTE_AUTORIZACION");

            Venta guardada = ventaRepo.save(venta);
            registrarDebitoCuentaCorrienteSiCorresponde(guardada, montoSaldoAplicado);
            return guardada;
        }

        venta.setEstado("CONFIRMADA");
        Venta guardada = ventaRepo.save(venta);
        registrarDebitoCuentaCorrienteSiCorresponde(guardada, montoSaldoAplicado);
        return guardada;
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

        Cliente cliente = clienteRepo.buscarPorIdConLock(venta.getCliente().getIdCliente())
            .orElseThrow(() -> new IllegalArgumentException("Cliente no existe: " + venta.getCliente().getIdCliente()));
        BigDecimal saldoDisponible = calcularSaldoCliente(cliente.getIdCliente());
        if (montoSaldoAplicado.compareTo(saldoDisponible) > 0) {
            throw new IllegalStateException("El cliente no tiene saldo suficiente (disponible: $"
                + saldoDisponible + ", solicitado: $" + montoSaldoAplicado + ")");
        }

        venta.setCliente(cliente);
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

        for (DetalleVenta detalle : venta.getDetalles()) {
            if (detalle.getProducto() != null) {
                Producto p = productoRepo.buscarPorIdConLock(detalle.getProducto().getIdProducto())
                    .orElseThrow(() -> new IllegalArgumentException("Producto no existe"));
                p.setStockActual(p.getStockActual() + detalle.getCantidad());
                productoRepo.save(p);
            }
        }

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

        venta.setEstado("ANULADA");
        venta.setResueltoPor(admin);
        venta.setResueltoEn(LocalDateTime.now());
        return ventaRepo.save(venta);
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
        List<ComprobanteHtmlBuilder.Linea> lineas = venta.getDetalles().stream()
            .map(d -> new ComprobanteHtmlBuilder.Linea(d.getDescripcion(), d.getCantidad(), d.getPrecioUnitario(),
                d.getSubtotal()))
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
                ? List.of("Medio de pago: " + venta.getMedioPago(), "Descuento: $" + venta.getDescuento())
                : List.of("Medio de pago: " + venta.getMedioPago());
        }

        return TicketHtmlBuilder.construir(info, lineas, venta.getTotalVenta(),
            "Comprobante interno, no válido como factura fiscal.", pdfService.logoDataUri());
    }

    /**
     * Crea o actualiza un trabajo a domicilio. A diferencia de registrarVenta (Cobros), acepta
     * cero ítems (un trabajo recién agendado puede no tener artículos ni mano de obra todavía) y
     * permite reabrir uno existente: en ese caso primero devuelve el stock de sus líneas viejas
     * con producto antes de reemplazarlas, para no descuadrar el inventario al editar cantidades.
     */
    @Transactional
    public Venta guardarTrabajoDomicilio(TrabajoDomicilioRequest req, Empleado empleado) {
        Venta venta;
        if (req.getIdVenta() != null) {
            venta = ventaRepo.findById(req.getIdVenta())
                .orElseThrow(() -> new IllegalArgumentException("Trabajo no existe: " + req.getIdVenta()));
            if (!"DOMICILIO".equals(venta.getTipoVenta())) {
                throw new IllegalArgumentException("La venta #" + req.getIdVenta() + " no es un trabajo a domicilio");
            }
            for (DetalleVenta viejo : venta.getDetalles()) {
                if (viejo.getProducto() != null) {
                    Producto p = viejo.getProducto();
                    p.setStockActual(p.getStockActual() + viejo.getCantidad());
                    productoRepo.save(p);
                }
            }
            venta.getDetalles().clear();
        } else {
            venta = new Venta();
            venta.setTipoVenta("DOMICILIO");
        }

        venta.setEmpleado(empleado);
        venta.setClienteNombre(req.getClienteNombre());
        venta.setClienteTelefono(req.getClienteTelefono());
        venta.setDireccionTrabajo(req.getDireccionTrabajo());
        venta.setDescripcionTrabajo(req.getDescripcionTrabajo());

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
            sesionRepo.findByEstado("ABIERTA").ifPresent(venta::setSesion);
            venta.setEstadoTrabajo("COBRADO");
            venta.setEstado("CONFIRMADA");
        } else {
            venta.setEstadoTrabajo(req.getEstadoTrabajo());
            venta.setEstado("EN_PROGRESO");
        }

        return ventaRepo.save(venta);
    }
}
