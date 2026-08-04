package com.thiago.escenasFX.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thiago.escenasFX.dto.CompraItemRequest;
import com.thiago.escenasFX.dto.CompraRequest;
import com.thiago.escenasFX.dto.PagoProveedorDTO;
import com.thiago.escenasFX.dto.ProductoComprasRankingDTO;
import com.thiago.escenasFX.dto.ProductoRequest;
import com.thiago.escenasFX.model.Compra;
import com.thiago.escenasFX.model.CompraItem;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.Producto;
import com.thiago.escenasFX.model.Proveedor;
import com.thiago.escenasFX.repository.CompraRepository;
import com.thiago.escenasFX.repository.ProductoRepository;

@Service
public class CompraService {

    private final CompraRepository compraRepo;
    private final ProductoRepository productoRepo;
    private final ProductoService productoService;
    private final ProveedorService proveedorService;

    public CompraService(CompraRepository compraRepo, ProductoRepository productoRepo,
            ProductoService productoService, ProveedorService proveedorService) {
        this.compraRepo = compraRepo;
        this.productoRepo = productoRepo;
        this.productoService = productoService;
        this.proveedorService = proveedorService;
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
        return compraRepo.save(compra);
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
}
