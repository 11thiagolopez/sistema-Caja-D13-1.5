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
import com.thiago.escenasFX.model.DetalleNotaCredito;
import com.thiago.escenasFX.model.DetalleVenta;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.Gasto;
import com.thiago.escenasFX.model.NotaCredito;
import com.thiago.escenasFX.model.Producto;
import com.thiago.escenasFX.model.Venta;
import com.thiago.escenasFX.repository.GastoRepository;
import com.thiago.escenasFX.repository.NotaCreditoRepository;
import com.thiago.escenasFX.repository.VentaRepository;

@Service
public class ReporteService {

    private static final String ESTADO_CONFIRMADA = "CONFIRMADA";

    // Motivos de Nota de Crédito que devuelven stock — igual lista que
    // NotaCreditoService.MOTIVOS_QUE_DEVUELVEN_STOCK (duplicada a propósito: son dos
    // responsabilidades distintas — acá decide si se resta del costo de mercadería del período,
    // allá si se reingresa stock real — no vale la pena una clase de constantes compartida para
    // una lista de 2 strings).
    private static final List<String> MOTIVOS_QUE_DEVUELVEN_STOCK = List.of(
        "DEVOLUCION_MERCADERIA", "ANULACION_TOTAL");

    private final VentaRepository ventaRepo;
    private final GastoRepository gastoRepo;
    private final NotaCreditoRepository notaCreditoRepo;

    public ReporteService(VentaRepository ventaRepo, GastoRepository gastoRepo,
            NotaCreditoRepository notaCreditoRepo) {
        this.ventaRepo = ventaRepo;
        this.gastoRepo = gastoRepo;
        this.notaCreditoRepo = notaCreditoRepo;
    }

    // Los trabajos a domicilio tienen su propio reporte (ReporteTrabajosService). Solo cuentan acá
    // en Comisiones y en el Balance, que es el resultado de todo el negocio.
    private List<Venta> ventasDeMostrador(LocalDate desde, LocalDate hasta) {
        return confirmadas(desde, hasta).stream()
            .filter(v -> !esTrabajo(v))
            .toList();
    }

    private List<Venta> confirmadas(LocalDate desde, LocalDate hasta) {
        return ventaRepo.findByFechaBetweenAndEstado(ZonaNegocio.inicioDelDia(desde), ZonaNegocio.finDelDia(hasta),
            ESTADO_CONFIRMADA);
    }

    private boolean esTrabajo(Venta v) {
        return "DOMICILIO".equals(v.getTipoVenta());
    }

