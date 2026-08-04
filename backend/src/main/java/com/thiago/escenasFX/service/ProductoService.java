package com.thiago.escenasFX.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.thiago.escenasFX.dto.ProductoRequest;
import com.thiago.escenasFX.model.Marca;
import com.thiago.escenasFX.model.Producto;
import com.thiago.escenasFX.repository.ProductoRepository;

@Service
public class ProductoService {

    private final ProductoRepository productoRepo;
    private final MarcaService marcaService;
    private final ProveedorService proveedorService;

    public ProductoService(ProductoRepository productoRepo, MarcaService marcaService,
            ProveedorService proveedorService) {
        this.productoRepo = productoRepo;
        this.marcaService = marcaService;
        this.proveedorService = proveedorService;
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
            .findByRubroAndFamiliaAndMarcaOrderByCorrelativoDesc(req.getRubro(), req.getFamilia(), marca.getCodigo());

        int correlativoAnterior = existentes.isEmpty() ? 0 : Integer.parseInt(existentes.get(0).getCorrelativo());
        String correlativo = String.format("%04d", correlativoAnterior + 1);
        String codigoInterno = req.getRubro() + req.getFamilia() + marca.getCodigo() + correlativo;

        Producto producto = new Producto();
        producto.setRubro(req.getRubro());
        producto.setFamilia(req.getFamilia());
        producto.setMarca(marca.getCodigo());
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

        return productoRepo.save(producto);
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
