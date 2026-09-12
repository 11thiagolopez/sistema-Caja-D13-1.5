package com.thiago.escenasFX.service;

import java.math.RoundingMode;
import java.util.List;

import org.springframework.stereotype.Service;

import com.thiago.escenasFX.dto.ProductoRequest;
import com.thiago.escenasFX.dto.ProductoUpdateRequest;
import com.thiago.escenasFX.model.Marca;
import com.thiago.escenasFX.model.Producto;
import com.thiago.escenasFX.repository.ProductoRepository;

@Service
public class ProductoService {

    private final ProductoRepository productoRepo;
    private final MarcaService marcaService;
    private final ProveedorService proveedorService;
    private final CotizacionService cotizacionService;

    public ProductoService(ProductoRepository productoRepo, MarcaService marcaService,
            ProveedorService proveedorService, CotizacionService cotizacionService) {
        this.productoRepo = productoRepo;
        this.marcaService = marcaService;
        this.proveedorService = proveedorService;
        this.cotizacionService = cotizacionService;
    }

    public List<Producto> listarTodos() {
        return productoRepo.findByActivoTrueOrderByDescripcionAsc();
    }

    public Producto obtenerPorId(Integer id) {
        return productoRepo.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Producto no existe: " + id));
    }

    /**
     * Da de alta un producto generando codigoInterno = rubro+familia+marca+correlativo. El
     * correlativo es un contador de 4 dígitos que arranca en 0001 para cada combinación nueva de
     * rubro+familia+marca, y sigue subiendo dentro de esa misma combinación.
     */
    public Producto crear(ProductoRequest req) {
        Marca marca = marcaService.resolverOCrear(req.getMarca());

        List<Producto> existentes = productoRepo
            .findByRubroAndFamiliaAndNumeroMarcaOrderByCorrelativoDesc(req.getRubro(), req.getFamilia(), marca.getCodigo());

        int correlativoAnterior = existentes.isEmpty() ? 0 : Integer.parseInt(existentes.get(0).getCorrelativo());
        String correlativo = String.format("%04d", correlativoAnterior + 1);
        String codigoInterno = req.getRubro() + req.getFamilia() + marca.getCodigo() + correlativo;

        Producto producto = new Producto();
        producto.setRubro(req.getRubro());
        producto.setFamilia(req.getFamilia());
        producto.setNumeroMarca(marca.getCodigo());
        producto.setMarca(marca.getNombre());
        producto.setCorrelativo(correlativo);
        producto.setCodigoInterno(codigoInterno);
        producto.setProveedor(req.getProveedor());
        producto.setProveedorRef(proveedorService.resolverOCrear(req.getProveedor()));
        producto.setCodigoFabrica(req.getCodigoFabrica());
        producto.setDescripcion(req.getDescripcion());
        producto.setPrecioVenta(req.getPrecioVenta());
        producto.setPrecioCompra(req.getPrecioCompra());
        producto.setStockActual(req.getStockActual());
        producto.setActivo(true);

        asignarCodigoBarras(producto, null);
        sincronizarAnclaUsd(producto);
        return productoRepo.save(producto);
    }

    /**
     * codigoBarras = codigoFabrica si el producto tiene código de fábrica cargado, o codigoInterno
     * si no (productos sueltos/cortados a medida, sin envoltorio con EAN) — nunca se genera un
     * código nuevo, es sólo un espejo de uno de esos dos valores ya existentes. Valida que ningún
     * otro producto ya lo esté usando (idAExcluir es el propio producto en una edición, o null en
     * el alta) antes de asignarlo — el conflicto se resuelve acá, no dejando que la constraint
     * UNIQUE de la base lo rechace con un 500 genérico.
     */
    private void asignarCodigoBarras(Producto producto, Integer idAExcluir) {
        String candidato = (producto.getCodigoFabrica() != null && !producto.getCodigoFabrica().isBlank())
            ? producto.getCodigoFabrica()
            : producto.getCodigoInterno();

        productoRepo.findByCodigoBarras(candidato).ifPresent(existente -> {
            if (idAExcluir == null || !existente.getIdProducto().equals(idAExcluir)) {
                throw new IllegalStateException(
                    "Ya existe otro producto con el código de barras/fábrica \"" + candidato + "\"");
            }
        });
        producto.setCodigoBarras(candidato);
    }

    /**
     * Edición en línea desde la tabla de Productos (solo ADMIN, ver SecurityConfig). No toca
     * rubro/numeroMarca/codigoInterno a propósito — ver ProductoUpdateRequest.
     */
    public Producto actualizar(Integer id, ProductoUpdateRequest req) {
        Producto producto = obtenerPorId(id);
        if (req.getDescripcion() != null) {
            producto.setDescripcion(req.getDescripcion());
        }
        if (req.getMarca() != null) {
            producto.setMarca(req.getMarca());
        }
        if (req.getPrecioVenta() != null) {
            producto.setPrecioVenta(req.getPrecioVenta());
        }
        if (req.getPrecioCompra() != null) {
            producto.setPrecioCompra(req.getPrecioCompra());
        }
        if (req.getStockActual() != null) {
            producto.setStockActual(req.getStockActual());
        }
        if (req.getCodigoFabrica() != null) {
            producto.setCodigoFabrica(req.getCodigoFabrica().isBlank() ? null : req.getCodigoFabrica());
            asignarCodigoBarras(producto, id);
        }
        sincronizarAnclaUsd(producto);
        return productoRepo.save(producto);
    }

    /**
     * Recalcula el ancla en USD de precioVenta/precioCompra contra la última cotización conocida
     * (dolarización). Se llama sin condición cada vez que se guarda un precio en pesos — si sólo
     * cambió otro campo, recalcular es un no-op salvo redondeo de centavos, y de paso autocorrige
     * el ancla si la cotización cambió desde la última vez. Si todavía no hay ninguna cotización
     * cargada (sistema recién migrado), el ancla queda en null hasta que exista una.
     */
    public void sincronizarAnclaUsd(Producto producto) {
        cotizacionService.ultimaConocida().ifPresent(cotizacion -> {
            producto.setPrecioVentaUsd(producto.getPrecioVenta().divide(cotizacion, 2, RoundingMode.HALF_UP));
            if (producto.getPrecioCompra() != null) {
                producto.setPrecioCompraUsd(producto.getPrecioCompra().divide(cotizacion, 2, RoundingMode.HALF_UP));
            }
        });
    }

    /** Baja lógica: no se borra la fila para no romper ventas históricas que la referencian. */
    public void eliminar(Integer id) {
        Producto producto = obtenerPorId(id);
        producto.setActivo(false);
        productoRepo.save(producto);
    }

    public Producto buscarPorCodigo(String codigo) {
        return productoRepo.buscarActivoPorCodigo(codigo)
            .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado para el código: " + codigo));
    }

    public Producto cargarStock(String codigo, int cantidad) {
        Producto producto = buscarPorCodigo(codigo);
        producto.setStockActual(producto.getStockActual() + cantidad);
        return productoRepo.save(producto);
    }
}
