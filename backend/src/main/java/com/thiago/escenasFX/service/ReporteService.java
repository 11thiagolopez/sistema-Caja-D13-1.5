package com.thiago.escenasFX.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.thiago.escenasFX.dto.BalanceFinancieroResponse;
import com.thiago.escenasFX.dto.ComisionEmpleadoDTO;
import com.thiago.escenasFX.dto.ProductoRankingDTO;
import com.thiago.escenasFX.model.DetalleVenta;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.Gasto;
import com.thiago.escenasFX.model.Producto;
import com.thiago.escenasFX.model.Venta;
import com.thiago.escenasFX.repository.GastoRepository;
import com.thiago.escenasFX.repository.VentaRepository;

@Service
public class ReporteService {

    private static final String ESTADO_CONFIRMADA = "CONFIRMADA";

    private final VentaRepository ventaRepo;
    private final GastoRepository gastoRepo;

    public ReporteService(VentaRepository ventaRepo, GastoRepository gastoRepo) {
        this.ventaRepo = ventaRepo;
        this.gastoRepo = gastoRepo;
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
     * Comisión por vendedor: % configurado en Empleado.comision sobre la GANANCIA (precio de
     * venta - costo) de lo que vendió en el rango, no sobre el total facturado. Se agrupan las
     * ventas CONFIRMADA del rango por empleado.
     */
    public List<ComisionEmpleadoDTO> comisionesPorVendedor(LocalDate desde, LocalDate hasta) {
        List<Venta> ventas = ventaRepo.findByFechaBetweenAndEstado(desde.atStartOfDay(), hasta.atTime(23, 59, 59),
            ESTADO_CONFIRMADA);

        return ventas.stream()
            .filter(v -> v.getEmpleado() != null)
            .collect(Collectors.groupingBy(v -> v.getEmpleado().getIdEmpleado()))
            .values().stream()
            .map(this::aComisionEmpleado)
            .sorted(Comparator.comparing(ComisionEmpleadoDTO::getComisionCalculada).reversed())
            .toList();
    }

    private ComisionEmpleadoDTO aComisionEmpleado(List<Venta> ventasDeUnEmpleado) {
        Empleado empleado = ventasDeUnEmpleado.get(0).getEmpleado();

        BigDecimal ganancia = ventasDeUnEmpleado.stream()
            .flatMap(v -> v.getDetalles().stream())
            .map(this::margenDelDetalle)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal porcentaje = empleado.getComision() != null ? empleado.getComision() : BigDecimal.ZERO;
        BigDecimal comisionCalculada = ganancia.multiply(porcentaje).divide(BigDecimal.valueOf(100));

        return new ComisionEmpleadoDTO(empleado.getIdEmpleado(), empleado.getNombre(), empleado.getComision(),
            ganancia, comisionCalculada, ventasDeUnEmpleado.size());
    }

    private BigDecimal margenDelDetalle(DetalleVenta d) {
        BigDecimal precioCompra = d.getProducto().getPrecioCompra();
        if (precioCompra == null) {
            precioCompra = BigDecimal.ZERO;
        }
        BigDecimal margenUnitario = d.getPrecioUnitario().subtract(precioCompra);
        return margenUnitario.multiply(BigDecimal.valueOf(d.getCantidad()));
    }

    public List<Venta> ventasPorVendedor(LocalDate desde, LocalDate hasta, Integer idEmpleado) {
        List<Venta> ventas = ventaRepo.findByFechaBetweenAndEstado(desde.atStartOfDay(), hasta.atTime(23, 59, 59),
            ESTADO_CONFIRMADA);
        return ventas.stream()
            .filter(v -> v.getEmpleado() != null && v.getEmpleado().getIdEmpleado().equals(idEmpleado))
            .toList();
    }

    /**
     * Asiento contable simplificado: Ganancia Neta = Ingresos por Ventas - Costo de Mercadería
     * - Gastos Operativos (tabla Gasto: alquiler, luz, etc.) - Comisiones pagadas a vendedores.
     * Los retiros de caja (MovimientoCaja) quedan fuera de este cálculo: son movimiento de
     * efectivo/arqueo, no un gasto del negocio (ver Reportes/Caja para el arqueo).
     */
    public BalanceFinancieroResponse balanceFinanciero(LocalDate desde, LocalDate hasta) {
        List<Venta> ventas = ventaRepo.findByFechaBetweenAndEstado(desde.atStartOfDay(), hasta.atTime(23, 59, 59),
            ESTADO_CONFIRMADA);

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

        List<Gasto> gastos = gastoRepo.findByFechaBetweenOrderByFechaDesc(desde, hasta);
        BigDecimal gastosOperativos = gastos.stream()
            .map(Gasto::getImporte)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal comisionesPagadas = comisionesPorVendedor(desde, hasta).stream()
            .map(ComisionEmpleadoDTO::getComisionCalculada)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal gananciaNeta = ingresosPorVentas
            .subtract(costoMercaderia)
            .subtract(gastosOperativos)
            .subtract(comisionesPagadas);

        return new BalanceFinancieroResponse(desde, hasta, ingresosPorVentas, costoMercaderia, gastosOperativos,
            comisionesPagadas, gananciaNeta);
    }
}
