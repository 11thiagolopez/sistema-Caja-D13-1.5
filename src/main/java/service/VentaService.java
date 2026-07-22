package service;

import model.DetalleVenta;
import model.Producto;
import model.Venta;
import repository.ProductoRepository;
import repository.VentaRepository;

public class VentaService {
	 @Autowired private VentaRepository ventaRepo;
	    @Autowired private ProductoRepository productoRepo;

	    @Transactional
	    public Venta registrarVenta(Venta venta) {
	        for (DetalleVenta d : venta.getDetalles()) {
	            Producto p = productoRepo.findById(d.getProducto().getIdProducto())
	                .orElseThrow(() -> new IllegalArgumentException("Producto no existe"));

	            if (p.getStockActual() < d.getCantidad()) {
	                throw new IllegalStateException("Stock insuficiente para " + p.getDescripcion());
	            }

	            p.setStockActual(p.getStockActual() - d.getCantidad());
	            productoRepo.save(p);

	            d.setVenta(venta);
	            d.setSubtotal(d.getPrecioUnitario() * d.getCantidad());
	        }
	        return ventaRepo.save(venta);
	    }
}
