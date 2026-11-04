package com.thiago.escenasFX.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thiago.escenasFX.dto.DetalleNotaCreditoRequest;
import com.thiago.escenasFX.dto.NotaCreditoRequest;
import com.thiago.escenasFX.model.Cliente;
import com.thiago.escenasFX.model.DetalleNotaCredito;
import com.thiago.escenasFX.model.DetalleVenta;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.MovimientoCuentaCorrienteCliente;
import com.thiago.escenasFX.model.NotaCredito;
import com.thiago.escenasFX.model.Producto;
import com.thiago.escenasFX.model.Venta;
import com.thiago.escenasFX.repository.ClienteRepository;
import com.thiago.escenasFX.repository.DetalleNotaCreditoRepository;
import com.thiago.escenasFX.repository.DetalleVentaRepository;
import com.thiago.escenasFX.repository.FacturaFiscalRepository;
import com.thiago.escenasFX.repository.MovimientoCuentaCorrienteClienteRepository;
import com.thiago.escenasFX.repository.NotaCreditoRepository;
import com.thiago.escenasFX.repository.ProductoRepository;
import com.thiago.escenasFX.repository.VentaRepository;

/**
 * Fase 1: la nota de crédito es siempre interna (sin AFIP, sin CAE) aunque la venta original
 * tenga factura fiscal — esFiscal queda guardado para que una fase futura sepa qué notas quedaron
 * pendientes de emitir ante ARCA. Nunca modifica la Venta/DetalleVenta original: es un documento
 * aparte vinculado a ella (mismo espíritu que FacturaFiscal, que tampoco toca la Venta).
 */
@Service
public class NotaCreditoService {

    private static final List<String> MOTIVOS_VALIDOS = List.of(
        "DEVOLUCION_MERCADERIA", "ERROR_TIPEO", "DESCUENTO_POSTERIOR", "ANULACION_TOTAL");
    private static final List<String> MOTIVOS_QUE_DEVUELVEN_STOCK = List.of(
        "DEVOLUCION_MERCADERIA", "ANULACION_TOTAL");

    private final NotaCreditoRepository notaCreditoRepo;
    private final DetalleNotaCreditoRepository detalleNotaCreditoRepo;
    private final VentaRepository ventaRepo;
    private final DetalleVentaRepository detalleVentaRepo;
    private final ProductoRepository productoRepo;
    private final ClienteRepository clienteRepo;
    private final FacturaFiscalRepository facturaFiscalRepo;
    private final MovimientoCuentaCorrienteClienteRepository movimientoRepo;
    private final PdfService pdfService;

    public NotaCreditoService(NotaCreditoRepository notaCreditoRepo,
            DetalleNotaCreditoRepository detalleNotaCreditoRepo, VentaRepository ventaRepo,
            DetalleVentaRepository detalleVentaRepo, ProductoRepository productoRepo,
            ClienteRepository clienteRepo, FacturaFiscalRepository facturaFiscalRepo,
            MovimientoCuentaCorrienteClienteRepository movimientoRepo, PdfService pdfService) {
        this.notaCreditoRepo = notaCreditoRepo;
        this.detalleNotaCreditoRepo = detalleNotaCreditoRepo;
        this.ventaRepo = ventaRepo;
        this.detalleVentaRepo = detalleVentaRepo;
        this.productoRepo = productoRepo;
        this.clienteRepo = clienteRepo;
        this.facturaFiscalRepo = facturaFiscalRepo;
        this.movimientoRepo = movimientoRepo;
        this.pdfService = pdfService;
    }

