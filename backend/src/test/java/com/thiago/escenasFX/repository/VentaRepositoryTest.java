package com.thiago.escenasFX.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.thiago.escenasFX.model.DetalleVenta;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.Producto;
import com.thiago.escenasFX.model.SesionCaja;
import com.thiago.escenasFX.model.Venta;

@DataJpaTest
class VentaRepositoryTest {

    @Autowired
    private VentaRepository ventaRepo;

    @Autowired
    private TestEntityManager em;

    private Empleado empleado() {
        Empleado e = new Empleado();
        e.setNombre("Vendedor");
        e.setUsuario("vendedor" + System.nanoTime());
        e.setPasswordHash("hash");
        e.setRol("VENDEDOR");
        return em.persistAndFlush(e);
    }

    private Producto producto() {
        Producto p = new Producto();
        p.setDescripcion("Producto de prueba");
        p.setStockActual(50);
        p.setPrecioVenta(new BigDecimal("100"));
        return em.persistAndFlush(p);
    }

    private Venta venta(LocalDateTime fecha, String estado, String medioPago, Producto producto,
            SesionCaja sesion) {
        Venta v = new Venta();
        v.setFecha(fecha);
        v.setEmpleado(empleado());
        v.setEstado(estado);
        v.setMedioPago(medioPago);
        v.setTotalVenta(new BigDecimal("300"));
        v.setSesion(sesion);

        DetalleVenta d = new DetalleVenta();
        d.setProducto(producto);
        d.setCantidad(3);
        d.setPrecioUnitario(new BigDecimal("100"));
        d.setSubtotal(new BigDecimal("300"));
        d.setVenta(v);
        v.getDetalles().add(d);

        return v;
    }

    @Test
    void guardarVenta_persisteLosDetallesEnCascada() {
        Producto producto = producto();
        Venta guardada = ventaRepo.saveAndFlush(venta(LocalDateTime.now(), "CONFIRMADA", "EFECTIVO", producto, null));
        em.clear();

        Venta recuperada = ventaRepo.findById(guardada.getIdVenta()).orElseThrow();

        assertThat(recuperada.getDetalles()).hasSize(1);
        assertThat(recuperada.getDetalles().get(0).getProducto().getIdProducto()).isEqualTo(producto.getIdProducto());
        assertThat(recuperada.getDetalles().get(0).getSubtotal()).isEqualByComparingTo("300");
    }

    @Test
    void eliminarDetalleDeLaColeccion_loBorraPorOrphanRemoval() {
        Producto producto = producto();
        Venta guardada = ventaRepo.saveAndFlush(venta(LocalDateTime.now(), "CONFIRMADA", "EFECTIVO", producto, null));
        Integer idDetalle = guardada.getDetalles().get(0).getIdDetalle();

        guardada.getDetalles().clear();
        ventaRepo.saveAndFlush(guardada);
        em.clear();

        assertThat(em.find(DetalleVenta.class, idDetalle)).isNull();
    }

    @Test
    void findByFechaBetween_filtraPorRangoDeFechas() {
        Producto producto = producto();
        LocalDateTime dentroDelRango = LocalDateTime.of(2026, 1, 15, 10, 0);
        LocalDateTime fueraDelRango = LocalDateTime.of(2026, 2, 1, 10, 0);

        ventaRepo.saveAndFlush(venta(dentroDelRango, "CONFIRMADA", "EFECTIVO", producto, null));
        ventaRepo.saveAndFlush(venta(fueraDelRango, "CONFIRMADA", "EFECTIVO", producto, null));

        var resultado = ventaRepo.findByFechaBetween(
            LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 1, 31, 23, 59));

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getFecha()).isEqualTo(dentroDelRango);
    }

    @Test
    void findByFechaBetweenAndEstado_excluyeLasPendientesDeAutorizacion() {
        Producto producto = producto();
        LocalDateTime fecha = LocalDateTime.of(2026, 3, 10, 12, 0);

        ventaRepo.saveAndFlush(venta(fecha, "CONFIRMADA", "EFECTIVO", producto, null));
        ventaRepo.saveAndFlush(venta(fecha, "PENDIENTE_AUTORIZACION", "EFECTIVO", producto, null));

        var resultado = ventaRepo.findByFechaBetweenAndEstado(
            LocalDateTime.of(2026, 3, 1, 0, 0), LocalDateTime.of(2026, 3, 31, 23, 59), "CONFIRMADA");

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getEstado()).isEqualTo("CONFIRMADA");
    }

    @Test
    void findBySesion_IdSesionAndEstado_filtraPorTurno() {
        Producto producto = producto();
        SesionCaja sesionManana = new SesionCaja();
        sesionManana.setFecha(LocalDate.now());
        sesionManana.setMontoInicial(new BigDecimal("1000"));
        sesionManana.setEstado("CERRADA");
        em.persistAndFlush(sesionManana);

        SesionCaja sesionTarde = new SesionCaja();
        sesionTarde.setFecha(LocalDate.now());
        sesionTarde.setMontoInicial(new BigDecimal("2000"));
        sesionTarde.setEstado("ABIERTA");
        em.persistAndFlush(sesionTarde);

        ventaRepo.saveAndFlush(venta(LocalDateTime.now(), "CONFIRMADA", "EFECTIVO", producto, sesionManana));
        ventaRepo.saveAndFlush(venta(LocalDateTime.now(), "CONFIRMADA", "EFECTIVO", producto, sesionTarde));

        var resultado = ventaRepo.findBySesion_IdSesionAndEstado(sesionManana.getIdSesion(), "CONFIRMADA");

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getSesion().getIdSesion()).isEqualTo(sesionManana.getIdSesion());
    }
}
