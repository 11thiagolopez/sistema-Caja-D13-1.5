package com.thiago.escenasFX.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thiago.escenasFX.dto.CompraItemRequest;
import com.thiago.escenasFX.dto.CompraRequest;
import com.thiago.escenasFX.dto.DetalleDevolucionProveedorRequest;
import com.thiago.escenasFX.dto.DevolucionProveedorRequest;
import com.thiago.escenasFX.dto.PagoProveedorDTO;
import com.thiago.escenasFX.dto.ProductoComprasRankingDTO;
import com.thiago.escenasFX.dto.ProductoRequest;
import com.thiago.escenasFX.model.Compra;
import com.thiago.escenasFX.model.CompraItem;
import com.thiago.escenasFX.model.DetalleDevolucionProveedor;
import com.thiago.escenasFX.model.DevolucionProveedor;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.MovimientoCuentaCorrienteProveedor;
import com.thiago.escenasFX.model.Producto;
import com.thiago.escenasFX.model.Proveedor;
import com.thiago.escenasFX.repository.CompraRepository;
import com.thiago.escenasFX.repository.DevolucionProveedorRepository;
import com.thiago.escenasFX.repository.MovimientoCuentaCorrienteProveedorRepository;
import com.thiago.escenasFX.repository.ProductoRepository;

@Service
public class CompraService {

    private final CompraRepository compraRepo;
    private final ProductoRepository productoRepo;
    private final ProductoService productoService;
    private final ProveedorService proveedorService;
    private final MovimientoCuentaCorrienteProveedorRepository movimientoProveedorRepo;
    private final DevolucionProveedorRepository devolucionProveedorRepo;

    public CompraService(CompraRepository compraRepo, ProductoRepository productoRepo,
            ProductoService productoService, ProveedorService proveedorService,
            MovimientoCuentaCorrienteProveedorRepository movimientoProveedorRepo,
            DevolucionProveedorRepository devolucionProveedorRepo) {
        this.compraRepo = compraRepo;
        this.productoRepo = productoRepo;
        this.productoService = productoService;
        this.proveedorService = proveedorService;
        this.movimientoProveedorRepo = movimientoProveedorRepo;
        this.devolucionProveedorRepo = devolucionProveedorRepo;
    }

    @Transactional
    public Compra registrarCompra(CompraRequest req, Empleado empleado) {
        if (req.getFecha().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La fecha de la compra no puede ser futura");
        }

        Proveedor proveedor = proveedorService.resolverOCrear(req.getProveedorNombre());

        Compra compra = new Compra();
        compra.setFecha(req.getFecha());
        compra.setProveedor(proveedor);
        compra.setMedioPago(req.getMedioPago());
        compra.setEmpleadoRegistro(empleado);

        BigDecimal total = BigDecimal.ZERO;

        for (CompraItemRequest itemReq : req.getItems()) {
            Producto producto = resolverProducto(itemReq, proveedor);

            producto.setStockActual(producto.getStockActual() + itemReq.getCantidad());
            producto.setPrecioCompra(itemReq.getPrecioCompraUnitario());
            if (itemReq.getPrecioVentaUnitario() != null) {
                producto.setPrecioVenta(itemReq.getPrecioVentaUnitario());
            }
            productoService.sincronizarAnclaUsd(producto);
            productoRepo.save(producto);

            BigDecimal subtotal = itemReq.getPrecioCompraUnitario().multiply(BigDecimal.valueOf(itemReq.getCantidad()));

            CompraItem item = new CompraItem();
            item.setCompra(compra);
            item.setProducto(producto);
            item.setCantidad(itemReq.getCantidad());
            item.setPrecioCompraUnitario(itemReq.getPrecioCompraUnitario());
            item.setPrecioVentaUnitario(itemReq.getPrecioVentaUnitario());
            item.setSubtotal(subtotal);
            compra.getItems().add(item);

            total = total.add(subtotal);
        }

        compra.setTotalCompra(total);

        // Cuenta corriente de proveedor: ausente = se paga todo de una (comportamiento de
        // siempre, cero cambio de conducta para quien no usa esto). Un valor menor deja el resto
        // como deuda pendiente, pagable después con registrarPago.
        BigDecimal montoPagado = req.getMontoPagado() != null ? req.getMontoPagado() : total;
        if (montoPagado.compareTo(BigDecimal.ZERO) < 0 || montoPagado.compareTo(total) > 0) {
            throw new IllegalArgumentException("El monto pagado no puede ser negativo ni mayor al total de la compra");
        }
        compra.setMontoPagado(montoPagado);

        Compra guardada = compraRepo.save(compra);

        registrarMovimientoProveedor(proveedor, "DEBE_COMPRA", total, guardada, null, null, empleado,
            "Compra #" + guardada.getIdCompra());
        if (montoPagado.compareTo(BigDecimal.ZERO) > 0) {
            registrarMovimientoProveedor(proveedor, "HABER_PAGO", montoPagado, guardada, req.getMedioPago(), null,
                empleado, "Pago al registrar la compra #" + guardada.getIdCompra());
        }

        return guardada;
    }

