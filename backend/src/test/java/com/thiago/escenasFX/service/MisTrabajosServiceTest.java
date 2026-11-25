package com.thiago.escenasFX.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.NotaTrabajo;
import com.thiago.escenasFX.model.Venta;
import com.thiago.escenasFX.repository.NotaTrabajoRepository;
import com.thiago.escenasFX.repository.VentaRepository;

@ExtendWith(MockitoExtension.class)
class MisTrabajosServiceTest {

    @Mock
    private VentaRepository ventaRepo;
    @Mock
    private NotaTrabajoRepository notaTrabajoRepo;
    @Mock
    private HistorialEstadoTrabajoService historialEstadoService;
    @Mock
    private NotificacionTrabajoService notificacionTrabajoService;

    @InjectMocks
    private MisTrabajosService misTrabajosService;

    private Empleado tecnico(int id) {
        Empleado e = new Empleado();
        e.setIdEmpleado(id);
        return e;
    }

    private Venta trabajoDe(Empleado tecnico, String estadoTrabajo, String estado) {
        Venta venta = new Venta();
        venta.setIdVenta(1);
        venta.setTipoVenta("DOMICILIO");
        venta.setEmpleadoTecnico(tecnico);
        venta.setEstadoTrabajo(estadoTrabajo);
        venta.setEstado(estado);
        return venta;
    }

    @Test
    void cambiarEstado_deOtroTecnico_rechaza() {
        Empleado fabian = tecnico(1);
        Empleado otro = tecnico(2);
        Venta venta = trabajoDe(fabian, "AGENDADO", "EN_PROGRESO");
        when(ventaRepo.buscarPorIdConLock(1)).thenReturn(Optional.of(venta));

        org.junit.jupiter.api.Assertions.assertThrows(AccessDeniedException.class,
            () -> misTrabajosService.cambiarEstado(1, "EN_CAMINO", otro));
        verify(ventaRepo, never()).save(any());
    }