    public List<ProductoRankingDTO> productosGanadores(LocalDate desde, LocalDate hasta, int limit) {
        List<Venta> ventas = ventasDeMostrador(desde, hasta);

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
        List<Venta> ventas = ventasDeMostrador(desde, hasta);

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
     * Comisión: % configurado en Empleado.comision sobre una base que depende del tipo de venta.
     * Mostrador → margen (precio de venta - costo) atribuido a quien registró/cobró la venta. Trabajo
     * a domicilio → solo la mano de obra, al técnico asignado (ver CuentasTrabajo): sus artículos no
     * pagan comisión a nadie, y sin técnico todo queda para el negocio.
     */
    public List<ComisionEmpleadoDTO> comisionesPorVendedor(LocalDate desde, LocalDate hasta) {
        Map<Integer, Empleado> empleadosPorId = new LinkedHashMap<>();
        Map<Integer, BigDecimal> gananciaPorId = new LinkedHashMap<>();
        Map<Integer, BigDecimal> comisionPorId = new LinkedHashMap<>();
        Map<Integer, Set<Integer>> ventasPorId = new LinkedHashMap<>();

        for (Venta v : confirmadas(desde, hasta)) {
            Empleado responsable = esTrabajo(v) ? v.getEmpleadoTecnico() : v.getEmpleado();
            if (responsable == null) {
                continue;
            }
            BigDecimal ganancia;
            BigDecimal comision;
            if (esTrabajo(v)) {
                ganancia = CuentasTrabajo.manoDeObra(v);
                if (ganancia.signum() == 0) {
                    continue;
                }
                comision = CuentasTrabajo.paraTecnico(v);
            } else {
                ganancia = v.getDetalles().stream()
                    .map(this::margenDelDetalle)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal porcentaje = responsable.getComision() != null ? responsable.getComision() : BigDecimal.ZERO;
                comision = ganancia.multiply(porcentaje).divide(BigDecimal.valueOf(100));
            }

            Integer idResponsable = responsable.getIdEmpleado();
            empleadosPorId.putIfAbsent(idResponsable, responsable);
            gananciaPorId.merge(idResponsable, ganancia, BigDecimal::add);
            comisionPorId.merge(idResponsable, comision, BigDecimal::add);
            ventasPorId.computeIfAbsent(idResponsable, k -> new LinkedHashSet<>()).add(v.getIdVenta());
        }

        return gananciaPorId.entrySet().stream()
            .map(entry -> {
                Empleado empleado = empleadosPorId.get(entry.getKey());
                return new ComisionEmpleadoDTO(empleado.getIdEmpleado(), empleado.getNombre(), empleado.getComision(),
                    entry.getValue(), comisionPorId.get(entry.getKey()), ventasPorId.get(entry.getKey()).size());
            })
            .sorted(Comparator.comparing(ComisionEmpleadoDTO::getComisionCalculada).reversed())
            .toList();
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

    /** Costo de mercadería que vuelve a sumar una línea de nota de crédito que devolvió stock. */
    private BigDecimal costoDevueltoDeLinea(DetalleNotaCredito d) {
        if (d.getProducto() == null || d.getProducto().getPrecioCompra() == null) {
            return BigDecimal.ZERO;
        }
        return d.getProducto().getPrecioCompra().multiply(BigDecimal.valueOf(d.getCantidad()));
    }

    public List<Venta> ventasPorVendedor(LocalDate desde, LocalDate hasta, Integer idEmpleado) {
        return ventasDeMostrador(desde, hasta).stream()
            .filter(v -> v.getEmpleado() != null && v.getEmpleado().getIdEmpleado().equals(idEmpleado))
            .toList();
    }

    public List<FormaPagoResumenDTO> ventasPorFormaPago(LocalDate desde, LocalDate hasta) {
        List<Venta> ventas = ventasDeMostrador(desde, hasta);

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
     * efectivo/arqueo, no un gasto del negocio (ver Reportes/Caja para el arqueo). Incluye los trabajos
     * a domicilio: es el resultado de todo el negocio.
     */
    public BalanceFinancieroResponse balanceFinanciero(LocalDate desde, LocalDate hasta) {
        List<Venta> ventas = confirmadas(desde, hasta);

        BigDecimal ingresosPorVentas = ventas.stream()
            .map(Venta::getTotalVenta)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal ingresosPorTrabajos = ventas.stream()
            .filter(this::esTrabajo)
            .map(Venta::getTotalVenta)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal costoMercaderia = ventas.stream()
            .flatMap(v -> v.getDetalles().stream())
            .map(d -> costoUnitarioDelDetalle(d).multiply(BigDecimal.valueOf(d.getCantidad())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Las notas de crédito del período restan de los ingresos (plata que se le devolvió al
        // cliente, fiscal o interna — las dos son revenue real dado de baja) y, si el motivo
        // devolvió stock, también del costo de mercadería (esa mercadería ya no se vendió de
        // verdad en este período, volvió al depósito). Nunca se tocan Venta/DetalleVenta: la nota
        // de crédito es un documento aparte, ver clase NotaCredito.
        List<NotaCredito> notasCredito = notaCreditoRepo.findByFechaBetweenOrderByFechaDesc(
            ZonaNegocio.inicioDelDia(desde), ZonaNegocio.finDelDia(hasta));

        BigDecimal totalNotasCredito = notasCredito.stream()
            .map(NotaCredito::getMontoTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal costoDevueltoPorNotasCredito = notasCredito.stream()
            .filter(nc -> MOTIVOS_QUE_DEVUELVEN_STOCK.contains(nc.getMotivo()))
            .flatMap(nc -> nc.getDetalles().stream())
            .map(this::costoDevueltoDeLinea)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        ingresosPorVentas = ingresosPorVentas.subtract(totalNotasCredito);
        costoMercaderia = costoMercaderia.subtract(costoDevueltoPorNotasCredito);

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
            comisionesPagadas, gananciaNeta, ingresosPorTrabajos);
    }
}