    /**
     * Pago posterior contra una compra puntual ya registrada — a diferencia del pago inicial de
     * registrarCompra, este sí puede dejar saldo pendiente parcial. No lockea la fila de Compra:
     * es una operación manual de ADMIN, de volumen bajo, mismo criterio de riesgo aceptado que
     * NotaCreditoService documenta para su guarda de doble acreditación.
     */
    @Transactional
    public Compra registrarPago(Integer idCompra, BigDecimal monto, String medioPago, Empleado empleado) {
        Compra compra = compraRepo.findById(idCompra)
            .orElseThrow(() -> new IllegalArgumentException("Compra no existe: " + idCompra));

        BigDecimal montoPagadoActual = compra.getMontoPagado() != null ? compra.getMontoPagado() : BigDecimal.ZERO;
        BigDecimal saldoPendiente = compra.getTotalCompra().subtract(montoPagadoActual);
        if (monto.compareTo(saldoPendiente) > 0) {
            throw new IllegalStateException("El pago supera el saldo pendiente de la compra #" + idCompra
                + " (pendiente: $" + saldoPendiente + ", pagado: $" + monto + ")");
        }

        compra.setMontoPagado(montoPagadoActual.add(monto));
        Compra guardada = compraRepo.save(compra);

        registrarMovimientoProveedor(compra.getProveedor(), "HABER_PAGO", monto, guardada, medioPago, null,
            empleado, "Pago contra la compra #" + idCompra);

        return guardada;
    }

    private void registrarMovimientoProveedor(Proveedor proveedor, String tipo, BigDecimal monto, Compra compra,
            String medioPago, DevolucionProveedor devolucion, Empleado empleado, String descripcion) {
        MovimientoCuentaCorrienteProveedor movimiento = new MovimientoCuentaCorrienteProveedor();
        movimiento.setProveedor(proveedor);
        movimiento.setTipo(tipo);
        movimiento.setMonto(monto);
        movimiento.setCompra(compra);
        movimiento.setMedioPago(medioPago);
        movimiento.setDevolucion(devolucion);
        movimiento.setDescripcion(descripcion);
        movimiento.setEmpleado(empleado);
        movimientoProveedorRepo.save(movimiento);
    }

    private Producto resolverProducto(CompraItemRequest itemReq, Proveedor proveedor) {
        if (itemReq.getIdProducto() != null) {
            return productoRepo.findById(itemReq.getIdProducto())
                .orElseThrow(() -> new IllegalArgumentException("Producto no existe: " + itemReq.getIdProducto()));
        }
        if (itemReq.getNuevoProducto() == null) {
            throw new IllegalArgumentException(
                "Cada renglón de la compra debe traer idProducto o los datos de un producto nuevo");
        }

        ProductoRequest nuevo = new ProductoRequest();
        nuevo.setRubro(itemReq.getNuevoProducto().getRubro());
        nuevo.setFamilia(itemReq.getNuevoProducto().getFamilia());
        nuevo.setMarca(itemReq.getNuevoProducto().getMarca());
        nuevo.setProveedor(proveedor.getNombre());
        nuevo.setCodigoFabrica(itemReq.getNuevoProducto().getCodigoFabrica());
        nuevo.setDescripcion(itemReq.getNuevoProducto().getDescripcion());
        nuevo.setPrecioVenta(itemReq.getPrecioVentaUnitario());
        nuevo.setPrecioCompra(itemReq.getPrecioCompraUnitario());
        // Arranca en 0: el stock de este mismo renglón se suma después, junto con el de
        // productos existentes, para no duplicar la cantidad.
        nuevo.setStockActual(0);
        return productoService.crear(nuevo);
    }

    public List<Compra> listarPorRango(LocalDate desde, LocalDate hasta) {
        return compraRepo.findByFechaBetweenOrderByFechaDesc(desde, hasta);
    }

    public List<PagoProveedorDTO> pagosPorProveedor(LocalDate desde, LocalDate hasta) {
        List<Compra> compras = listarPorRango(desde, hasta);

        return compras.stream()
            .filter(c -> c.getProveedor() != null)
            .collect(Collectors.groupingBy(c -> c.getProveedor().getIdProveedor()))
            .values().stream()
            .map(grupo -> {
                Proveedor proveedor = grupo.get(0).getProveedor();
                BigDecimal total = grupo.stream().map(Compra::getTotalCompra).reduce(BigDecimal.ZERO, BigDecimal::add);
                return new PagoProveedorDTO(proveedor.getIdProveedor(), proveedor.getNombre(), total, grupo.size());
            })
            .sorted(Comparator.comparing(PagoProveedorDTO::getTotalPagado).reversed())
            .toList();
    }

