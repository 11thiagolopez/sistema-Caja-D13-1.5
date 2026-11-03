package com.thiago.escenasFX.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.thiago.escenasFX.dto.SuscripcionPushRequest;
import com.thiago.escenasFX.repository.EmpleadoRepository;
import com.thiago.escenasFX.service.PushService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/push")
public class PushController {

    private final PushService pushService;
    private final EmpleadoRepository empleadoRepo;

    public PushController(PushService pushService, EmpleadoRepository empleadoRepo) {
        this.pushService = pushService;
        this.empleadoRepo = empleadoRepo;
    }

    @GetMapping("/clave-publica")
    public Map<String, String> clavePublica() {
        return Map.of("clavePublica", pushService.clavePublica());
    }

    @PostMapping("/suscripcion")
    public ResponseEntity<Void> guardarSuscripcion(@Valid @RequestBody SuscripcionPushRequest request,
            Authentication authentication) {
        pushService.guardarSuscripcion(request.getEndpoint(), RolAutenticado.empleado(authentication, empleadoRepo));
        return ResponseEntity.noContent().build();
    }
}
