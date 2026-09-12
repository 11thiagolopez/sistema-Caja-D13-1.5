package com.thiago.escenasFX.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.thiago.escenasFX.model.Producto;

@ExtendWith(MockitoExtension.class)
class NotificacionServiceTest {

    @Mock
    private EmailService emailService;

    private NotificacionService notificacionService;

    @BeforeEach
    void setUp() {
        notificacionService = new NotificacionService(emailService);
    }

    private Producto producto() {
        Producto p = new Producto();
        p.setIdProducto(1);
        p.setDescripcion("Cable 2x1.5mm");
        p.setCodigoInterno("0101010007");
        return p;
    }

    @Test
    void cruzaExactoElUmbralDe5_enviaElAviso() {
        notificacionService.evaluarYNotificarStockBajo(producto(), 6, 5);

        ArgumentCaptor<String> asunto = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> cuerpo = ArgumentCaptor.forClass(String.class);
        verify(emailService).enviarAAdmins(asunto.capture(), cuerpo.capture());
        assertThat(cuerpo.getValue())
            .contains("Cable 2x1.5mm")
            .contains("0101010007")
            .contains("5 unidad");
    }

    @Test
    void cruzaExactoElUmbralDe2_enviaLaAlertaUrgente() {
        notificacionService.evaluarYNotificarStockBajo(producto(), 3, 2);

        verify(emailService).enviarAAdmins(anyString(), anyString());
    }

    @Test
    void unaVentaGrandeQueCruzaLosDosUmbralesALaVez_enviaLosDosAvisos() {
        // De 6 a 1 en una sola venta: cruza tanto el umbral de 5 como el de 2.
        notificacionService.evaluarYNotificarStockBajo(producto(), 6, 1);

        verify(emailService, times(2)).enviarAAdmins(anyString(), anyString());
    }

    @Test
    void yaEstabaPorDebajoDe5YSigueBajando_noReenviaElMismoAviso() {
        // 4 -> 3: ya había cruzado 5 antes, esta venta no cruza ningún umbral nuevo.
        notificacionService.evaluarYNotificarStockBajo(producto(), 4, 3);

        verify(emailService, never()).enviarAAdmins(anyString(), anyString());
    }

    @Test
    void elStockSube_noEnviaNadaAunqueQuedeBajo() {
        // Ej: se anula una venta o se carga stock — nunca es una baja, no corresponde alertar.
        notificacionService.evaluarYNotificarStockBajo(producto(), 1, 4);

        verify(emailService, never()).enviarAAdmins(anyString(), anyString());
    }

    @Test
    void siFallaElEnvioDeEmail_noPropagaLaExcepcion() {
        doThrow(new RuntimeException("Resend caído")).when(emailService).enviarAAdmins(anyString(), anyString());

        // No debe lanzar: la venta que disparó esto tiene que poder completarse igual.
        notificacionService.evaluarYNotificarStockBajo(producto(), 6, 5);
    }
}
