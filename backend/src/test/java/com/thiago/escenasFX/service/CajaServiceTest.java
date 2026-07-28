package com.thiago.escenasFX.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.thiago.escenasFX.dto.ResumenDiaDTO;
import com.thiago.escenasFX.dto.ResumenRangoDTO;
import com.thiago.escenasFX.exception.AuthenticationFailedException;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.MovimientoCaja;
import com.thiago.escenasFX.model.SesionCaja;
import com.thiago.escenasFX.model.SolicitudRetiro;
import com.thiago.escenasFX.model.Venta;
import com.thiago.escenasFX.repository.MovimientoCajaRepository;
import com.thiago.escenasFX.repository.SesionCajaRepository;
import com.thiago.escenasFX.repository.SolicitudRetiroRepository;
import com.thiago.escenasFX.repository.VentaRepository;

@ExtendWith(MockitoExtension.class)
class CajaServiceTest {

    @Mock
    private VentaRepository ventaRepo;
    @Mock
    private MovimientoCajaRepository movRepo;
    @Mock
    private SesionCajaRepository sesionRepo;
    @Mock
    private SolicitudRetiroRepository solicitudRetiroRepo;
    @Mock
    private OtpService otpService;
    @Mock
    private EmailService emailService;

    @InjectMocks
    private CajaService cajaService;

    private Venta ventaConfirmada(String medioPago, String total) {
        Venta v = new Venta();
        v.setMedioPago(medioPago);
        v.setTotalVenta(new BigDecimal(total));
        v.setEstado("CONFIRMADA");
        return v;
    }

    private MovimientoCaja retiro(String medioPago, String monto) {
        MovimientoCaja m = new MovimientoCaja();
        m.setMedioPago(medioPago);
        m.setMonto(new BigDecimal(monto));
        m.setTipo("RETIRO");
        return m;
    }

    @Test
    void abrirSesion_sinSesionAbierta_creaSesion() {
        when(sesionRepo.findByFechaAndEstado(eq(LocalDate.now()), eq("ABIERTA"))).thenReturn(Optional.empty());
        when(sesionRepo.save(any(SesionCaja.class))).thenAnswer(inv -> inv.getArgument(0));

        Empleado empleado = new Empleado();
        empleado.setIdEmpleado(1);

        SesionCaja creada = cajaService.abrirSesion(new BigDecimal("1000"), empleado);

        assertThat(creada.getEstado()).isEqualTo("ABIERTA");
        assertThat(creada.getMontoInicial()).isEqualByComparingTo("1000");
        assertThat(creada.getEmpleadoApertura()).isEqualTo(empleado);
        assertThat(creada.getFecha()).isEqualTo(LocalDate.now());
    }

