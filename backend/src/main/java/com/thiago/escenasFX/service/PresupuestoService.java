package com.thiago.escenasFX.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thiago.escenasFX.dto.DetallePresupuestoRequest;
import com.thiago.escenasFX.dto.PresupuestoRequest;
import com.thiago.escenasFX.model.DetallePresupuesto;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.Presupuesto;
import com.thiago.escenasFX.model.Producto;
import com.thiago.escenasFX.repository.PresupuestoRepository;
import com.thiago.escenasFX.repository.ProductoRepository;

@Service
public class PresupuestoService {

    private static final String NOMBRE_LOCAL = "D13 Distribuidora";

    private final PresupuestoRepository presupuestoRepo;
    private final ProductoRepository productoRepo;
    private final EmailService emailService;
    private final PdfService pdfService;

    public PresupuestoService(PresupuestoRepository presupuestoRepo, ProductoRepository productoRepo,
            EmailService emailService, PdfService pdfService) {
        this.presupuestoRepo = presupuestoRepo;
        this.productoRepo = productoRepo;
        this.emailService = emailService;
        this.pdfService = pdfService;
    }

    /**
     * A diferencia de VentaService.registrarVenta, esto es solo una cotización informativa: no
     * valida ni descuenta stock, no genera una Venta, no toca ninguna SesionCaja. Cada línea trae
     * un producto del catálogo o una descripción manual (trabajo sin precio fijo).
     */
    @Transactional
    public Presupuesto crear(PresupuestoRequest req, Empleado empleado) {
        Presupuesto presupuesto = new Presupuesto();
        presupuesto.setEmpleado(empleado);
        presupuesto.setClienteNombre(req.getClienteNombre());
        presupuesto.setClienteEmail(req.getClienteEmail());
        presupuesto.setClienteTelefono(req.getClienteTelefono());

        BigDecimal total = BigDecimal.ZERO;

        for (DetallePresupuestoRequest itemReq : req.getDetalles()) {
            DetallePresupuesto detalle = new DetallePresupuesto();
            detalle.setPresupuesto(presupuesto);

            if (itemReq.getIdProducto() != null) {
                Producto producto = productoRepo.findById(itemReq.getIdProducto())
                    .orElseThrow(() -> new IllegalArgumentException("Producto no existe: " + itemReq.getIdProducto()));
                detalle.setProducto(producto);
                detalle.setDescripcion(producto.getDescripcion());
            } else {
                if (itemReq.getDescripcion() == null || itemReq.getDescripcion().isBlank()) {
                    throw new IllegalArgumentException(
                        "Cada línea del presupuesto necesita un producto o una descripción manual");
                }
                detalle.setDescripcion(itemReq.getDescripcion());
            }

            BigDecimal subtotal = itemReq.getPrecioUnitario().multiply(BigDecimal.valueOf(itemReq.getCantidad()));
            detalle.setCantidad(itemReq.getCantidad());
            detalle.setPrecioUnitario(itemReq.getPrecioUnitario());
            detalle.setSubtotal(subtotal);
            presupuesto.getDetalles().add(detalle);

            total = total.add(subtotal);
        }

        presupuesto.setTotalPresupuesto(total);
        return presupuestoRepo.save(presupuesto);
    }

    public List<Presupuesto> listarPorRango(LocalDate desde, LocalDate hasta) {
        return presupuestoRepo.findByFechaBetweenOrderByFechaDesc(desde.atStartOfDay(), hasta.atTime(23, 59, 59));
    }

    public Presupuesto obtenerPorId(Integer idPresupuesto) {
        return presupuestoRepo.findById(idPresupuesto)
            .orElseThrow(() -> new IllegalArgumentException("Presupuesto no existe: " + idPresupuesto));
    }

    public byte[] generarPdf(Integer idPresupuesto) {
        return pdfService.generarPdf(construirHtml(obtenerPorId(idPresupuesto)));
    }

    @Transactional
    public Presupuesto enviarPorEmail(Integer idPresupuesto) {
        Presupuesto presupuesto = obtenerPorId(idPresupuesto);
        if (presupuesto.getClienteEmail() == null || presupuesto.getClienteEmail().isBlank()) {
            throw new IllegalStateException("Este presupuesto no tiene un email de cliente cargado");
        }

        byte[] pdf = pdfService.generarPdf(construirHtml(presupuesto));
        String asunto = "Presupuesto #" + presupuesto.getIdPresupuesto() + " - " + NOMBRE_LOCAL;
        String cuerpo = "Te enviamos tu presupuesto en PDF adjunto. Gracias por elegirnos — " + NOMBRE_LOCAL + ".";
        emailService.enviarConAdjuntoPdf(presupuesto.getClienteEmail(), asunto, cuerpo,
            "presupuesto-" + presupuesto.getIdPresupuesto() + ".pdf", pdf);

        presupuesto.setEnviadoPorEmail(true);
        return presupuestoRepo.save(presupuesto);
    }

    private String construirHtml(Presupuesto presupuesto) {
        List<ComprobanteHtmlBuilder.Linea> lineas = presupuesto.getDetalles().stream()
            .map(d -> new ComprobanteHtmlBuilder.Linea(d.getDescripcion(), d.getCantidad(), d.getPrecioUnitario(),
                d.getSubtotal()))
            .toList();

        return ComprobanteHtmlBuilder.construir(
            "Presupuesto #" + presupuesto.getIdPresupuesto(),
            List.of("Para: " + presupuesto.getClienteNombre()),
            lineas,
            presupuesto.getTotalPresupuesto(),
            null,
            pdfService.logoDataUri());
    }
}
