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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.thiago.escenasFX.dto.GastoRequest;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.Gasto;
import com.thiago.escenasFX.model.SesionCaja;
import com.thiago.escenasFX.repository.GastoRepository;
import com.thiago.escenasFX.repository.SesionCajaRepository;

@ExtendWith(MockitoExtension.class)
class GastoServiceTest {

    @Mock
    private GastoRepository gastoRepo;
    @Mock
    private SesionCajaRepository sesionRepo;

    @InjectMocks
    private GastoService gastoService;

    private GastoRequest request(String medioPago, String origen) {
        GastoRequest req = new GastoRequest();
        req.setNombre("Pago de luz");
        req.setImporte(new BigDecimal("100"));
        req.setFecha(LocalDate.now());
        req.setMedioPago(medioPago);
        req.setOrigen(origen);
        return req;
    }

    @Test
    void crear_deCajaConCajaAbierta_guardaMedioOrigenYSesion() {
        SesionCaja sesion = new SesionCaja();
        when(sesionRepo.findByEstado("ABIERTA")).thenReturn(Optional.of(sesion));
        when(gastoRepo.save(any(Gasto.class))).thenAnswer(inv -> inv.getArgument(0));

        Gasto gasto = gastoService.crear(request("EFECTIVO", "CAJA"), new Empleado());

        assertThat(gasto.getMedioPago()).isEqualTo("EFECTIVO");
        assertThat(gasto.getOrigen()).isEqualTo("CAJA");
        assertThat(gasto.getSesion()).isSameAs(sesion);
    }

    @Test
    void crear_deCajaSinCajaAbierta_rechaza() {
        when(sesionRepo.findByEstado("ABIERTA")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gastoService.crear(request("EFECTIVO", "CAJA"), new Empleado()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No hay una caja abierta");

        verify(gastoRepo, never()).save(any());
    }

    @Test
    void crear_externoSinCajaAbierta_seGuardaSinSesion() {
        when(sesionRepo.findByEstado("ABIERTA")).thenReturn(Optional.empty());
        when(gastoRepo.save(any(Gasto.class))).thenAnswer(inv -> inv.getArgument(0));

        Gasto gasto = gastoService.crear(request("TRANSFERENCIA", "EXTERNO"), new Empleado());

        assertThat(gasto.getOrigen()).isEqualTo("EXTERNO");
        assertThat(gasto.getSesion()).isNull();
    }

    @Test
    void crear_medioInvalido_rechaza() {
        assertThatThrownBy(() -> gastoService.crear(request("TARJETA", "CAJA"), new Empleado()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("medio de pago");

        assertThatThrownBy(() -> gastoService.crear(request(null, "CAJA"), new Empleado()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("medio de pago");
    }

    @Test
    void crear_origenInvalido_rechaza() {
        assertThatThrownBy(() -> gastoService.crear(request("EFECTIVO", "OTRO"), new Empleado()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("origen");

        assertThatThrownBy(() -> gastoService.crear(request("EFECTIVO", null), new Empleado()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("origen");
    }
}
