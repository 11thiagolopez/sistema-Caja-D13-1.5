package com.thiago.escenasFX.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thiago.escenasFX.dto.PuntoGraficoDTO;
import com.thiago.escenasFX.dto.ReporteTrabajosResponse;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.HistorialEstadoTrabajo;
import com.thiago.escenasFX.model.Venta;
import com.thiago.escenasFX.repository.HistorialEstadoTrabajoRepository;
import com.thiago.escenasFX.repository.VentaRepository;

/**
 * Reporte de trabajos a domicilio, separado del de mostrador. Cuenta los CONFIRMADOS por el día en
 * que se cobraron. Como los gráficos de Reportes, no descuenta notas de crédito.
 *
 * <p>Etapa 3 del documento del dueño: además de lo de siempre, cantidad e ingresos por mes, tiempo
 * promedio por trabajo (general y por técnico, desde el historial de estados) y el resumen mensual
 * de comisiones por técnico para liquidarlas.
 */
@Service
@Transactional(readOnly = true)
public class ReporteTrabajosService {

    private static final Map<String, String> ETIQUETAS_MEDIO_PAGO = Map.of(
        "EFECTIVO", "Efectivo",
        "TRANSFERENCIA", "Transferencia",
        "TARJETA", "Tarjeta");
    private static final String SIN_MEDIO = "Sin especificar";

    private final VentaRepository ventaRepo;
    private final HistorialEstadoTrabajoRepository historialRepo;

    public ReporteTrabajosService(VentaRepository ventaRepo, HistorialEstadoTrabajoRepository historialRepo) {
        this.ventaRepo = ventaRepo;
        this.historialRepo = historialRepo;
    }

    public ReporteTrabajosResponse reporte(LocalDate desde, LocalDate hasta) {
        if (desde.isAfter(hasta)) {
            throw new IllegalArgumentException("La fecha desde no puede ser posterior a la fecha hasta");
        }

        List<Venta> trabajos = ventaRepo.findByTipoVentaAndEstadoAndFechaBetweenOrderByFechaDesc("DOMICILIO", "CONFIRMADA",
            ZonaNegocio.inicioDelDia(desde), ZonaNegocio.finDelDia(hasta));
        Map<Integer, Long> minutosPorTrabajo = minutosDeTrabajo(trabajos);

        BigDecimal total = BigDecimal.ZERO;
        BigDecimal manoDeObra = BigDecimal.ZERO;
        BigDecimal paraTecnicos = BigDecimal.ZERO;
        BigDecimal costoArticulos = BigDecimal.ZERO;

        Map<LocalDate, BigDecimal> porDia = new LinkedHashMap<>();
        for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
            porDia.put(dia, BigDecimal.ZERO);
        }
        Map<YearMonth, ReporteTrabajosResponse.PorMes> porMes = new LinkedHashMap<>();
        for (YearMonth mes = YearMonth.from(desde); !mes.isAfter(YearMonth.from(hasta)); mes = mes.plusMonths(1)) {
            porMes.put(mes, new ReporteTrabajosResponse.PorMes(mes.toString(), 0, BigDecimal.ZERO, BigDecimal.ZERO));
        }
        Map<String, BigDecimal> porMedio = new LinkedHashMap<>();
        Map<Integer, AcumuladoTecnico> porTecnico = new LinkedHashMap<>();
        Map<String, ReporteTrabajosResponse.ComisionMes> comisiones = new LinkedHashMap<>();
        List<ReporteTrabajosResponse.Trabajo> detalle = new ArrayList<>();
        long minutosTotales = 0;
        int conTiempo = 0;

        for (Venta trabajo : trabajos) {
            BigDecimal totalTrabajo = trabajo.getTotalVenta();
            BigDecimal manoDeObraTrabajo = CuentasTrabajo.manoDeObra(trabajo);
            BigDecimal paraTecnico = CuentasTrabajo.paraTecnico(trabajo);
            Long minutos = minutosPorTrabajo.get(trabajo.getIdVenta());
            LocalDate diaCobro = ZonaNegocio.diaDe(trabajo.getFecha());
            YearMonth mes = YearMonth.from(diaCobro);
            Empleado tecnico = trabajo.getEmpleadoTecnico();

            total = total.add(totalTrabajo);
            manoDeObra = manoDeObra.add(manoDeObraTrabajo);
            paraTecnicos = paraTecnicos.add(paraTecnico);
            costoArticulos = costoArticulos.add(CuentasTrabajo.costoArticulos(trabajo));
            if (minutos != null) {
                minutosTotales += minutos;
                conTiempo++;
            }

            porDia.merge(diaCobro, totalTrabajo, BigDecimal::add);
            porMes.merge(mes, new ReporteTrabajosResponse.PorMes(mes.toString(), 1, totalTrabajo, paraTecnico),
                (a, b) -> new ReporteTrabajosResponse.PorMes(a.mes(), a.cantidadTrabajos() + 1, a.total().add(b.total()),
                    a.paraTecnicos().add(b.paraTecnicos())));
            String medio = ETIQUETAS_MEDIO_PAGO.getOrDefault(String.valueOf(trabajo.getMedioPago()), SIN_MEDIO);
            porMedio.merge(medio, totalTrabajo, BigDecimal::add);
            porTecnico.computeIfAbsent(tecnico != null ? tecnico.getIdEmpleado() : null,
                id -> new AcumuladoTecnico(id, tecnico != null ? tecnico.getNombre() : "Sin técnico"))
                .sumar(manoDeObraTrabajo, paraTecnico, minutos);
            if (tecnico != null) {
                comisiones.merge(mes + "|" + tecnico.getIdEmpleado(),
                    new ReporteTrabajosResponse.ComisionMes(mes.toString(), tecnico.getIdEmpleado(), tecnico.getNombre(), 1,
                        manoDeObraTrabajo, paraTecnico),
                    (a, b) -> new ReporteTrabajosResponse.ComisionMes(a.mes(), a.idEmpleado(), a.nombre(),
                        a.cantidadTrabajos() + 1, a.manoDeObra().add(b.manoDeObra()), a.paraTecnico().add(b.paraTecnico())));
            }

            detalle.add(new ReporteTrabajosResponse.Trabajo(trabajo.getIdVenta(), trabajo.getFecha(),
                trabajo.getDireccionTrabajo(), trabajo.getClienteNombre(),
                tecnico != null ? tecnico.getNombre() : null, medio,
                totalTrabajo, manoDeObraTrabajo, totalTrabajo.subtract(manoDeObraTrabajo), paraTecnico,
                totalTrabajo.subtract(paraTecnico), minutos));
        }

