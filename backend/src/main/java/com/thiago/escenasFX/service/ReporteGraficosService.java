package com.thiago.escenasFX.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thiago.escenasFX.dto.PuntoGraficoDTO;
import com.thiago.escenasFX.dto.RankingItemDTO;
import com.thiago.escenasFX.dto.RankingMarcasResponse;
import com.thiago.escenasFX.dto.ResumenVentasDTO;
import com.thiago.escenasFX.dto.VentasPorDiaResponse;
import com.thiago.escenasFX.repository.ReporteGraficosRepository;

/**
 * Datos de los gráficos de Reportes. Solo cuenta ventas de mostrador CONFIRMADAS. A propósito no descuenta notas
 * de crédito (son ventas brutas del período, a diferencia de ReporteService.balanceFinanciero).
 *
 * Zona horaria: Venta.fecha se guarda con LocalDateTime.now(), es decir en la zona de la JVM, que
 * puede no ser la del local (Railway corre en UTC salvo que se configure TZ). Por eso el rango y el
 * día de cada venta se convierten desde esa zona a la de Argentina, y da bien en ambos casos.
 */
@Service
@Transactional(readOnly = true)
public class ReporteGraficosService {

    private static final ZoneId ZONA_NEGOCIO = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final String ORDEN_MONTO = "monto";
    private static final String ORDEN_UNIDADES = "unidades";
    private static final String SIN_MARCA = "Sin marca";
    private static final String MEDIO_SALDO = "SALDO_CUENTA_CORRIENTE";
    private static final int TAMANIO_TOP = 10;
    private static final int TAMANIO_PEORES = 5;

    private static final Map<String, String> ETIQUETAS_MEDIO_PAGO = Map.of(
        "EFECTIVO", "Efectivo",
        "TRANSFERENCIA", "Transferencia",
        "TARJETA", "Tarjeta",
        MEDIO_SALDO, "Saldo cuenta corriente");

    private static final Map<DayOfWeek, String> NOMBRES_DIA = Map.of(
        DayOfWeek.MONDAY, "Lunes",
        DayOfWeek.TUESDAY, "Martes",
        DayOfWeek.WEDNESDAY, "Miércoles",
        DayOfWeek.THURSDAY, "Jueves",
        DayOfWeek.FRIDAY, "Viernes",
        DayOfWeek.SATURDAY, "Sábado",
        DayOfWeek.SUNDAY, "Domingo");

    private final ReporteGraficosRepository repo;

    public ReporteGraficosService(ReporteGraficosRepository repo) {
        this.repo = repo;
    }

    public ResumenVentasDTO resumen(LocalDate desde, LocalDate hasta) {
        LocalDateTime inicio = inicioDe(desde, hasta);
        LocalDateTime fin = finDe(hasta);

        Object[] totales = repo.totalesDeVentas(inicio, fin).get(0);
        BigDecimal totalVendido = aMonto(totales[0]);
        long cantidadVentas = ((Number) totales[1]).longValue();
        BigDecimal ticketPromedio = cantidadVentas == 0
            ? BigDecimal.ZERO.setScale(2)
            : totalVendido.divide(BigDecimal.valueOf(cantidadVentas), 2, RoundingMode.HALF_UP);

        return new ResumenVentasDTO(totalVendido, cantidadVentas, ticketPromedio,
            repo.unidadesVendidas(inicio, fin).longValue());
    }

    public RankingMarcasResponse marcas(LocalDate desde, LocalDate hasta, String orden) {
        validarOrden(orden);

        // Se fusiona en Java: una marca null y una en blanco caen en grupos distintos en SQL.
        Map<String, BigDecimal> montos = new LinkedHashMap<>();
        Map<String, Long> unidades = new LinkedHashMap<>();
        for (Object[] fila : repo.totalesPorMarca(inicioDe(desde, hasta), finDe(hasta))) {
            String marca = fila[0] == null || ((String) fila[0]).isBlank() ? SIN_MARCA : (String) fila[0];
            montos.merge(marca, aMonto(fila[1]), BigDecimal::add);
            unidades.merge(marca, ((Number) fila[2]).longValue(), Long::sum);
        }

        Comparator<RankingItemDTO> porValor = Comparator.comparing(RankingItemDTO::getValor);
        List<RankingItemDTO> items = montos.keySet().stream()
            .map(marca -> aItem(marca, montos.get(marca), unidades.get(marca), orden))
            .toList();

        List<RankingItemDTO> mejores = items.stream().sorted(porValor.reversed()).limit(TAMANIO_TOP).toList();
        // "Sin marca" no es una marca que se pueda dejar de comprar: no aporta en esta lista.
        List<RankingItemDTO> peores = items.stream()
            .filter(item -> !SIN_MARCA.equals(item.getEtiqueta()))
            .sorted(porValor)
            .limit(TAMANIO_PEORES)
            .toList();
        return new RankingMarcasResponse(mejores, peores);
    }

    public List<RankingItemDTO> productos(LocalDate desde, LocalDate hasta, String orden) {
        validarOrden(orden);
        LocalDateTime inicio = inicioDe(desde, hasta);
        LocalDateTime fin = finDe(hasta);
        PageRequest top = PageRequest.of(0, TAMANIO_TOP);

        List<Object[]> filas = ORDEN_MONTO.equals(orden)
            ? repo.productosPorMonto(inicio, fin, top)
            : repo.productosPorUnidades(inicio, fin, top);

        return filas.stream()
            .map(fila -> aItem((String) fila[0], aMonto(fila[1]), ((Number) fila[2]).longValue(), orden))
            .toList();
    }