    public List<ProductoComprasRankingDTO> productosMasComprados(LocalDate desde, LocalDate hasta, int limit) {
        List<Compra> compras = listarPorRango(desde, hasta);

        List<CompraItem> items = compras.stream().flatMap(c -> c.getItems().stream()).toList();

        return items.stream()
            .collect(Collectors.groupingBy(i -> i.getProducto().getIdProducto()))
            .values().stream()
            .map(grupo -> {
                Producto producto = grupo.get(0).getProducto();
                long cantidad = grupo.stream().mapToLong(CompraItem::getCantidad).sum();
                BigDecimal total = grupo.stream().map(CompraItem::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
                return new ProductoComprasRankingDTO(producto.getIdProducto(), producto.getDescripcion(), cantidad, total);
            })
            .sorted(Comparator.comparingLong(ProductoComprasRankingDTO::getCantidadComprada).reversed())
            .limit(limit)
            .toList();
    }

    public List<MovimientoCuentaCorrienteProveedor> movimientosProveedor(Integer idProveedor) {
        return movimientoProveedorRepo.findByProveedorIdProveedorOrderByFechaDesc(idProveedor);
    }

    public List<DevolucionProveedor> listarDevolucionesPorRango(LocalDateTime desde, LocalDateTime hasta) {
        return devolucionProveedorRepo.findByFechaBetweenOrderByFechaDesc(desde, hasta);
    }

    // Mismo criterio que ClienteService.calcularSaldo: nunca se guarda un saldo suelto, se
    // recalcula siempre sumando el ledger. Acá DEBE_COMPRA suma lo que le debemos al proveedor,
    // HABER_PAGO/HABER_DEVOLUCION lo restan.
    public BigDecimal calcularSaldoProveedor(Integer idProveedor) {
        BigDecimal saldo = BigDecimal.ZERO;
        for (MovimientoCuentaCorrienteProveedor m : movimientosProveedor(idProveedor)) {
            saldo = "DEBE_COMPRA".equals(m.getTipo()) ? saldo.add(m.getMonto()) : saldo.subtract(m.getMonto());
        }
        return saldo;
    }

    /**
     * Devolución de mercadería a un proveedor — espejo del lado compra de la Nota de Crédito,
     * sin AFIP (B2B interno). Reduce stock real y acredita la cuenta corriente GENERAL del
     * proveedor (no earmarked contra una compra puntual, ver DevolucionProveedor).
     */
    @Transactional
    public DevolucionProveedor registrarDevolucion(DevolucionProveedorRequest req, Empleado empleado) {
        Proveedor proveedor = proveedorService.obtenerPorId(req.getIdProveedor());
        Compra compraOriginal = req.getIdCompraOriginal() != null
            ? compraRepo.findById(req.getIdCompraOriginal())
                .orElseThrow(() -> new IllegalArgumentException("Compra no existe: " + req.getIdCompraOriginal()))
            : null;

        List<DetalleDevolucionProveedor> detalles = new ArrayList<>();
        BigDecimal montoTotal = BigDecimal.ZERO;

        for (DetalleDevolucionProveedorRequest lineaReq : req.getDetalles()) {
            Producto producto = productoRepo.buscarPorIdConLock(lineaReq.getIdProducto())
                .orElseThrow(() -> new IllegalArgumentException("Producto no existe: " + lineaReq.getIdProducto()));
            if (producto.getStockActual() < lineaReq.getCantidad()) {
                throw new IllegalStateException("Stock insuficiente para devolver " + producto.getDescripcion());
            }
            producto.setStockActual(producto.getStockActual() - lineaReq.getCantidad());
            productoRepo.save(producto);

            BigDecimal subtotal = lineaReq.getPrecioUnitario().multiply(BigDecimal.valueOf(lineaReq.getCantidad()));
            DetalleDevolucionProveedor detalle = new DetalleDevolucionProveedor();
            detalle.setProducto(producto);
            detalle.setCantidad(lineaReq.getCantidad());
            detalle.setPrecioUnitario(lineaReq.getPrecioUnitario());
            detalle.setSubtotal(subtotal);
            detalles.add(detalle);

            montoTotal = montoTotal.add(subtotal);
        }

        DevolucionProveedor devolucion = new DevolucionProveedor();
        devolucion.setProveedor(proveedor);
        devolucion.setCompraOriginal(compraOriginal);
        devolucion.setMotivo(req.getMotivo());
        devolucion.setMontoTotal(montoTotal);
        devolucion.setEmpleado(empleado);
        detalles.forEach(d -> d.setDevolucion(devolucion));
        devolucion.setDetalles(detalles);

        DevolucionProveedor guardada = devolucionProveedorRepo.save(devolucion);

        registrarMovimientoProveedor(proveedor, "HABER_DEVOLUCION", montoTotal, null, null, guardada, empleado,
            "Devolución #" + guardada.getIdDevolucion()
                + (req.getMotivo() != null && !req.getMotivo().isBlank() ? " (" + req.getMotivo() + ")" : ""));

        return guardada;
    }
}