    @Transactional
    public NotaCredito crear(NotaCreditoRequest req, Empleado empleado) {
        if (!MOTIVOS_VALIDOS.contains(req.getMotivo())) {
            throw new IllegalArgumentException("Motivo inválido: " + req.getMotivo());
        }
        if (!"TOTAL".equals(req.getAlcance()) && !"PARCIAL".equals(req.getAlcance())) {
            throw new IllegalArgumentException("Alcance inválido: " + req.getAlcance());
        }
        if ("PARCIAL".equals(req.getAlcance()) && (req.getDetalles() == null || req.getDetalles().isEmpty())) {
            throw new IllegalArgumentException("Una nota de crédito parcial necesita al menos una línea");
        }

        Venta venta = ventaRepo.findById(req.getIdVentaOriginal())
            .orElseThrow(() -> new IllegalArgumentException("Venta no existe: " + req.getIdVentaOriginal()));
        if (!"CONFIRMADA".equals(venta.getEstado())) {
            throw new IllegalStateException("Solo se puede emitir una nota de crédito sobre una venta CONFIRMADA");
        }

        Cliente cliente = resolverCliente(req, venta);
        boolean devuelveStock = MOTIVOS_QUE_DEVUELVEN_STOCK.contains(req.getMotivo());

        List<DetalleNotaCreditoRequest> lineas = "TOTAL".equals(req.getAlcance())
            ? expandirATodasLasLineas(venta)
            : req.getDetalles();

        List<DetalleNotaCredito> detalles = new ArrayList<>();
        BigDecimal montoTotal = BigDecimal.ZERO;
        BigDecimal bruto = bruto(venta);
        boolean hayDescuento = venta.getDescuento() != null && venta.getDescuento().signum() > 0 && bruto.signum() > 0;
        Map<Integer, Integer> acreditadoAhora = new HashMap<>();

        for (DetalleNotaCreditoRequest lineaReq : lineas) {
            DetalleVenta original = detalleVentaRepo.findById(lineaReq.getIdDetalleVentaOriginal())
                .orElseThrow(() -> new IllegalArgumentException(
                    "Línea de venta no existe: " + lineaReq.getIdDetalleVentaOriginal()));
            if (!original.getVenta().getIdVenta().equals(venta.getIdVenta())) {
                throw new IllegalArgumentException(
                    "La línea " + original.getIdDetalle() + " no pertenece a la venta #" + venta.getIdVenta());
            }

            int yaAcreditado = detalleNotaCreditoRepo.sumarCantidadAcreditada(original.getIdDetalle());
            int remanente = original.getCantidad() - yaAcreditado;
            if (lineaReq.getCantidad() > remanente) {
                throw new IllegalStateException("La línea " + original.getIdDetalle() + " ya tiene "
                    + yaAcreditado + " unidad(es) acreditada(s) de " + original.getCantidad()
                    + "; quedan " + remanente + " disponibles");
            }

            if (devuelveStock && original.getProducto() != null) {
                Producto p = productoRepo.buscarPorIdConLock(original.getProducto().getIdProducto())
                    .orElseThrow(() -> new IllegalArgumentException("Producto no existe"));
                p.setStockActual(p.getStockActual() + lineaReq.getCantidad());
                productoRepo.save(p);
            }

            BigDecimal precioUnitario = original.getPrecioUnitario();
            BigDecimal subtotal = precioUnitario.multiply(BigDecimal.valueOf(lineaReq.getCantidad()));
            if (hayDescuento) {
                // Se acredita lo que se cobró: el descuento de la venta se reparte entre sus líneas.
                subtotal = prorratear(subtotal, venta.getTotalVenta(), bruto);
                precioUnitario = prorratear(precioUnitario, venta.getTotalVenta(), bruto);
            }

            DetalleNotaCredito detalle = new DetalleNotaCredito();
            detalle.setDetalleVentaOriginal(original);
            detalle.setProducto(original.getProducto());
            detalle.setDescripcion(original.getDescripcion());
            detalle.setCantidad(lineaReq.getCantidad());
            detalle.setPrecioUnitario(precioUnitario);
            detalle.setSubtotal(subtotal);
            detalles.add(detalle);
            acreditadoAhora.merge(original.getIdDetalle(), lineaReq.getCantidad(), Integer::sum);

            montoTotal = montoTotal.add(subtotal);
        }

        if (hayDescuento && !detalles.isEmpty()) {
            montoTotal = ajustarRedondeo(venta, detalles, montoTotal, acreditadoAhora);
        }

        NotaCredito nc = new NotaCredito();
        nc.setVentaOriginal(venta);
        nc.setCliente(cliente);
        nc.setMotivo(req.getMotivo());
        nc.setAlcance(req.getAlcance());
        nc.setMontoTotal(montoTotal);
        boolean esFiscal = facturaFiscalRepo.findByVentaIdVenta(venta.getIdVenta())
            .filter(f -> "EMITIDA".equals(f.getEstado()))
            .isPresent();
        nc.setEsFiscal(esFiscal);
        // Interna: ya está todo hecho (stock + saldo). Fiscal: falta la llamada a ARCA
        // (NotaCreditoFiscalService.emitir), así que no puede quedar "EMITIDA" todavía.
        nc.setEstado(esFiscal ? "PENDIENTE_FISCAL" : "EMITIDA");
        nc.setEmpleado(empleado);
        detalles.forEach(d -> d.setNotaCredito(nc));
        nc.setDetalles(detalles);

        NotaCredito guardada = notaCreditoRepo.save(nc);

        MovimientoCuentaCorrienteCliente movimiento = new MovimientoCuentaCorrienteCliente();
        movimiento.setCliente(cliente);
        movimiento.setTipo("CREDITO");
        movimiento.setMonto(montoTotal);
        movimiento.setNotaCredito(guardada);
        movimiento.setDescripcion("Nota de crédito #" + guardada.getIdNotaCredito()
            + " — venta #" + venta.getIdVenta() + " (" + req.getMotivo() + ")");
        movimiento.setEmpleado(empleado);
        movimientoRepo.save(movimiento);

        return guardada;
    }