    @Test
    void abrirSesion_yaHaySesionAbiertaHoy_lanzaExcepcion() {
        when(sesionRepo.findByFechaAndEstado(eq(LocalDate.now()), eq("ABIERTA")))
            .thenReturn(Optional.of(new SesionCaja()));

        assertThatThrownBy(() -> cajaService.abrirSesion(new BigDecimal("1000"), new Empleado()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Ya existe una sesión de caja abierta hoy");

        verify(sesionRepo, never()).save(any());
    }

    @Test
    void calcularResumenDeSesion_calculaArqueoConMultiplesMediosDePago() {
        SesionCaja sesion = new SesionCaja();
        sesion.setIdSesion(10);
        sesion.setMontoInicial(new BigDecimal("1000"));
        when(sesionRepo.findById(10)).thenReturn(Optional.of(sesion));

        when(ventaRepo.findBySesion_IdSesionAndEstado(10, "CONFIRMADA")).thenReturn(List.of(
            ventaConfirmada("EFECTIVO", "300"),
            ventaConfirmada("TRANSFERENCIA", "500"),
            ventaConfirmada("TARJETA", "200")));
        when(movRepo.findBySesion_IdSesion(10)).thenReturn(List.of(
            retiro("EFECTIVO", "100")));

        ResumenDiaDTO resumen = cajaService.calcularResumenDeSesion(10);

        assertThat(resumen.getMontoInicial()).isEqualByComparingTo("1000");
        assertThat(resumen.getVentasEfectivo()).isEqualByComparingTo("300");
        assertThat(resumen.getVentasTransferencia()).isEqualByComparingTo("500");
        assertThat(resumen.getVentasTarjeta()).isEqualByComparingTo("200");
        assertThat(resumen.getRetirosEfectivo()).isEqualByComparingTo("100");
        // efectivoFinal = montoInicial + ventasEfectivo - retirosEfectivo = 1000 + 300 - 100
        assertThat(resumen.getEfectivoFinal()).isEqualByComparingTo("1200");
        // totalDigital = ventasTransferencia - retirosTransferencia + ventasTarjeta = 500 - 0 + 200
        assertThat(resumen.getTotalDigital()).isEqualByComparingTo("700");
        assertThat(resumen.getCajaTotalDelDia()).isEqualByComparingTo("1900");
    }

    @Test
    void calcularResumenDeSesion_sesionNoExiste_lanzaExcepcion() {
        when(sesionRepo.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cajaService.calcularResumenDeSesion(99))
            .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Reproduce el escenario real de dos turnos el mismo día: el monto inicial del total debe
     * sumar el de cada sesión del rango (estén abiertas o cerradas), no solo la última abierta
     * -- este es el bug que se encontró y corrigió al agregar el arqueo por turno.
     */
    @Test
    void calcularResumenPorRango_sumaMontoInicialDeTodasLasSesionesDelPeriodo() {
        LocalDate hoy = LocalDate.now();

        SesionCaja sesionManana = new SesionCaja();
        sesionManana.setIdSesion(10);
        sesionManana.setFecha(hoy);
        sesionManana.setEstado("CERRADA");
        sesionManana.setMontoInicial(new BigDecimal("1000"));

        SesionCaja sesionTarde = new SesionCaja();
        sesionTarde.setIdSesion(11);
        sesionTarde.setFecha(hoy);
        sesionTarde.setEstado("ABIERTA");
        sesionTarde.setMontoInicial(new BigDecimal("2000"));

        when(sesionRepo.findByFechaBetweenOrderByFechaAscIdSesionAsc(hoy, hoy))
            .thenReturn(List.of(sesionManana, sesionTarde));
        when(sesionRepo.findById(10)).thenReturn(Optional.of(sesionManana));
        when(sesionRepo.findById(11)).thenReturn(Optional.of(sesionTarde));
        // Ninguna venta/retiro vinculado a una sesión puntual en este caso de prueba.
        when(ventaRepo.findBySesion_IdSesionAndEstado(any(), eq("CONFIRMADA"))).thenReturn(List.of());
        when(movRepo.findBySesion_IdSesion(any())).thenReturn(List.of());

        when(ventaRepo.findByFechaBetweenAndEstado(any(LocalDateTime.class), any(LocalDateTime.class),
            eq("CONFIRMADA"))).thenReturn(List.of(ventaConfirmada("EFECTIVO", "500")));
        when(movRepo.findByFechaBetween(any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(List.of());

        ResumenRangoDTO rango = cajaService.calcularResumenPorRango(hoy, hoy);

        assertThat(rango.getTotal().getMontoInicial()).isEqualByComparingTo("3000");
        assertThat(rango.getTotal().getVentasEfectivo()).isEqualByComparingTo("500");
        assertThat(rango.getTotal().getEfectivoFinal()).isEqualByComparingTo("3500");

        assertThat(rango.getSesiones()).hasSize(2);
        assertThat(rango.getSesiones().get(0).getResumen().getMontoInicial()).isEqualByComparingTo("1000");
        assertThat(rango.getSesiones().get(1).getResumen().getMontoInicial()).isEqualByComparingTo("2000");
    }

    @Test
    void calcularResumenDelDia_noPierdeElMontoInicialAunqueLaUltimaSesionEsteCerrada() {
        LocalDate hoy = LocalDate.now();

        SesionCaja sesionCerrada = new SesionCaja();
        sesionCerrada.setIdSesion(5);
        sesionCerrada.setFecha(hoy);
        sesionCerrada.setEstado("CERRADA");
        sesionCerrada.setMontoInicial(new BigDecimal("500"));

        when(sesionRepo.findByFechaBetweenOrderByFechaAscIdSesionAsc(hoy, hoy)).thenReturn(List.of(sesionCerrada));
        when(sesionRepo.findById(5)).thenReturn(Optional.of(sesionCerrada));
        when(ventaRepo.findBySesion_IdSesionAndEstado(5, "CONFIRMADA")).thenReturn(List.of());
        when(movRepo.findBySesion_IdSesion(5)).thenReturn(List.of());
        when(ventaRepo.findByFechaBetweenAndEstado(any(LocalDateTime.class), any(LocalDateTime.class),
            eq("CONFIRMADA"))).thenReturn(List.of(ventaConfirmada("EFECTIVO", "200")));
        when(movRepo.findByFechaBetween(any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(List.of());

        ResumenDiaDTO resumen = cajaService.calcularResumenDelDia();

        // Antes del fix, esto daba 0 porque solo buscaba la sesión ABIERTA de hoy.
        assertThat(resumen.getMontoInicial()).isEqualByComparingTo("500");
        assertThat(resumen.getEfectivoFinal()).isEqualByComparingTo("700");
    }

    @Test
    void solicitarRetiro_generaCodigoLoHasheaYEnviaEmail() {
        when(otpService.generarCodigo()).thenReturn("654321");
        when(otpService.hash("654321")).thenReturn("hash-retiro");
        LocalDateTime expiracion = LocalDateTime.now().plusMinutes(10);
        when(otpService.nuevaExpiracion()).thenReturn(expiracion);
        when(solicitudRetiroRepo.save(any(SolicitudRetiro.class))).thenAnswer(inv -> inv.getArgument(0));

        Empleado solicitante = new Empleado();
        solicitante.setIdEmpleado(2);

        SolicitudRetiro solicitud = cajaService.solicitarRetiro(new BigDecimal("300"), "Gastos varios", "EFECTIVO",
            solicitante);

        assertThat(solicitud.getEstado()).isEqualTo("PENDIENTE");
        assertThat(solicitud.getOtpHash()).isEqualTo("hash-retiro");
        assertThat(solicitud.getOtpExpiraEn()).isEqualTo(expiracion);
        assertThat(solicitud.getMonto()).isEqualByComparingTo("300");
        verify(emailService).enviarOtpAAdmins(anyString(), anyString());
    }

    private SolicitudRetiro solicitudPendiente() {
        SolicitudRetiro s = new SolicitudRetiro();
        s.setIdSolicitud(1);
        s.setMonto(new BigDecimal("300"));
        s.setMotivo("Gastos varios");
        s.setMedioPago("EFECTIVO");
        s.setEmpleadoSolicitante(new Empleado());
        s.setOtpHash("hash-retiro");
        s.setEstado("PENDIENTE");
        return s;
    }

    @Test
    void confirmarRetiro_codigoCorrecto_creaMovimientoCajaYConfirmaSolicitud() {
        SolicitudRetiro solicitud = solicitudPendiente();
        solicitud.setOtpExpiraEn(LocalDateTime.now().plusMinutes(5));
        when(solicitudRetiroRepo.findById(1)).thenReturn(Optional.of(solicitud));
        when(otpService.coincide("654321", "hash-retiro")).thenReturn(true);
        when(sesionRepo.findByFechaAndEstado(any(LocalDate.class), anyString())).thenReturn(Optional.empty());
        when(movRepo.save(any(MovimientoCaja.class))).thenAnswer(inv -> inv.getArgument(0));

        MovimientoCaja movimiento = cajaService.confirmarRetiro(1, "654321");

        assertThat(movimiento.getTipo()).isEqualTo("RETIRO");
        assertThat(movimiento.getMonto()).isEqualByComparingTo("300");
        assertThat(solicitud.getEstado()).isEqualTo("CONFIRMADA");
        verify(solicitudRetiroRepo).save(solicitud);
    }

    @Test
    void confirmarRetiro_codigoIncorrecto_lanzaAuthenticationFailedException() {
        SolicitudRetiro solicitud = solicitudPendiente();
        solicitud.setOtpExpiraEn(LocalDateTime.now().plusMinutes(5));
        when(solicitudRetiroRepo.findById(1)).thenReturn(Optional.of(solicitud));
        when(otpService.coincide("000000", "hash-retiro")).thenReturn(false);

        assertThatThrownBy(() -> cajaService.confirmarRetiro(1, "000000"))
            .isInstanceOf(AuthenticationFailedException.class);

        verify(movRepo, never()).save(any());
    }

    @Test
    void confirmarRetiro_solicitudYaProcesada_lanzaIllegalStateException() {
        SolicitudRetiro solicitud = solicitudPendiente();
        solicitud.setEstado("CONFIRMADA");
        when(solicitudRetiroRepo.findById(1)).thenReturn(Optional.of(solicitud));

        assertThatThrownBy(() -> cajaService.confirmarRetiro(1, "654321"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("ya fue procesada");

        verify(movRepo, never()).save(any());
    }

    @Test
    void confirmarRetiro_otpExpirado_marcaExpiradaYLanzaExcepcion() {
        SolicitudRetiro solicitud = solicitudPendiente();
        solicitud.setOtpExpiraEn(LocalDateTime.now().minusMinutes(1));
        when(solicitudRetiroRepo.findById(1)).thenReturn(Optional.of(solicitud));

        assertThatThrownBy(() -> cajaService.confirmarRetiro(1, "654321"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("expiró");

        assertThat(solicitud.getEstado()).isEqualTo("EXPIRADA");
        verify(movRepo, never()).save(any());
    }
}
