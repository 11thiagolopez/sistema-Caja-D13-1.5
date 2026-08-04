package com.thiago.escenasFX.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.thiago.escenasFX.dto.AbrirCajaRequest;
import com.thiago.escenasFX.dto.ConfirmarRetiroRequest;
import com.thiago.escenasFX.dto.MovimientoCajaResponse;
import com.thiago.escenasFX.dto.ResumenDiaDTO;
import com.thiago.escenasFX.dto.ResumenDiaResponse;
import com.thiago.escenasFX.dto.ResumenRangoDTO;
import com.thiago.escenasFX.dto.ResumenRangoResponse;
import com.thiago.escenasFX.dto.ResumenSesionDTO;
import com.thiago.escenasFX.dto.ResumenSesionResponse;
import com.thiago.escenasFX.dto.RetiroRequest;
import com.thiago.escenasFX.dto.SesionCajaResponse;
import com.thiago.escenasFX.dto.SolicitudRetiroResponse;
import com.thiago.escenasFX.dto.VentaResponse;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.MovimientoCaja;
import com.thiago.escenasFX.model.SesionCaja;
import com.thiago.escenasFX.model.SolicitudRetiro;
import com.thiago.escenasFX.repository.EmpleadoRepository;
import com.thiago.escenasFX.service.CajaService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/caja")
public class CajaController {

    private final CajaService cajaService;
    private final EmpleadoRepository empleadoRepo;

    public CajaController(CajaService cajaService, EmpleadoRepository empleadoRepo) {
        this.cajaService = cajaService;
        this.empleadoRepo = empleadoRepo;
    }

    @PostMapping("/abrir")
    public SesionCajaResponse abrir(@Valid @RequestBody AbrirCajaRequest request) {
        Empleado empleado = empleadoRepo.findById(request.getIdEmpleado())
            .orElseThrow(() -> new IllegalArgumentException("Empleado no existe: " + request.getIdEmpleado()));
        SesionCaja sesion = cajaService.abrirSesion(request.getMontoInicial(), empleado);
        return toResponse(sesion);
    }

    @PostMapping("/cerrar")
    public SesionCajaResponse cerrar() {
        return toResponse(cajaService.cerrarSesionDelDia());
    }

    /**
     * Usado por el modal de "abrir caja" que se muestra al loguearse (ambos roles): 200 con la
     * sesión si hay una ABIERTA, 204 si no hay ninguna.
     */
    @GetMapping("/sesion-abierta")
    public ResponseEntity<SesionCajaResponse> sesionAbierta() {
        Optional<SesionCaja> sesion = cajaService.obtenerSesionAbiertaOpcional();
        return sesion.map(s -> ResponseEntity.ok(toResponse(s))).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/retiro/solicitar")
    public SolicitudRetiroResponse solicitarRetiro(@Valid @RequestBody RetiroRequest request) {
        Empleado empleado = empleadoRepo.findById(request.getIdEmpleado())
            .orElseThrow(() -> new IllegalArgumentException("Empleado no existe: " + request.getIdEmpleado()));
        SolicitudRetiro solicitud = cajaService.solicitarRetiro(request.getMonto(), request.getMotivo(),
            request.getMedioPago(), empleado);
        return toResponse(solicitud);
    }

    @PostMapping("/retiro/confirmar")
    public MovimientoCajaResponse confirmarRetiro(@Valid @RequestBody ConfirmarRetiroRequest request) {
        MovimientoCaja movimiento = cajaService.confirmarRetiro(request.getIdSolicitud(), request.getCodigo());
        return toResponse(movimiento);
    }

    @GetMapping("/resumen-dia")
    public ResumenDiaResponse resumenDelDia() {
        return toResponse(cajaService.calcularResumenDelDia());
    }

    /**
     * Arqueo de un rango de fechas (ej. un mes entero): el total combinado del período más el
     * desglose de cada turno/sesión individual dentro de ese rango.
     */
    @GetMapping("/resumen")
    public ResumenRangoResponse resumenPorRango(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        ResumenRangoDTO rango = cajaService.calcularResumenPorRango(desde, hasta);

        List<ResumenSesionResponse> sesiones = rango.getSesiones().stream()
            .map(this::toResumenSesionResponse)
            .toList();

        return new ResumenRangoResponse(rango.getDesde(), rango.getHasta(), toResponse(rango.getTotal()), sesiones);
    }

    private ResumenSesionResponse toResumenSesionResponse(ResumenSesionDTO s) {
        SesionCaja sesion = s.getSesion();
        String empleadoApertura = sesion.getEmpleadoApertura() != null
            ? sesion.getEmpleadoApertura().getNombre()
            : null;
        return new ResumenSesionResponse(sesion.getIdSesion(), sesion.getFecha(), sesion.getEstado(),
            empleadoApertura, toResponse(s.getResumen()));
    }

    private ResumenDiaResponse toResponse(ResumenDiaDTO resumen) {
        List<VentaResponse> ventas = resumen.getVentas().stream().map(VentaMapper::toResponse).toList();
        List<MovimientoCajaResponse> retiros = resumen.getRetiros().stream().map(this::toResponse).toList();

        return new ResumenDiaResponse(
            ventas,
            retiros,
            resumen.getMontoInicial(),
            resumen.getVentasEfectivo(),
            resumen.getVentasTransferencia(),
            resumen.getVentasTarjeta(),
            resumen.getRetirosEfectivo(),
            resumen.getRetirosTransferencia(),
            resumen.getEfectivoFinal(),
            resumen.getTotalDigital(),
            resumen.getCajaTotalDelDia());
    }

    private SesionCajaResponse toResponse(SesionCaja sesion) {
        return new SesionCajaResponse(sesion.getIdSesion(), sesion.getFecha(), sesion.getMontoInicial(),
            sesion.getEstado());
    }

    private MovimientoCajaResponse toResponse(MovimientoCaja m) {
        return new MovimientoCajaResponse(m.getIdMovimiento(), m.getFecha(), m.getTipo(), m.getMedioPago(),
            m.getMonto(), m.getMotivo());
    }

    private SolicitudRetiroResponse toResponse(SolicitudRetiro s) {
        return new SolicitudRetiroResponse(s.getIdSolicitud(), s.getMonto(), s.getMotivo(), s.getMedioPago(),
            s.getEstado(), s.getOtpExpiraEn());
    }
}