    public VentasPorDiaResponse ventasPorDia(LocalDate desde, LocalDate hasta) {
        Map<LocalDate, BigDecimal> totales = totalesPorDiaConCeros(desde, hasta);

        List<PuntoGraficoDTO> puntos = totales.entrySet().stream()
            .map(entry -> new PuntoGraficoDTO(entry.getKey().toString(), entry.getValue()))
            .toList();

        // El peor día se busca solo entre los días con ventas: si no, un local cerrado los domingos
        // tendría siempre al domingo como peor día. Ante un empate gana el primero del rango.
        List<PuntoGraficoDTO> conVentas = puntos.stream()
            .filter(punto -> punto.getValor().signum() > 0)
            .toList();
        PuntoGraficoDTO mejor = conVentas.stream().reduce(null,
            (a, b) -> a == null || b.getValor().compareTo(a.getValor()) > 0 ? b : a);
        PuntoGraficoDTO peor = conVentas.stream().reduce(null,
            (a, b) -> a == null || b.getValor().compareTo(a.getValor()) < 0 ? b : a);

        return new VentasPorDiaResponse(puntos, mejor, peor);
    }

    /**
     * Promedio por día de la semana: lo vendido esos días dividido la cantidad de veces que ese día
     * aparece en el rango, tenga ventas o no (un lunes sin ventas baja el promedio de los lunes).
     */
    public List<PuntoGraficoDTO> ventasPorDiaSemana(LocalDate desde, LocalDate hasta) {
        Map<DayOfWeek, BigDecimal> sumas = new LinkedHashMap<>();
        Map<DayOfWeek, Integer> apariciones = new LinkedHashMap<>();
        totalesPorDiaConCeros(desde, hasta).forEach((dia, total) -> {
            sumas.merge(dia.getDayOfWeek(), total, BigDecimal::add);
            apariciones.merge(dia.getDayOfWeek(), 1, Integer::sum);
        });

        List<PuntoGraficoDTO> puntos = new ArrayList<>();
        for (DayOfWeek dia : DayOfWeek.values()) {
            BigDecimal promedio = apariciones.containsKey(dia)
                ? sumas.get(dia).divide(BigDecimal.valueOf(apariciones.get(dia)), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2);
            puntos.add(new PuntoGraficoDTO(NOMBRES_DIA.get(dia), promedio));
        }
        return puntos;
    }

    /**
     * Lo pagado con saldo a favor va a "Saldo cuenta corriente" y solo el resto a su medio de pago,
     * así un pago parcial con saldo no infla el efectivo o la transferencia de la venta.
     */
    public List<PuntoGraficoDTO> mediosPago(LocalDate desde, LocalDate hasta) {
        Map<String, BigDecimal> montos = new LinkedHashMap<>();
        for (Object[] fila : repo.totalesPorMedioPago(inicioDe(desde, hasta), finDe(hasta))) {
            String medio = fila[0] == null || ((String) fila[0]).isBlank() ? "SIN_ESPECIFICAR" : (String) fila[0];
            BigDecimal saldo = aMonto(fila[2]);
            montos.merge(medio, aMonto(fila[1]).subtract(saldo), BigDecimal::add);
            montos.merge(MEDIO_SALDO, saldo, BigDecimal::add);
        }

        return montos.entrySet().stream()
            .filter(entry -> entry.getValue().signum() > 0)
            .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
            .map(entry -> new PuntoGraficoDTO(etiquetaMedioPago(entry.getKey()), entry.getValue()))
            .toList();
    }

    private String etiquetaMedioPago(String medio) {
        if ("SIN_ESPECIFICAR".equals(medio)) {
            return "Sin especificar";
        }
        return ETIQUETAS_MEDIO_PAGO.getOrDefault(medio, medio);
    }

    private Map<LocalDate, BigDecimal> totalesPorDiaConCeros(LocalDate desde, LocalDate hasta) {
        Map<LocalDate, BigDecimal> totales = new LinkedHashMap<>();
        for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
            totales.put(dia, BigDecimal.ZERO.setScale(2));
        }

        List<Object[]> filas = repo.totalesPorDia(TimeZone.getDefault().getID(), ZONA_NEGOCIO.getId(),
            inicioDe(desde, hasta), finDe(hasta));
        for (Object[] fila : filas) {
            // El driver puede devolver java.sql.Date o LocalDate; el texto es igual en ambos.
            LocalDate dia = LocalDate.parse(fila[0].toString().substring(0, 10));
            totales.computeIfPresent(dia, (d, cero) -> aMonto(fila[1]));
        }
        return totales;
    }

    private LocalDateTime inicioDe(LocalDate desde, LocalDate hasta) {
        if (desde.isAfter(hasta)) {
            throw new IllegalArgumentException("La fecha desde no puede ser posterior a la fecha hasta");
        }
        return aZonaGuardada(desde);
    }

    private LocalDateTime finDe(LocalDate hasta) {
        return aZonaGuardada(hasta.plusDays(1));
    }

    /** Medianoche de ese día en Argentina, expresada en la zona en la que está guardada la fecha. */
    private LocalDateTime aZonaGuardada(LocalDate dia) {
        return dia.atStartOfDay(ZONA_NEGOCIO).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    private void validarOrden(String orden) {
        if (!ORDEN_MONTO.equals(orden) && !ORDEN_UNIDADES.equals(orden)) {
            throw new IllegalArgumentException("El orden debe ser monto o unidades");
        }
    }

    private RankingItemDTO aItem(String etiqueta, BigDecimal monto, long unidades, String orden) {
        BigDecimal valor = ORDEN_MONTO.equals(orden) ? monto : BigDecimal.valueOf(unidades);
        return new RankingItemDTO(etiqueta, valor, monto, unidades);
    }

    private BigDecimal aMonto(Object numero) {
        return new BigDecimal(numero.toString()).setScale(2, RoundingMode.HALF_UP);
    }
}
