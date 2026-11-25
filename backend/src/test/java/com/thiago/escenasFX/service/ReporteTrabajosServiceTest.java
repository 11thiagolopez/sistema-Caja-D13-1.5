package com.thiago.escenasFX.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.thiago.escenasFX.dto.ReporteTrabajosResponse;
import com.thiago.escenasFX.model.DetalleVenta;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.HistorialEstadoTrabajo;
import com.thiago.escenasFX.model.Venta;
import com.thiago.escenasFX.repository.HistorialEstadoTrabajoRepository;
import com.thiago.escenasFX.repository.VentaRepository;

/** Etapa 3: tiempo promedio por trabajo y resúmenes por mes (cantidad, ingresos, comisiones). */
class ReporteTrabajosServiceTest {

    private VentaRepository ventaRepo;
    private HistorialEstadoTrabajoRepository historialRepo;
    private ReporteTrabajosService service;
    private Empleado fabian;

    @BeforeEach
    void setUp() {
        ventaRepo = mock(VentaRepository.class);
        historialRepo = mock(HistorialEstadoTrabajoRepository.class);
        service = new ReporteTrabajosService(ventaRepo, historialRepo);
        fabian = new Empleado();
        fabian.setIdEmpleado(3);
        fabian.setNombre("Fabián");
        fabian.setComision(new BigDecimal("50"));
    }

    private Venta trabajo(int id, LocalDateTime cobrado, String manoDeObra) {
        Venta v = new Venta();
        v.setIdVenta(id);
        v.setTipoVenta("DOMICILIO");
        v.setEstado("CONFIRMADA");
        v.setFecha(cobrado);
        v.setEmpleadoTecnico(fabian);
        v.setComisionTecnico(new BigDecimal("50"));
        v.setMedioPago("EFECTIVO");
        DetalleVenta d = new DetalleVenta();
        d.setTipo("SERVICIO");
        d.setDescripcion("Mano de obra");
        d.setCantidad(1);
        d.setPrecioUnitario(new BigDecimal(manoDeObra));
        d.setSubtotal(new BigDecimal(manoDeObra));
        d.setVenta(v);
        v.getDetalles().add(d);
        v.setTotalVenta(new BigDecimal(manoDeObra));
        return v;
    }

    private HistorialEstadoTrabajo h(Venta v, String estado, LocalDateTime cuando) {
        HistorialEstadoTrabajo h = new HistorialEstadoTrabajo();
        h.setVenta(v);
        h.setEstadoNuevo(estado);
        h.setCreadoEn(cuando);
        return h;
    }

    @Test
    void tiempoPromedio_delPrimerEnCaminoAlAvisoDelTecnico_oAlTerminadoDelJefe() {
        LocalDateTime dia = LocalDateTime.of(2026, 11, 10, 9, 0);
        Venta conAviso = trabajo(1, dia.plusHours(8), "10000");
        Venta sinAviso = trabajo(2, dia.plusHours(8), "10000");
        Venta sinEnCamino = trabajo(3, dia.plusHours(8), "10000");
        when(ventaRepo.findByTipoVentaAndEstadoAndFechaBetweenOrderByFechaDesc(eq("DOMICILIO"), eq("CONFIRMADA"), any(), any()))
            .thenReturn(List.of(conAviso, sinAviso, sinEnCamino));
        when(historialRepo.findByVentaIdVentaInOrderByCreadoEnAsc(anyCollection())).thenReturn(List.of(
            h(conAviso, "EN_CAMINO", dia),
            h(conAviso, "EN_CURSO", dia.plusMinutes(30)),
            h(conAviso, "TERMINADO_TECNICO", dia.plusMinutes(90)),
            h(conAviso, "TERMINADO", dia.plusMinutes(200)),
            h(sinAviso, "EN_CAMINO", dia),
            h(sinAviso, "TERMINADO", dia.plusMinutes(150)),
            h(sinEnCamino, "TERMINADO_TECNICO", dia.plusMinutes(60))));

        ReporteTrabajosResponse r = service.reporte(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));

        // 90 (hasta el aviso, no hasta el Terminado del jefe) y 150 (sin aviso → Terminado del jefe).
        assertThat(r.trabajosConTiempo()).isEqualTo(2);
        assertThat(r.tiempoPromedioMinutos()).isEqualTo(120L);
        assertThat(r.porTecnico().get(0).tiempoPromedioMinutos()).isEqualTo(120L);
        assertThat(r.trabajos()).filteredOn(t -> t.idVenta() == 3).first()
            .extracting(ReporteTrabajosResponse.Trabajo::minutos).isNull();
    }

    @Test
    void porMes_yComisionesPorMes_separanCadaMesDelRango() {
        when(ventaRepo.findByTipoVentaAndEstadoAndFechaBetweenOrderByFechaDesc(eq("DOMICILIO"), eq("CONFIRMADA"), any(), any()))
            .thenReturn(List.of(
                trabajo(1, LocalDateTime.of(2026, 10, 15, 12, 0), "10000"),
                trabajo(2, LocalDateTime.of(2026, 11, 3, 12, 0), "20000"),
                trabajo(3, LocalDateTime.of(2026, 11, 20, 12, 0), "30000")));
        when(historialRepo.findByVentaIdVentaInOrderByCreadoEnAsc(anyCollection())).thenReturn(List.of());

        ReporteTrabajosResponse r = service.reporte(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 11, 30));

        // Septiembre aparece en cero: el gráfico muestra todos los meses del rango.
        assertThat(r.porMes()).extracting(ReporteTrabajosResponse.PorMes::mes)
            .containsExactly("2026-09", "2026-10", "2026-11");
        assertThat(r.porMes().get(0).cantidadTrabajos()).isZero();
        assertThat(r.porMes().get(2).cantidadTrabajos()).isEqualTo(2);
        assertThat(r.porMes().get(2).total()).isEqualByComparingTo("50000");

        assertThat(r.comisionesPorMes()).hasSize(2);
        assertThat(r.comisionesPorMes().get(1).mes()).isEqualTo("2026-11");
        assertThat(r.comisionesPorMes().get(1).paraTecnico()).isEqualByComparingTo("25000");
        assertThat(r.tiempoPromedioMinutos()).isNull();
    }
}
