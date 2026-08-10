package com.thiago.escenasFX.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thiago.escenasFX.dto.DetalleVentaRequest;
import com.thiago.escenasFX.dto.TrabajoDomicilioRequest;
import com.thiago.escenasFX.exception.AuthenticationFailedException;
import com.thiago.escenasFX.model.DetalleVenta;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.Producto;
import com.thiago.escenasFX.model.Venta;
import com.thiago.escenasFX.repository.EmpleadoRepository;
import com.thiago.escenasFX.repository.ProductoRepository;
import com.thiago.escenasFX.repository.SesionCajaRepository;
import com.thiago.escenasFX.repository.VentaRepository;

@Service
public class VentaService {

    private static final String NOMBRE_LOCAL = "D13 Distribuidora";

    private final VentaRepository ventaRepo;
    private final ProductoRepository productoRepo;
    private final EmpleadoRepository empleadoRepo;
    private final OtpService otpService;
    private final EmailService emailService;
    private final SesionCajaRepository sesionRepo;
    private final PdfService pdfService;

    public VentaService(VentaRepository ventaRepo, ProductoRepository productoRepo,
            EmpleadoRepository empleadoRepo, OtpService otpService, EmailService emailService,
            SesionCajaRepository sesionRepo, PdfService pdfService) {
        this.ventaRepo = ventaRepo;
        this.productoRepo = productoRepo;
        this.empleadoRepo = empleadoRepo;
        this.otpService = otpService;
        this.emailService = emailService;
        this.sesionRepo = sesionRepo;
        this.pdfService = pdfService;
    }

    /**
     * El stock se valida y descuenta siempre en este método para las líneas con un producto real,
     * incluso si la venta queda PENDIENTE_AUTORIZACION por un descuento manual (se "reserva" de
     * inmediato; no hay liberación automática de stock si el OTP nunca se confirma). Las líneas
     * manuales (trabajos sin producto, ej. "Apertura de cerradura") no tocan stock.
     */
    @Transactional
    public Venta registrarVenta(Venta venta) {
        // Vincula la venta a la caja abierta en este momento, si hay una (sin filtrar por fecha:
        // una sesión de un día anterior que nunca se cerró sigue "ABIERTA"). Permite el arqueo por
        // turno; no es obligatorio tener una caja abierta para vender (no se pidió esa regla).
        sesionRepo.findByEstado("ABIERTA").ifPresent(venta::setSesion);

        BigDecimal totalBruto = BigDecimal.ZERO;

        for (DetalleVenta d : venta.getDetalles()) {
            if (d.getProducto() != null) {
                Producto p = productoRepo.findById(d.getProducto().getIdProducto())
                    .orElseThrow(() -> new IllegalArgumentException("Producto no existe"));

                if (p.getStockActual() < d.getCantidad()) {
                    throw new IllegalStateException("Stock insuficiente para " + p.getDescripcion());
                }

                p.setStockActual(p.getStockActual() - d.getCantidad());
                productoRepo.save(p);
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

        if (descuento.compareTo(BigDecimal.ZERO) > 0) {
            String codigo = otpService.generarCodigo();
            venta.setEstado("PENDIENTE_AUTORIZACION");
            venta.setOtpHash(otpService.hash(codigo));
            venta.setOtpExpiraEn(otpService.nuevaExpiracion());

            Venta guardada = ventaRepo.save(venta);

            emailService.enviarOtpAAdmins(
                "Autorización de descuento - venta #" + guardada.getIdVenta(),
                "Se registró la venta #" + guardada.getIdVenta() + " con un descuento de $" + descuento
                    + " (" + guardada.getMotivoDescuento() + ").\n"
                    + "Código de confirmación: " + codigo + "\n"
                    + "Vence en " + OtpService.VIGENCIA_MINUTOS + " minutos.");

            return guardada;
        }

        venta.setEstado("CONFIRMADA");
        return ventaRepo.save(venta);
    }

    @Transactional
    public Venta confirmarDescuento(Integer idVenta, String codigoIngresado) {
        Venta venta = ventaRepo.findById(idVenta)
            .orElseThrow(() -> new IllegalArgumentException("Venta no existe: " + idVenta));

        if (!"PENDIENTE_AUTORIZACION".equals(venta.getEstado())) {
            throw new IllegalStateException("La venta no tiene un descuento pendiente de autorización");
        }
        if (venta.getOtpExpiraEn().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("El código OTP expiró; la venta sigue pendiente de autorización");
        }
        if (!otpService.coincide(codigoIngresado, venta.getOtpHash())) {
            throw new AuthenticationFailedException("Código OTP inválido");
        }

        venta.setEstado("CONFIRMADA");
        venta.setOtpHash(null);
        venta.setOtpExpiraEn(null);
        return ventaRepo.save(venta);
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
     * (remito, más completo: cliente, dirección, descripción del trabajo, técnico).
     */
    private String construirHtmlComprobante(Venta venta) {
        List<ComprobanteHtmlBuilder.Linea> lineas = venta.getDetalles().stream()
            .map(d -> new ComprobanteHtmlBuilder.Linea(d.getDescripcion(), d.getCantidad(), d.getPrecioUnitario(),
                d.getSubtotal()))
            .toList();

        boolean esDomicilio = "DOMICILIO".equals(venta.getTipoVenta());
        String titulo = (esDomicilio ? "Remito de trabajo #" : "Comprobante de venta #") + venta.getIdVenta();

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

        return ComprobanteHtmlBuilder.construir(titulo, info, lineas, venta.getTotalVenta(),
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
                Producto p = productoRepo.findById(itemReq.getIdProducto())
                    .orElseThrow(() -> new IllegalArgumentException("Producto no existe: " + itemReq.getIdProducto()));
                if (p.getStockActual() < itemReq.getCantidad()) {
                    throw new IllegalStateException("Stock insuficiente para " + p.getDescripcion());
                }
                p.setStockActual(p.getStockActual() - itemReq.getCantidad());
                productoRepo.save(p);
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