    private BigDecimal bruto(Venta venta) {
        return venta.getDetalles().stream()
            .map(d -> d.getPrecioUnitario().multiply(BigDecimal.valueOf(d.getCantidad())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal prorratear(BigDecimal monto, BigDecimal totalVenta, BigDecimal bruto) {
        return monto.multiply(totalVenta).divide(bruto, 2, RoundingMode.HALF_UP);
    }

    // Prorratear línea por línea puede correr unos centavos. Las notas de una venta nunca superan lo
    // cobrado, y la que completa todas las líneas cierra justo en ese total.
    private BigDecimal ajustarRedondeo(Venta venta, List<DetalleNotaCredito> detalles, BigDecimal montoTotal,
            Map<Integer, Integer> acreditadoAhora) {
        BigDecimal disponible = venta.getTotalVenta().subtract(notaCreditoRepo.sumarMontoPorVenta(venta.getIdVenta()));
        boolean completaLaVenta = venta.getDetalles().stream().allMatch(d ->
            detalleNotaCreditoRepo.sumarCantidadAcreditada(d.getIdDetalle())
                + acreditadoAhora.getOrDefault(d.getIdDetalle(), 0) >= d.getCantidad());
        if (!completaLaVenta && montoTotal.compareTo(disponible) <= 0) {
            return montoTotal;
        }
        DetalleNotaCredito ultima = detalles.get(detalles.size() - 1);
        ultima.setSubtotal(ultima.getSubtotal().add(disponible.subtract(montoTotal)));
        return disponible;
    }

    public List<NotaCredito> listarPorRango(LocalDateTime desde, LocalDateTime hasta) {
        return notaCreditoRepo.findByFechaBetweenOrderByFechaDesc(desde, hasta);
    }

    public NotaCredito obtenerPorId(Integer id) {
        return notaCreditoRepo.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Nota de crédito no existe: " + id));
    }

    public byte[] generarPdf(Integer id) {
        NotaCredito nc = obtenerPorId(id);
        List<ComprobanteHtmlBuilder.Linea> lineas = nc.getDetalles().stream()
            .map(d -> new ComprobanteHtmlBuilder.Linea(d.getDescripcion(), d.getCantidad(), d.getPrecioUnitario(),
                d.getSubtotal()))
            .toList();
        List<String> info = List.of(
            "Cliente: " + nc.getCliente().getNombre(),
            "Venta original: #" + nc.getVentaOriginal().getIdVenta(),
            "Motivo: " + nc.getMotivo());
        String html = TicketHtmlBuilder.construir(info, lineas, nc.getMontoTotal(),
            "Nota de crédito interna, no válida como factura fiscal.", pdfService.logoDataUri());
        return pdfService.generarPdf(html);
    }

    private Cliente resolverCliente(NotaCreditoRequest req, Venta venta) {
        if (req.getIdCliente() != null) {
            return clienteRepo.findById(req.getIdCliente())
                .orElseThrow(() -> new IllegalArgumentException("Cliente no existe: " + req.getIdCliente()));
        }
        if (venta.getCliente() != null) {
            return venta.getCliente();
        }
        throw new IllegalArgumentException("Hay que indicar un cliente para emitir la nota de crédito");
    }

    private List<DetalleNotaCreditoRequest> expandirATodasLasLineas(Venta venta) {
        List<DetalleNotaCreditoRequest> lineas = new ArrayList<>();
        for (DetalleVenta original : venta.getDetalles()) {
            int yaAcreditado = detalleNotaCreditoRepo.sumarCantidadAcreditada(original.getIdDetalle());
            int remanente = original.getCantidad() - yaAcreditado;
            if (remanente <= 0) {
                continue;
            }
            DetalleNotaCreditoRequest linea = new DetalleNotaCreditoRequest();
            linea.setIdDetalleVentaOriginal(original.getIdDetalle());
            linea.setCantidad(remanente);
            lineas.add(linea);
        }
        if (lineas.isEmpty()) {
            throw new IllegalStateException("La venta #" + venta.getIdVenta()
                + " ya tiene todas sus líneas acreditadas por notas de crédito anteriores");
        }
        return lineas;
    }
}
