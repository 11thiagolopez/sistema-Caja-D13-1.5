package com.thiago.escenasFX.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.thiago.escenasFX.dto.BalanceFinancieroResponse;
import com.thiago.escenasFX.dto.ProductoRankingDTO;
import com.thiago.escenasFX.model.DetalleVenta;
import com.thiago.escenasFX.model.MovimientoCaja;
import com.thiago.escenasFX.model.Producto;
import com.thiago.escenasFX.model.Venta;
import com.thiago.escenasFX.repository.MovimientoCajaRepository;
import com.thiago.escenasFX.repository.VentaRepository;

@Service
public class ReporteService {

    private static final String ESTADO_CONFIRMADA = "CONFIRMADA";

    private final VentaRepository ventaRepo;
    private final MovimientoCajaRepository movRepo;

    public ReporteService(VentaRepository ventaRepo, MovimientoCajaRepository movRepo) {
        this.ventaRepo = ventaRepo;
        this.movRepo = movRepo;
    }

    public List<ProductoRankingDTO> productosGanadores(LocalDate desde, LocalDate hasta, int limit) {
        List<Venta> ventas = ventaRepo.findByFechaBetweenAndEstado(desde.atStartOfDay(), hasta.atTime(23, 59, 59),
            ESTADO_CONFIRMADA);

        List<DetalleVenta> detalles = ventas.stream()
            .flatMap(v -> v.getDetalles().stream())
            .toList();

        return detalles.stream()
            .collect(Collectors.groupingBy(d -> d.getProducto().getIdProducto()))
            .values().stream()
            .map(this::aRanking)
            .sorted(Comparator.comparingLong(ProductoRankingDTO::getCantidadVendida).reversed())
            .limit(limit)
            .toList();
    }

    private ProductoRankingDTO aRanking(List<DetalleVenta> detallesDeUnProducto) {
        Producto producto = detallesDeUnProducto.get(0).getProducto();
        long cantidad = detallesDeUnProducto.stream().mapToLong(DetalleVenta::getCantidad).sum();
        BigDecimal total = detallesDeUnProducto.stream()
            .map(DetalleVenta::getSubtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ProductoRankingDTO(producto.getIdProducto(), producto.getDescripcion(), cantidad, total);
    }

    /**
     * Asiento contable simplificado: Ganancia Neta = Ingresos por Ventas - Costo de Mercadería
     * - Gastos Operativos. Los "gastos operativos" son todos los MovimientoCaja tipo RETIRO:
     * como ahora solo se crean al confirmar el OTP (ver CajaService.confirmarRetiro), su sola
     * existencia ya implica que están aprobados.
     */
    public BalanceFinancieroResponse balanceFinanciero(LocalDate desde, LocalDate hasta) {
        var desdeDateTime = desde.atStartOfDay();
        var hastaDateTime = hasta.atTime(23, 59, 59);

        List<Venta> ventas = ventaRepo.findByFechaBetweenAndEstado(desdeDateTime, hastaDateTime, ESTADO_CONFIRMADA);

        BigDecimal ingresosPorVentas = ventas.stream()
            .map(Venta::getTotalVenta)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal costoMercaderia = ventas.stream()
            .flatMap(v -> v.getDetalles().stream())
            .map(d -> {
                BigDecimal precioCompra = d.getProducto().getPrecioCompra();
                if (precioCompra == null) {
                    precioCompra = BigDecimal.ZERO;
                }
                return precioCompra.multiply(BigDecimal.valueOf(d.getCantidad()));
            })
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<MovimientoCaja> retiros = movRepo.findByFechaBetween(desdeDateTime, hastaDateTime);
        BigDecimal gastosOperativos = retiros.stream()
            .map(MovimientoCaja::getMonto)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal gananciaNeta = ingresosPorVentas.subtract(costoMercaderia).subtract(gastosOperativos);

        return new BalanceFinancieroResponse(desde, hasta, ingresosPorVentas, costoMercaderia, gastosOperativos,
            gananciaNeta);
    }
}