    @Test
    void cambiarEstado_aEstadoNoPermitidoParaElTecnico_rechaza() {
        Empleado fabian = tecnico(1);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
            () -> misTrabajosService.cambiarEstado(1, "CERRADO_Y_COBRADO", fabian));
        verify(ventaRepo, never()).buscarPorIdConLock(any());
    }

    @Test
    void cambiarEstado_trabajoYaCerrado_rechaza() {
        Empleado fabian = tecnico(1);
        Venta venta = trabajoDe(fabian, "CERRADO_Y_COBRADO", "CONFIRMADA");
        when(ventaRepo.buscarPorIdConLock(1)).thenReturn(Optional.of(venta));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
            () -> misTrabajosService.cambiarEstado(1, "EN_CAMINO", fabian));
        verify(ventaRepo, never()).save(any());
    }

    @Test
    void cambiarEstado_trabajoCancelado_rechaza() {
        Empleado fabian = tecnico(1);
        Venta venta = trabajoDe(fabian, "CANCELADO", "ANULADA");
        when(ventaRepo.buscarPorIdConLock(1)).thenReturn(Optional.of(venta));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
            () -> misTrabajosService.cambiarEstado(1, "EN_CAMINO", fabian));
    }

    @Test
    void cambiarEstado_propioYValido_actualizaYRegistraHistorial() {
        Empleado fabian = tecnico(1);
        Venta venta = trabajoDe(fabian, "AGENDADO", "EN_PROGRESO");
        when(ventaRepo.buscarPorIdConLock(1)).thenReturn(Optional.of(venta));
        when(ventaRepo.save(any(Venta.class))).thenAnswer(inv -> inv.getArgument(0));

        Venta actualizado = misTrabajosService.cambiarEstado(1, "EN_CAMINO", fabian);

        assertThat(actualizado.getEstadoTrabajo()).isEqualTo("EN_CAMINO");
        verify(historialEstadoService).registrar(actualizado, "AGENDADO", "EN_CAMINO", fabian, null);
        verify(notificacionTrabajoService).avisarATodosLosAdmin(org.mockito.ArgumentMatchers.eq("EN_CAMINO"),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(actualizado));
    }

    @Test
    void cambiarEstado_avisoDeTerminado_avisaAlJefeSinBloquearElTrabajo() {
        Empleado fabian = tecnico(1);
        Venta venta = trabajoDe(fabian, "EN_CURSO", "EN_PROGRESO");
        when(ventaRepo.buscarPorIdConLock(1)).thenReturn(Optional.of(venta));
        when(ventaRepo.save(any(Venta.class))).thenAnswer(inv -> inv.getArgument(0));

        Venta actualizado = misTrabajosService.cambiarEstado(1, "TERMINADO_TECNICO", fabian);

        assertThat(actualizado.getEstadoTrabajo()).isEqualTo("TERMINADO_TECNICO");
        assertThat(VentaService.estaBloqueado(actualizado)).isFalse();
        verify(notificacionTrabajoService).avisarATodosLosAdmin(org.mockito.ArgumentMatchers.eq("TERMINADO"),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(actualizado));
    }

    @Test
    void cambiarEstado_elTecnicoNoPuedeFijarElTerminadoDelJefe() {
        Empleado fabian = tecnico(1);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
            () -> misTrabajosService.cambiarEstado(1, "TERMINADO", fabian));
    }

    @Test
    void cambiarEstado_deUnTrabajoTerminadoPorElJefe_rechaza() {
        Empleado fabian = tecnico(1);
        Venta venta = trabajoDe(fabian, "TERMINADO", "EN_PROGRESO");
        when(ventaRepo.buscarPorIdConLock(1)).thenReturn(Optional.of(venta));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
            () -> misTrabajosService.cambiarEstado(1, "EN_CURSO", fabian));
    }

    @Test
    void agregarNota_deOtroTecnico_rechaza() {
        Empleado fabian = tecnico(1);
        Empleado otro = tecnico(2);
        Venta venta = trabajoDe(fabian, "AGENDADO", "EN_PROGRESO");
        when(ventaRepo.findById(1)).thenReturn(Optional.of(venta));

        org.junit.jupiter.api.Assertions.assertThrows(AccessDeniedException.class,
            () -> misTrabajosService.agregarNota(1, "intento ajeno", otro));
        verify(notaTrabajoRepo, never()).save(any());
    }

    @Test
    void agregarNota_trabajoYaCerrado_rechaza() {
        Empleado fabian = tecnico(1);
        Venta venta = trabajoDe(fabian, "CERRADO_Y_COBRADO", "CONFIRMADA");
        when(ventaRepo.findById(1)).thenReturn(Optional.of(venta));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
            () -> misTrabajosService.agregarNota(1, "demasiado tarde", fabian));
    }

    @Test
    void agregarNota_propia_seGuardaConElTecnicoComoAutor() {
        Empleado fabian = tecnico(1);
        Venta venta = trabajoDe(fabian, "EN_CURSO", "EN_PROGRESO");
        when(ventaRepo.findById(1)).thenReturn(Optional.of(venta));
        when(notaTrabajoRepo.save(any(NotaTrabajo.class))).thenAnswer(inv -> inv.getArgument(0));

        NotaTrabajo nota = misTrabajosService.agregarNota(1, "Falta un repuesto", fabian);

        assertThat(nota.getTexto()).isEqualTo("Falta un repuesto");
        assertThat(nota.getEmpleado()).isEqualTo(fabian);
        assertThat(nota.getVenta()).isEqualTo(venta);
        verify(notificacionTrabajoService).avisarATodosLosAdmin(org.mockito.ArgumentMatchers.eq("NOTA"),
            org.mockito.ArgumentMatchers.contains("Falta un repuesto"), org.mockito.ArgumentMatchers.eq(venta));
    }

    @Test
    void listar_delegaEnElRepositorioConElIdDelTecnico() {
        Empleado fabian = tecnico(7);

        misTrabajosService.listar(fabian);

        verify(ventaRepo).buscarTrabajosDelTecnico(7);
    }
}
