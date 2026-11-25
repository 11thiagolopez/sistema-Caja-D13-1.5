package com.thiago.escenasFX.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.thiago.escenasFX.dto.NotificacionResponse;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.Notificacion;
import com.thiago.escenasFX.repository.EmpleadoRepository;
import com.thiago.escenasFX.service.NotificacionTrabajoService;

/**
 * La campanita: cada empleado ve solo sus propias notificaciones (sin matcher de rol propio en
 * SecurityConfig — cualquier rol autenticado puede tener avisos, la pertenencia la filtra siempre
 * la query por su propio id de empleado, nunca por uno del request).
 */
@RestController
@RequestMapping("/api/notificaciones")
public class NotificacionController {

    private final NotificacionTrabajoService notificacionTrabajoService;
    private final EmpleadoRepository empleadoRepo;

    public NotificacionController(NotificacionTrabajoService notificacionTrabajoService,
            EmpleadoRepository empleadoRepo) {
        this.notificacionTrabajoService = notificacionTrabajoService;
        this.empleadoRepo = empleadoRepo;
    }

    @GetMapping
    public List<NotificacionResponse> listar(Authentication authentication) {
        Empleado empleado = RolAutenticado.empleado(authentication, empleadoRepo);
        return notificacionTrabajoService.listar(empleado).stream().map(this::toResponse).toList();
    }

    @GetMapping("/no-leidas")
    public Map<String, Long> contarNoLeidas(Authentication authentication) {
        Empleado empleado = RolAutenticado.empleado(authentication, empleadoRepo);
        return Map.of("cantidad", notificacionTrabajoService.contarNoLeidas(empleado));
    }

    @PostMapping("/{id}/leida")
    public ResponseEntity<Void> marcarLeida(@PathVariable Integer id, Authentication authentication) {
        Empleado empleado = RolAutenticado.empleado(authentication, empleadoRepo);
        notificacionTrabajoService.marcarLeida(id, empleado);
        return ResponseEntity.noContent().build();
    }

    private NotificacionResponse toResponse(Notificacion n) {
        return new NotificacionResponse(n.getIdNotificacion(), n.getTipo(), n.getTexto(),
            n.getVenta() != null ? n.getVenta().getIdVenta() : null, n.isLeida(), n.getCreadaEn());
    }
}
