package com.thiago.escenasFX.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.Venta;

class OrdenTrabajoHtmlBuilderTest {

    private Venta trabajo() {
        Empleado tecnico = new Empleado();
        tecnico.setNombre("Fabián");
        Venta venta = new Venta();
        venta.setIdVenta(42);
        venta.setTipoVenta("DOMICILIO");
        venta.setFechaProgramada(LocalDate.of(2026, 11, 26));
        venta.setHoraDesde(LocalTime.of(10, 0));
        venta.setHoraHasta(LocalTime.of(12, 0));
        venta.setEmpleadoTecnico(tecnico);
        venta.setClienteNombre("Pérez & Hijos");
        venta.setClienteTelefono("1155556666");
        venta.setDireccionTrabajo("Belgrano 233");
        venta.setDescripcionTrabajo("Instalar termotanque");
        venta.setObservacionesTecnico("Tocar timbre <2B>");
        return venta;
    }

    @Test
    void construir_incluyeLosDatosDelTrabajoEscapados() {
        String html = OrdenTrabajoHtmlBuilder.construir(trabajo(),
            List.of(new ComprobanteHtmlBuilder.Linea("Mano de obra", 1, new BigDecimal("5000"), new BigDecimal("5000"))),
            new BigDecimal("5000"), "logo.png");

        assertThat(html)
            .contains("ORDEN DE TRABAJO", "N° 42", "26/11/2026", "10:00 a 12:00", "Fabián")
            .contains("Pérez &amp; Hijos", "Tocar timbre &lt;2B&gt;", "Belgrano 233")
            .doesNotContain("1155556666")
            .contains("Instalar termotanque", "1x Mano de obra", "TOTAL", "$5000", "Firma")
            .contains("size: 58mm 210mm")
            .doesNotContain("<2B>")
            .doesNotContain("class='letra'");
    }

    @Test
    void construir_sinItems_loAclaraYNoMuestraTotal() {
        String html = OrdenTrabajoHtmlBuilder.construir(trabajo(), List.of(), BigDecimal.ZERO, "logo.png");

        assertThat(html).contains("Sin artículos ni mano de obra cargados").doesNotContain("TOTAL");
    }

    @Test
    void construir_sinDatosOpcionales_noDejaRenglonesVacios() {
        Venta venta = trabajo();
        venta.setHoraDesde(null);
        venta.setHoraHasta(null);
        venta.setObservacionesTecnico(null);
        venta.setClienteTelefono(null);

        String html = OrdenTrabajoHtmlBuilder.construir(venta, List.of(), BigDecimal.ZERO, "logo.png");

        assertThat(html).doesNotContain("Horario", "Observaciones", "<p class='info'>Tel:");
    }

    @Test
    void franjaHoraria_cubreLasCombinaciones() {
        assertThat(OrdenTrabajoHtmlBuilder.franjaHoraria(LocalTime.of(9, 30), LocalTime.of(11, 0))).isEqualTo("09:30 a 11:00");
        assertThat(OrdenTrabajoHtmlBuilder.franjaHoraria(LocalTime.of(9, 30), null)).isEqualTo("desde las 09:30");
        assertThat(OrdenTrabajoHtmlBuilder.franjaHoraria(null, LocalTime.of(11, 0))).isEqualTo("hasta las 11:00");
        assertThat(OrdenTrabajoHtmlBuilder.franjaHoraria(null, null)).isNull();
    }
}
