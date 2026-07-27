package com.thiago.escenasFX.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.thiago.escenasFX.model.MovimientoCaja;
import com.thiago.escenasFX.model.SesionCaja;

/**
 * Cubre SesionCajaRepository y MovimientoCajaRepository juntos: comparten el mismo concepto de
 * turno (SesionCaja) que sostiene el arqueo por turno de CajaService.
 */
@DataJpaTest
class CajaRepositoriesTest {

    @Autowired
    private SesionCajaRepository sesionRepo;

    @Autowired
    private MovimientoCajaRepository movRepo;

    @Autowired
    private TestEntityManager em;

    private SesionCaja sesion(LocalDate fecha, String estado, String montoInicial) {
        SesionCaja s = new SesionCaja();
        s.setFecha(fecha);
        s.setEstado(estado);
        s.setMontoInicial(new BigDecimal(montoInicial));
        return em.persistAndFlush(s);
    }

    @Test
    void findByFechaAndEstado_encuentraLaSesionAbiertaDeHoy() {
        sesion(LocalDate.now(), "ABIERTA", "1000");

        assertThat(sesionRepo.findByFechaAndEstado(LocalDate.now(), "ABIERTA")).isPresent();
        assertThat(sesionRepo.findByFechaAndEstado(LocalDate.now(), "CERRADA")).isEmpty();
    }

    @Test
    void findByFechaBetweenOrderByFechaAscIdSesionAsc_devuelveEnOrden() {
        LocalDate hoy = LocalDate.now();
        SesionCaja turnoTarde = sesion(hoy, "ABIERTA", "2000");
        SesionCaja turnoManana = sesion(hoy.minusDays(1), "CERRADA", "1000");

        var resultado = sesionRepo.findByFechaBetweenOrderByFechaAscIdSesionAsc(hoy.minusDays(1), hoy);

        assertThat(resultado).extracting(SesionCaja::getIdSesion)
            .containsExactly(turnoManana.getIdSesion(), turnoTarde.getIdSesion());
    }

    @Test
    void findBySesion_IdSesion_devuelveSoloLosMovimientosDeEsaSesion() {
        SesionCaja sesionUno = sesion(LocalDate.now(), "CERRADA", "1000");
        SesionCaja sesionDos = sesion(LocalDate.now(), "ABIERTA", "2000");

        MovimientoCaja retiroUno = new MovimientoCaja();
        retiroUno.setTipo("RETIRO");
        retiroUno.setMedioPago("EFECTIVO");
        retiroUno.setMonto(new BigDecimal("100"));
        retiroUno.setMotivo("Gastos");
        retiroUno.setSesion(sesionUno);
        movRepo.saveAndFlush(retiroUno);

        MovimientoCaja retiroDos = new MovimientoCaja();
        retiroDos.setTipo("RETIRO");
        retiroDos.setMedioPago("EFECTIVO");
        retiroDos.setMonto(new BigDecimal("200"));
        retiroDos.setMotivo("Otros gastos");
        retiroDos.setSesion(sesionDos);
        movRepo.saveAndFlush(retiroDos);

        var resultado = movRepo.findBySesion_IdSesion(sesionUno.getIdSesion());

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getMonto()).isEqualByComparingTo("100");
    }

    @Test
    void findByFechaBetween_filtraMovimientosPorRangoDeFechas() {
        MovimientoCaja dentro = new MovimientoCaja();
        dentro.setFecha(LocalDateTime.of(2026, 1, 15, 10, 0));
        dentro.setTipo("RETIRO");
        dentro.setMedioPago("EFECTIVO");
        dentro.setMonto(new BigDecimal("50"));
        dentro.setMotivo("Gastos");
        movRepo.saveAndFlush(dentro);

        MovimientoCaja fuera = new MovimientoCaja();
        fuera.setFecha(LocalDateTime.of(2026, 3, 1, 10, 0));
        fuera.setTipo("RETIRO");
        fuera.setMedioPago("EFECTIVO");
        fuera.setMonto(new BigDecimal("75"));
        fuera.setMotivo("Otros");
        movRepo.saveAndFlush(fuera);

        var resultado = movRepo.findByFechaBetween(
            LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 1, 31, 23, 59));

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getMonto()).isEqualByComparingTo("50");
    }
}
