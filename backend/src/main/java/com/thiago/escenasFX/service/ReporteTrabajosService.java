package com.thiago.escenasFX.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thiago.escenasFX.dto.PuntoGraficoDTO;
import com.thiago.escenasFX.dto.ReporteTrabajosResponse;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.Venta;
import com.thiago.escenasFX.repository.VentaRepository;

/**
 * Reporte de trabajos a domicilio, separado del de mostrador. Cuenta los CONFIRMADOS por el día en
 * que se cobraron. Como los gráficos de Reportes, no descuenta notas de crédito.
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

    public ReporteTrabajosService(VentaRepository ventaRepo) {
        this.ventaRepo = ventaRepo;
    }

    public ReporteTrabajosResponse reporte(LocalDate desde, LocalDate hasta) {
        if (desde.isAfter(hasta)) {
            throw new IllegalArgumentException("La fecha desde no puede ser posterior a la fecha hasta");
        }

        List<Venta> trabajos = ventaRepo.findByTipoVentaAndEstadoAndFechaBetweenOrderByFechaDesc("DOMICILIO", "CONFIRMADA",
            ZonaNegocio.inicioDelDia(desde), ZonaNegocio.finDelDia(hasta));

        BigDecimal total = BigDecimal.ZERO;
        BigDecimal manoDeObra = BigDecimal.ZERO;
        BigDecimal paraTecnicos = BigDecimal.ZERO;
        BigDecimal costoArticulos = BigDecimal.ZERO;

        Map<LocalDate, BigDecimal> porDia = new LinkedHashMap<>();
        for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
            porDia.put(dia, BigDecimal.ZERO);
        }
        Map<String, BigDecimal> porMedio = new LinkedHashMap<>();
        Map<Integer, ReporteTrabajosResponse.PorTecnico> porTecnico = new LinkedHashMap<>();
        List<ReporteTrabajosResponse.Trabajo> detalle = new ArrayList<>();

        for (Venta trabajo : trabajos) {
            BigDecimal totalTrabajo = trabajo.getTotalVenta();
            BigDecimal manoDeObraTrabajo = CuentasTrabajo.manoDeObra(trabajo);
            BigDecimal paraTecnico = CuentasTrabajo.paraTecnico(trabajo);

            total = total.add(totalTrabajo);
            manoDeObra = manoDeObra.add(manoDeObraTrabajo);
            paraTecnicos = paraTecnicos.add(paraTecnico);
            costoArticulos = costoArticulos.add(CuentasTrabajo.costoArticulos(trabajo));

            porDia.merge(ZonaNegocio.diaDe(trabajo.getFecha()), totalTrabajo, BigDecimal::add);
            String medio = ETIQUETAS_MEDIO_PAGO.getOrDefault(String.valueOf(trabajo.getMedioPago()), SIN_MEDIO);
            porMedio.merge(medio, totalTrabajo, BigDecimal::add);
            sumarTecnico(porTecnico, trabajo.getEmpleadoTecnico(), manoDeObraTrabajo, paraTecnico);

            detalle.add(new ReporteTrabajosResponse.Trabajo(trabajo.getIdVenta(), trabajo.getFecha(),
                trabajo.getDireccionTrabajo(), trabajo.getClienteNombre(),
                trabajo.getEmpleadoTecnico() != null ? trabajo.getEmpleadoTecnico().getNombre() : null, medio,
                totalTrabajo, manoDeObraTrabajo, totalTrabajo.subtract(manoDeObraTrabajo), paraTecnico,
                totalTrabajo.subtract(paraTecnico)));
        }

        BigDecimal paraNegocio = total.subtract(paraTecnicos);

        return new ReporteTrabajosResponse(trabajos.size(), total, manoDeObra, total.subtract(manoDeObra), paraTecnicos,
            paraNegocio, costoArticulos, paraNegocio.subtract(costoArticulos),
            porTecnico.values().stream()
                .sorted(Comparator.comparing(ReporteTrabajosResponse.PorTecnico::paraTecnico).reversed())
                .toList(),
            porDia.entrySet().stream()
                .map(entry -> new PuntoGraficoDTO(entry.getKey().toString(), entry.getValue()))
                .toList(),
            porMedio.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .map(entry -> new PuntoGraficoDTO(entry.getKey(), entry.getValue()))
                .toList(),
            detalle);
    }

    private void sumarTecnico(Map<Integer, ReporteTrabajosResponse.PorTecnico> porTecnico, Empleado tecnico,
            BigDecimal manoDeObra, BigDecimal paraTecnico) {
        Integer id = tecnico != null ? tecnico.getIdEmpleado() : null;
        ReporteTrabajosResponse.PorTecnico actual = porTecnico.get(id);
        if (actual == null) {
            porTecnico.put(id, new ReporteTrabajosResponse.PorTecnico(id, tecnico != null ? tecnico.getNombre() : "Sin técnico",
                1, manoDeObra, paraTecnico, manoDeObra.subtract(paraTecnico)));
            return;
        }
        porTecnico.put(id, new ReporteTrabajosResponse.PorTecnico(id, actual.nombre(), actual.cantidadTrabajos() + 1,
            actual.manoDeObra().add(manoDeObra), actual.paraTecnico().add(paraTecnico),
            actual.paraNegocio().add(manoDeObra.subtract(paraTecnico))));
    }
}
