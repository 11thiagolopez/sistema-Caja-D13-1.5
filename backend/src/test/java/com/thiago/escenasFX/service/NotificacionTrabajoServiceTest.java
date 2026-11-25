package com.thiago.escenasFX.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.Notificacion;
import com.thiago.escenasFX.model.Venta;
import com.thiago.escenasFX.repository.EmpleadoRepository;
import com.thiago.escenasFX.repository.NotificacionRepository;

@ExtendWith(MockitoExtension.class)
class NotificacionTrabajoServiceTest {

    @Mock
    private NotificacionRepository notificacionRepo;
    @Mock
    private EmpleadoRepository empleadoRepo;
    @Mock
    private PushService pushService;

    private NotificacionTrabajoService service;

    private Empleado empleado(int id) {
        Empleado e = new Empleado();
        e.setIdEmpleado(id);
        return e;
    }

    private Venta venta(int id) {
        Venta v = new Venta();
        v.setIdVenta(id);
        return v;
    }

    private NotificacionTrabajoService crearServicio() {
        return new NotificacionTrabajoService(notificacionRepo, empleadoRepo, pushService, new ObjectMapper());
    }

    @Test
    void avisarATecnico_guardaLaNotificacionYMandaElPushConElUrlDelTrabajo() {
        service = crearServicio();
        Empleado fabian = empleado(1);
        Venta venta = venta(42);
        when(notificacionRepo.save(any(Notificacion.class))).thenAnswer(inv -> inv.getArgument(0));

        service.avisarATecnico(fabian, "TRABAJO_NUEVO", "Nuevo trabajo: Belgrano 233", venta);

        ArgumentCaptor<Notificacion> captor = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificacionRepo).save(captor.capture());
        assertThat(captor.getValue().getEmpleado()).isEqualTo(fabian);
        assertThat(captor.getValue().getTipo()).isEqualTo("TRABAJO_NUEVO");
        assertThat(captor.getValue().getVenta()).isEqualTo(venta);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(pushService).avisarConContenido(eq(fabian), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue()).contains("\"tipo\":\"TRABAJO_NUEVO\"").contains("/mis-trabajos/42");
    }

    @Test
    void avisarATodosLosAdmin_avisaACadaAdminActivoPorSeparado() {
        service = crearServicio();
        Empleado admin1 = empleado(1);
        Empleado admin2 = empleado(2);
        Venta venta = venta(7);
        when(empleadoRepo.findByRolAndActivoTrue("ADMIN")).thenReturn(List.of(admin1, admin2));
        when(notificacionRepo.save(any(Notificacion.class))).thenAnswer(inv -> inv.getArgument(0));

        service.avisarATodosLosAdmin("TERMINADO", "Trabajo #7 terminado", venta);

        verify(pushService).avisarConContenido(eq(admin1), anyString());
        verify(pushService).avisarConContenido(eq(admin2), anyString());
    }

    @Test
    void guardarYEnviar_textoMuyLargo_loAcortaParaQueEntreEnLaColumna() {
        service = crearServicio();
        Empleado fabian = empleado(1);
        Venta venta = venta(1);
        String textoLargo = "x".repeat(300);
        when(notificacionRepo.save(any(Notificacion.class))).thenAnswer(inv -> inv.getArgument(0));

        service.avisarATecnico(fabian, "NOTA", textoLargo, venta);

        ArgumentCaptor<Notificacion> captor = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificacionRepo).save(captor.capture());
        assertThat(captor.getValue().getTexto()).hasSizeLessThanOrEqualTo(255);
    }

    @Test
    void siElPushFalla_laNotificacionYaQuedoGuardada() {
        service = crearServicio();
        Empleado fabian = empleado(1);
        Venta venta = venta(1);
        when(notificacionRepo.save(any(Notificacion.class))).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.doThrow(new RuntimeException("sin red")).when(pushService)
            .avisarConContenido(any(), anyString());

        service.avisarATecnico(fabian, "TRABAJO_NUEVO", "texto", venta);

        verify(notificacionRepo).save(any(Notificacion.class));
    }

    @Test
    void marcarLeida_deOtroEmpleado_rechaza() {
        service = crearServicio();
        when(notificacionRepo.findByIdNotificacionAndEmpleado_IdEmpleado(1, 2)).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
            () -> service.marcarLeida(1, empleado(2)));
    }

    @Test
    void marcarLeida_propia_laMarca() {
        service = crearServicio();
        Notificacion notificacion = new Notificacion();
        notificacion.setLeida(false);
        when(notificacionRepo.findByIdNotificacionAndEmpleado_IdEmpleado(1, 2)).thenReturn(Optional.of(notificacion));

        service.marcarLeida(1, empleado(2));

        assertThat(notificacion.isLeida()).isTrue();
        verify(notificacionRepo).save(notificacion);
    }
}
