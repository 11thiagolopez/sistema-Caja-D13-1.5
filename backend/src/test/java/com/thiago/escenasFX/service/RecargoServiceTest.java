package com.thiago.escenasFX.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.thiago.escenasFX.model.RecargoMedioPago;
import com.thiago.escenasFX.repository.RecargoMedioPagoRepository;

@ExtendWith(MockitoExtension.class)
class RecargoServiceTest {

    @Mock
    private RecargoMedioPagoRepository repo;

    @InjectMocks
    private RecargoService recargoService;

    @Test
    void calcularRecargo_despuesDeLaComisionQuedaExactoElNeto() {
        for (String neto : new String[] {"100", "3700", "12345.67", "0.01"}) {
            for (String pct : new String[] {"20", "15", "3.5"}) {
                BigDecimal n = new BigDecimal(neto);
                BigDecimal p = new BigDecimal(pct);
                BigDecimal cobrado = n.add(RecargoService.calcularRecargo(n, p));
                BigDecimal comision = cobrado.multiply(p).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                // Lo que queda nunca es menos que el precio original.
                assertThat(cobrado.subtract(comision)).isGreaterThanOrEqualTo(n);
            }
        }
    }

    @Test
    void calcularRecargo_casosDelDuenio() {
        assertThat(RecargoService.calcularRecargo(new BigDecimal("100"), new BigDecimal("20"))).isEqualByComparingTo("25");
        assertThat(RecargoService.calcularRecargo(new BigDecimal("100"), new BigDecimal("15"))).isEqualByComparingTo("17.65");
        assertThat(RecargoService.calcularRecargo(new BigDecimal("100"), BigDecimal.ZERO)).isEqualByComparingTo("0");
        assertThat(RecargoService.calcularRecargo(BigDecimal.ZERO, new BigDecimal("20"))).isEqualByComparingTo("0");
    }

    @Test
    void porcentajeDe_sinFilaUsaElPorDefecto_yEfectivoNoLleva() {
        when(repo.findById(RecargoService.TARJETA_CREDITO)).thenReturn(Optional.empty());

        assertThat(recargoService.porcentajeDe(RecargoService.TARJETA_CREDITO)).isEqualByComparingTo("20");
        assertThat(recargoService.porcentajeDe("EFECTIVO")).isEqualByComparingTo("0");
    }

    @Test
    void porcentajeDe_usaElValorGuardado() {
        RecargoMedioPago r = new RecargoMedioPago();
        r.setMedioPago(RecargoService.TARJETA_DEBITO);
        r.setPorcentaje(new BigDecimal("12.50"));
        when(repo.findById(RecargoService.TARJETA_DEBITO)).thenReturn(Optional.of(r));

        assertThat(recargoService.porcentajeDe(RecargoService.TARJETA_DEBITO)).isEqualByComparingTo("12.5");
    }

    @Test
    void actualizar_guardaElPorcentaje() {
        when(repo.findById(RecargoService.TARJETA_CREDITO)).thenReturn(Optional.empty());
        when(repo.save(any(RecargoMedioPago.class))).thenAnswer(inv -> inv.getArgument(0));

        RecargoMedioPago r = recargoService.actualizar(RecargoService.TARJETA_CREDITO, new BigDecimal("22"));

        assertThat(r.getPorcentaje()).isEqualByComparingTo("22.00");
    }

    @Test
    void actualizar_rechazaMediosSinRecargoYPorcentajesFueraDeRango() {
        assertThatThrownBy(() -> recargoService.actualizar("EFECTIVO", new BigDecimal("10")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> recargoService.actualizar(RecargoService.TARJETA_CREDITO, new BigDecimal("95")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> recargoService.actualizar(RecargoService.TARJETA_CREDITO, new BigDecimal("-1")))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
