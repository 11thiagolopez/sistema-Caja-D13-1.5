package com.thiago.escenasFX.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.thiago.escenasFX.dto.RecargoRequest;
import com.thiago.escenasFX.model.RecargoMedioPago;
import com.thiago.escenasFX.service.RecargoService;

import jakarta.validation.Valid;

/**
 * GET lo usa Cobros (ambos roles) para mostrar cuánto se cobra por posnet; PUT es solo ADMIN (ver
 * SecurityConfig). El recargo real de cada venta lo calcula VentaService, no el frontend.
 */
@RestController
@RequestMapping("/api/recargos")
public class RecargoController {

    public record RecargoResponse(String medioPago, BigDecimal porcentaje) {
    }

    private final RecargoService recargoService;

    public RecargoController(RecargoService recargoService) {
        this.recargoService = recargoService;
    }

    @GetMapping
    public List<RecargoResponse> listar() {
        return recargoService.listar().stream().map(RecargoController::toResponse).toList();
    }

    @PutMapping("/{medioPago}")
    public RecargoResponse actualizar(@PathVariable String medioPago, @Valid @RequestBody RecargoRequest request) {
        return toResponse(recargoService.actualizar(medioPago.trim().toUpperCase(), request.getPorcentaje()));
    }

    private static RecargoResponse toResponse(RecargoMedioPago r) {
        return new RecargoResponse(r.getMedioPago(), r.getPorcentaje());
    }
}
