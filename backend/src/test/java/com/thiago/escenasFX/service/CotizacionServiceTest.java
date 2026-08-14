package com.thiago.escenasFX.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.thiago.escenasFX.exception.CotizacionNoDisponibleException;
import com.thiago.escenasFX.model.CotizacionDolar;
import com.thiago.escenasFX.repository.CotizacionDolarRepository;

@ExtendWith(MockitoExtension.class)
class CotizacionServiceTest {

    @Mock
    private CotizacionDolarRepository cotizacionRepo;

    @Mock
    private CotizacionApiClient apiClient;

    @InjectMocks
    private CotizacionService cotizacionService;

    @Test
    void obtenerCotizacionDelDia_yaHayUnaDeHoy_noConsultaLasApis() {
        CotizacionDolar deHoy = new CotizacionDolar();
        deHoy.setValorVenta(new BigDecimal("1200"));
        when(cotizacionRepo.findFirstByFechaOrderByCreadoEnDesc(LocalDate.now())).thenReturn(Optional.of(deHoy));

        CotizacionDolar resultado = cotizacionService.obtenerCotizacionDelDia();

        assertThat(resultado.getValorVenta()).isEqualByComparingTo("1200");
        verify(apiClient, never()).consultarPrimaria();
        verify(cotizacionRepo, never()).save(any());
    }

    @Test
    void obtenerCotizacionDelDia_sinCotizacionDeHoy_usaLaPrimaria() {
        when(cotizacionRepo.findFirstByFechaOrderByCreadoEnDesc(LocalDate.now())).thenReturn(Optional.empty());
        when(apiClient.consultarPrimaria()).thenReturn(Optional.of(new BigDecimal("1250")));
        when(cotizacionRepo.save(any(CotizacionDolar.class))).thenAnswer(inv -> inv.getArgument(0));

        CotizacionDolar resultado = cotizacionService.obtenerCotizacionDelDia();

        assertThat(resultado.getValorVenta()).isEqualByComparingTo("1250");
        ArgumentCaptor<CotizacionDolar> captor = ArgumentCaptor.forClass(CotizacionDolar.class);
        verify(cotizacionRepo).save(captor.capture());
        assertThat(captor.getValue().getFuente()).isEqualTo("dolarapi.com");
        assertThat(captor.getValue().isManual()).isFalse();
        verify(apiClient, never()).consultarSecundaria();
    }

    @Test
    void obtenerCotizacionDelDia_primariaFalla_usaLaSecundaria() {
        when(cotizacionRepo.findFirstByFechaOrderByCreadoEnDesc(LocalDate.now())).thenReturn(Optional.empty());
        when(apiClient.consultarPrimaria()).thenReturn(Optional.empty());
        when(apiClient.consultarSecundaria()).thenReturn(Optional.of(new BigDecimal("1260")));
        when(cotizacionRepo.save(any(CotizacionDolar.class))).thenAnswer(inv -> inv.getArgument(0));

        CotizacionDolar resultado = cotizacionService.obtenerCotizacionDelDia();

        assertThat(resultado.getValorVenta()).isEqualByComparingTo("1260");
        ArgumentCaptor<CotizacionDolar> captor = ArgumentCaptor.forClass(CotizacionDolar.class);
        verify(cotizacionRepo).save(captor.capture());
        assertThat(captor.getValue().getFuente()).isEqualTo("dolar-bna");
    }

    @Test
    void obtenerCotizacionDelDia_fallanLasDosApis_lanzaExcepcionYNoGuardaNada() {
        when(cotizacionRepo.findFirstByFechaOrderByCreadoEnDesc(LocalDate.now())).thenReturn(Optional.empty());
        when(apiClient.consultarPrimaria()).thenReturn(Optional.empty());
        when(apiClient.consultarSecundaria()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cotizacionService.obtenerCotizacionDelDia())
            .isInstanceOf(CotizacionNoDisponibleException.class);

        verify(cotizacionRepo, never()).save(any());
    }

    @Test
    void registrarManual_guardaConFuenteManual() {
        when(cotizacionRepo.save(any(CotizacionDolar.class))).thenAnswer(inv -> inv.getArgument(0));

        CotizacionDolar resultado = cotizacionService.registrarManual(new BigDecimal("1300"));

        assertThat(resultado.getValorVenta()).isEqualByComparingTo("1300");
        ArgumentCaptor<CotizacionDolar> captor = ArgumentCaptor.forClass(CotizacionDolar.class);
        verify(cotizacionRepo).save(captor.capture());
        assertThat(captor.getValue().getFuente()).isEqualTo("MANUAL");
        assertThat(captor.getValue().isManual()).isTrue();
    }

    @Test
    void ultimaConocida_devuelveElValorMasReciente() {
        CotizacionDolar cotizacion = new CotizacionDolar();
        cotizacion.setValorVenta(new BigDecimal("1400"));
        when(cotizacionRepo.findFirstByOrderByCreadoEnDesc()).thenReturn(Optional.of(cotizacion));

        assertThat(cotizacionService.ultimaConocida()).contains(new BigDecimal("1400"));
    }

    @Test
    void ultimaConocida_sinNingunaCargada_devuelveEmpty() {
        when(cotizacionRepo.findFirstByOrderByCreadoEnDesc()).thenReturn(Optional.empty());

        assertThat(cotizacionService.ultimaConocida()).isEmpty();
    }
}
