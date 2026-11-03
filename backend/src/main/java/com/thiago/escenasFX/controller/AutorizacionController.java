package com.thiago.escenasFX.controller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.thiago.escenasFX.dto.AutorizacionEstadoResponse;
import com.thiago.escenasFX.dto.AutorizacionPendienteResponse;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.SolicitudRetiro;
import com.thiago.escenasFX.model.Venta;
import com.thiago.escenasFX.repository.EmpleadoRepository;
import com.thiago.escenasFX.repository.SolicitudRetiroRepository;
import com.thiago.escenasFX.repository.VentaRepository;
import com.thiago.escenasFX.service.CajaService;
import com.thiago.escenasFX.service.VentaService;

/**
 * Peticiones de un VENDEDOR que espera la respuesta de un ADMIN (retiro de caja o descuento de
 * venta). Salen de las tablas de cada operación, no de una tabla propia: una petición no es más
 * que una solicitud o una venta todavía pendiente.
 */
@RestController
@RequestMapping("/api/autorizaciones")
public class AutorizacionController {

    private static final String RETIRO = "RETIRO";
    private static final String DESCUENTO = "DESCUENTO";

    private final CajaService cajaService;
    private final VentaService ventaService;
    private final EmpleadoRepository empleadoRepo;
    private final SolicitudRetiroRepository solicitudRetiroRepo;
    private final VentaRepository ventaRepo;

    public AutorizacionController(CajaService cajaService, VentaService ventaService, EmpleadoRepository empleadoRepo,
            SolicitudRetiroRepository solicitudRetiroRepo, VentaRepository ventaRepo) {
        this.cajaService = cajaService;
        this.ventaService = ventaService;
        this.empleadoRepo = empleadoRepo;
        this.solicitudRetiroRepo = solicitudRetiroRepo;
        this.ventaRepo = ventaRepo;
    }

    @GetMapping
    public List<AutorizacionPendienteResponse> pendientes() {
        List<AutorizacionPendienteResponse> pendientes = new ArrayList<>();
        cajaService.listarRetirosPendientes().forEach(s -> pendientes.add(toPendiente(s)));
        ventaService.listarDescuentosPendientes().forEach(v -> pendientes.add(toPendiente(v)));
        pendientes.sort(Comparator.comparing(AutorizacionPendienteResponse::getFecha));
        return pendientes;
    }

    /**
     * Cómo quedaron las peticiones que el propio empleado ya conoce por id, para actualizar su
     * panel. A propósito no lista nada ni devuelve montos: el VENDEDOR no debe tener ningún
     * endpoint de "mis ventas" (decisión del dueño, ver SecurityConfig). Ids ajenos se ignoran.
     */
    @GetMapping("/estado")
    public List<AutorizacionEstadoResponse> estado(@RequestParam(defaultValue = "") List<Integer> retiros,
            @RequestParam(defaultValue = "") List<Integer> descuentos, Authentication authentication) {
        Integer idEmpleado = RolAutenticado.empleado(authentication, empleadoRepo).getIdEmpleado();

        List<AutorizacionEstadoResponse> estados = new ArrayList<>();
        solicitudRetiroRepo.findByIdSolicitudInAndEmpleadoSolicitante_IdEmpleado(retiros, idEmpleado)
            .forEach(s -> estados.add(toEstado(s)));
        ventaRepo.findByIdVentaInAndEmpleado_IdEmpleado(descuentos, idEmpleado)
            .forEach(v -> estados.add(toEstado(v)));
        return estados;
    }

    @PostMapping("/retiros/{id}/aprobar")
    public AutorizacionEstadoResponse aprobarRetiro(@PathVariable Integer id, Authentication authentication) {
        return toEstado(cajaService.aprobarRetiro(id, admin(authentication)));
    }

    @PostMapping("/retiros/{id}/rechazar")
    public AutorizacionEstadoResponse rechazarRetiro(@PathVariable Integer id, Authentication authentication) {
        return toEstado(cajaService.rechazarRetiro(id, admin(authentication)));
    }

    @PostMapping("/descuentos/{id}/aprobar")
    public AutorizacionEstadoResponse aprobarDescuento(@PathVariable Integer id, Authentication authentication) {
        return toEstado(ventaService.aprobarDescuento(id, admin(authentication)));
    }

    @PostMapping("/descuentos/{id}/rechazar")
    public AutorizacionEstadoResponse rechazarDescuento(@PathVariable Integer id, Authentication authentication) {
        return toEstado(ventaService.rechazarDescuento(id, admin(authentication)));
    }

    private Empleado admin(Authentication authentication) {
        return RolAutenticado.empleado(authentication, empleadoRepo);
    }

    private AutorizacionPendienteResponse toPendiente(SolicitudRetiro s) {
        return new AutorizacionPendienteResponse(RETIRO, s.getIdSolicitud(), s.getMonto(), s.getMotivo(),
            nombre(s.getEmpleadoSolicitante()), s.getCreadoEn(), s.getMedioPago(), null);
    }

    private AutorizacionPendienteResponse toPendiente(Venta v) {
        return new AutorizacionPendienteResponse(DESCUENTO, v.getIdVenta(), v.getDescuento(), v.getMotivoDescuento(),
            nombre(v.getEmpleado()), v.getFecha(), v.getMedioPago(), v.getTotalVenta());
    }

    private String nombre(Empleado empleado) {
        return empleado != null ? empleado.getNombre() : null;
    }

    // EXPIRADA es histórico (flujo viejo con código): para quien la pidió, es que no se aprobó.
    private AutorizacionEstadoResponse toEstado(SolicitudRetiro s) {
        String estado = switch (s.getEstado()) {
            case "PENDIENTE" -> "PENDIENTE";
            case "CONFIRMADA" -> "APROBADA";
            default -> "RECHAZADA";
        };
        return new AutorizacionEstadoResponse(RETIRO, s.getIdSolicitud(), estado);
    }

    private AutorizacionEstadoResponse toEstado(Venta v) {
        String estado = switch (v.getEstado()) {
            case "PENDIENTE_AUTORIZACION" -> "PENDIENTE";
            case "CONFIRMADA" -> "APROBADA";
            default -> "RECHAZADA";
        };
        return new AutorizacionEstadoResponse(DESCUENTO, v.getIdVenta(), estado);
    }
}
