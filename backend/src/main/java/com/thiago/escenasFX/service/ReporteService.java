package com.thiago.escenasFX.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.thiago.escenasFX.dto.BalanceFinancieroResponse;
import com.thiago.escenasFX.dto.ComisionEmpleadoDTO;
import com.thiago.escenasFX.dto.FormaPagoResumenDTO;
import com.thiago.escenasFX.dto.MarcaRankingDTO;
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

        // Los ítems manuales (trabajos sin producto, ej. "Apertura de cerradura") no rankean acá:
        // no hay un producto del catálogo al que atribuirles la venta.
        List<DetalleVenta> detalles = ventas.stream()
            .flatMap(v -> v.getDetalles().stream())
            .filter(d -> d.getProducto() != null)
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

    public List<MarcaRankingDTO> ventasPorMarca(LocalDate desde, LocalDate hasta) {
        List<Venta> ventas = ventaRepo.findByFechaBetweenAndEstado(desde.atStartOfDay(), hasta.atTime(23, 59, 59),
            ESTADO_CONFIRMADA);

        // Los ítems manuales no tienen marca — se excluyen del ranking (ver productosGanadores).
        List<DetalleVenta> detalles = ventas.stream()
            .flatMap(v -> v.getDetalles().stream())
            .filter(d -> d.getProducto() != null)
            .toList();

        return detalles.stream()
            .collect(Collectors.groupingBy(d -> marcaONombreDefault(d.getProducto())))
            .entrySet().stream()
            .map(entry -> aMarcaRanking(entry.getKey(), entry.getValue()))
            .sorted(Comparator.comparingLong(MarcaRankingDTO::getCantidadVendida).reversed())
            .toList();
    }

    private String marcaONombreDefault(Producto producto) {
        String marca = producto.getMarca();
        return (marca == null || marca.isBlank()) ? "Sin marca" : marca;
    }

    private MarcaRankingDTO aMarcaRanking(String marca, List<DetalleVenta> detallesDeUnaMarca) {
        long cantidad = detallesDeUnaMarca.stream().mapToLong(DetalleVenta::getCantidad).sum();
        BigDecimal total = detallesDeUnaMarca.stream()
            .map(DetalleVenta::getSubtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new MarcaRankingDTO(marca, cantidad, total);
    }

    /**
     * Comisión: % configurado en Empleado.comision sobre una base que depende del tipo de línea.
     * Artículo/copia (venta de mostrador o dentro de un trabajo a domicilio) → margen (precio de
     * venta - costo) atribuido a quien registró/cobró la venta, como siempre. Mano de obra
     * ("SERVICIO") de un trabajo a domicilio → el monto BRUTO de esa línea, sin restar costo,
     * atribuido al técnico asignado — nunca a quien cobró ni mezclado con productos.
     */
    public List<ComisionEmpleadoDTO> comisionesPorVendedor(LocalDate desde, LocalDate hasta) {
        List<Venta> ventas = ventaRepo.findByFechaBetweenAndEstado(desde.atStartOfDay(), hasta.atTime(23, 59, 59),
            ESTADO_CONFIRMADA);

        Map<Integer, Empleado> empleadosPorId = new LinkedHashMap<>();
        Map<Integer, BigDecimal> gananciaPorId = new LinkedHashMap<>();
        Map<Integer, Set<Integer>> ventasPorId = new LinkedHashMap<>();

        for (Venta v : ventas) {
            for (DetalleVenta d : v.getDetalles()) {
                Empleado responsable;
                BigDecimal monto;
                if ("SERVICIO".equals(d.getTipo()) && v.getEmpleadoTecnico() != null) {
                    responsable = v.getEmpleadoTecnico();
                    monto = d.getPrecioUnitario().multiply(BigDecimal.valueOf(d.getCantidad()));
                } else if (v.getEmpleado() != null) {
                    responsable = v.getEmpleado();
                    monto = margenDelDetalle(d);
                } else {
                    continue;
                }

                Integer idResponsable = responsable.getIdEmpleado();
                empleadosPorId.putIfAbsent(idResponsable, responsable);
                gananciaPorId.merge(idResponsable, monto, BigDecimal::add);
                ventasPorId.computeIfAbsent(idResponsable, k -> new LinkedHashSet<>()).add(v.getIdVenta());
            }
        }

        return gananciaPorId.entrySet().stream()
            .map(entry -> aComisionEmpleado(empleadosPorId.get(entry.getKey()), entry.getValue(),
                ventasPorId.get(entry.getKey()).size()))
            .sorted(Comparator.comparing(ComisionEmpleadoDTO::getComisionCalculada).reversed())
            .toList();
    }

    private ComisionEmpleadoDTO aComisionEmpleado(Empleado empleado, BigDecimal ganancia, int cantidadVentas) {
        BigDecimal porcentaje = empleado.getComision() != null ? empleado.getComision() : BigDecimal.ZERO;
        BigDecimal comisionCalculada = ganancia.multiply(porcentaje).divide(BigDecimal.valueOf(100));

        return new ComisionEmpleadoDTO(empleado.getIdEmpleado(), empleado.getNombre(), empleado.getComision(),
            ganancia, comisionCalculada, cantidadVentas);
    }

    private BigDecimal margenDelDetalle(DetalleVenta d) {
        BigDecimal margenUnitario = d.getPrecioUnitario().subtract(costoUnitarioDelDetalle(d));
        return margenUnitario.multiply(BigDecimal.valueOf(d.getCantidad()));
    }

    /**
     * Costo de mercadería de una línea: el precioCompra del producto si la línea viene del
     * catálogo, o cero si es un ítem manual (trabajo de mano de obra, sin costo de mercadería
     * registrado — ej. "Apertura de cerradura").
     */
    private BigDecimal costoUnitarioDelDetalle(DetalleVenta d) {
        if (d.getProducto() == null || d.getProducto().getPrecioCompra() == null) {
            return BigDecimal.ZERO;
        }
        return d.getProducto().getPrecioCompra();
    }

    public List<Venta> ventasPorVendedor(LocalDate desde, LocalDate hasta, Integer idEmpleado) {
        List<Venta> ventas = ventaRepo.findByFechaBetweenAndEstado(desde.atStartOfDay(), hasta.atTime(23, 59, 59),
            ESTADO_CONFIRMADA);
        return ventas.stream()
            .filter(v -> v.getEmpleado() != null && v.getEmpleado().getIdEmpleado().equals(idEmpleado))
            .toList();
    }

    public List<FormaPagoResumenDTO> ventasPorFormaPago(LocalDate desde, LocalDate hasta) {
        List<Venta> ventas = ventaRepo.findByFechaBetweenAndEstado(desde.atStartOfDay(), hasta.atTime(23, 59, 59),
            ESTADO_CONFIRMADA);

        return ventas.stream()
            .collect(Collectors.groupingBy(v -> v.getMedioPago() == null ? "Sin especificar" : v.getMedioPago()))
            .entrySet().stream()
            .map(entry -> {
                BigDecimal total = entry.getValue().stream()
                    .map(Venta::getTotalVenta)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                return new FormaPagoResumenDTO(entry.getKey(), entry.getValue().size(), total);
            })
            .sorted(Comparator.comparing(FormaPagoResumenDTO::getTotalFacturado).reversed())
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
            .map(d -> costoUnitarioDelDetalle(d).multiply(BigDecimal.valueOf(d.getCantidad())))
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