        BigDecimal paraNegocio = total.subtract(paraTecnicos);

        return new ReporteTrabajosResponse(trabajos.size(), total, manoDeObra, total.subtract(manoDeObra), paraTecnicos,
            paraNegocio, costoArticulos, paraNegocio.subtract(costoArticulos),
            conTiempo > 0 ? Math.round((double) minutosTotales / conTiempo) : null, conTiempo,
            porTecnico.values().stream()
                .map(AcumuladoTecnico::aRespuesta)
                .sorted(Comparator.comparing(ReporteTrabajosResponse.PorTecnico::paraTecnico).reversed())
                .toList(),
            porDia.entrySet().stream()
                .map(entry -> new PuntoGraficoDTO(entry.getKey().toString(), entry.getValue()))
                .toList(),
            porMedio.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .map(entry -> new PuntoGraficoDTO(entry.getKey(), entry.getValue()))
                .toList(),
            List.copyOf(porMes.values()),
            comisiones.values().stream()
                .sorted(Comparator.comparing(ReporteTrabajosResponse.ComisionMes::mes)
                    .thenComparing(ReporteTrabajosResponse.ComisionMes::nombre))
                .toList(),
            detalle);
    }

    /**
     * Minutos de cada trabajo: del primer "En camino" al primer aviso "Terminé" del técnico posterior
     * (o, si no avisó, al primer Terminado del jefe). Sin alguno de los dos registros, no hay tiempo:
     * los trabajos cargados antes del historial (o cerrados sin pasar por esos estados) no cuentan.
     */
    Map<Integer, Long> minutosDeTrabajo(List<Venta> trabajos) {
        if (trabajos.isEmpty()) {
            return Map.of();
        }
        Map<Integer, LocalDateTime> enCamino = new HashMap<>();
        Map<Integer, LocalDateTime> avisoTecnico = new HashMap<>();
        Map<Integer, LocalDateTime> terminadoJefe = new HashMap<>();
        for (HistorialEstadoTrabajo h : historialRepo.findByVentaIdVentaInOrderByCreadoEnAsc(
                trabajos.stream().map(Venta::getIdVenta).toList())) {
            Integer id = h.getVenta().getIdVenta();
            String estado = h.getEstadoNuevo();
            if ("EN_CAMINO".equals(estado)) {
                enCamino.putIfAbsent(id, h.getCreadoEn());
            } else if (enCamino.containsKey(id) && VentaService.ESTADO_TERMINADO_TECNICO.equals(estado)) {
                avisoTecnico.putIfAbsent(id, h.getCreadoEn());
            } else if (enCamino.containsKey(id) && VentaService.ESTADO_TERMINADO.equals(estado)) {
                terminadoJefe.putIfAbsent(id, h.getCreadoEn());
            }
        }
        Map<Integer, Long> minutos = new HashMap<>();
        for (Map.Entry<Integer, LocalDateTime> inicio : enCamino.entrySet()) {
            LocalDateTime fin = avisoTecnico.getOrDefault(inicio.getKey(), terminadoJefe.get(inicio.getKey()));
            if (fin != null && fin.isAfter(inicio.getValue())) {
                minutos.put(inicio.getKey(), Duration.between(inicio.getValue(), fin).toMinutes());
            }
        }
        return minutos;
    }

    private static final class AcumuladoTecnico {
        private final Integer idEmpleado;
        private final String nombre;
        private int cantidad;
        private BigDecimal manoDeObra = BigDecimal.ZERO;
        private BigDecimal paraTecnico = BigDecimal.ZERO;
        private long minutos;
        private int conTiempo;

        AcumuladoTecnico(Integer idEmpleado, String nombre) {
            this.idEmpleado = idEmpleado;
            this.nombre = nombre;
        }

        void sumar(BigDecimal manoDeObraTrabajo, BigDecimal paraTecnicoTrabajo, Long minutosTrabajo) {
            cantidad++;
            manoDeObra = manoDeObra.add(manoDeObraTrabajo);
            paraTecnico = paraTecnico.add(paraTecnicoTrabajo);
            if (minutosTrabajo != null) {
                minutos += minutosTrabajo;
                conTiempo++;
            }
        }

        ReporteTrabajosResponse.PorTecnico aRespuesta() {
            return new ReporteTrabajosResponse.PorTecnico(idEmpleado, nombre, cantidad, manoDeObra, paraTecnico,
                manoDeObra.subtract(paraTecnico), conTiempo > 0 ? Math.round((double) minutos / conTiempo) : null);
        }
    }
}
