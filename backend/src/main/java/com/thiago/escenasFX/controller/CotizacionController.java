package com.thiago.escenasFX.controller;

import java.time.LocalDate;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.thiago.escenasFX.dto.CotizacionManualRequest;
import com.thiago.escenasFX.dto.CotizacionResponse;
import com.thiago.escenasFX.model.CotizacionDolar;
import com.thiago.escenasFX.repository.CotizacionDolarRepository;
import com.thiago.escenasFX.service.CotizacionService;

import jakarta.validation.Valid;

/**
 * Gate diario de cotización: el frontend consulta /actual al loguear (cualquier rol) y, si no hay
 * fila de hoy, bloquea el sistema hasta que /cargar (automático) o /manual (solo ADMIN) resuelva
 * una. No toca stock/precios de productos — eso sigue pasando únicamente al abrir caja
 * (CajaService.abrirSesion), que reusa la fila de hoy que este endpoint deja creada.
 */
@RestController
@RequestMapping("/api/cotizacion")
public class CotizacionController {

    private final CotizacionService cotizacionService;
    private final CotizacionDolarRepository cotizacionRepo;

    public CotizacionController(CotizacionService cotizacionService, CotizacionDolarRepository cotizacionRepo) {
        this.cotizacionService = cotizacionService;
        this.cotizacionRepo = cotizacionRepo;
    }

    @GetMapping("/actual")
    public ResponseEntity<CotizacionResponse> actual() {
        return cotizacionRepo.findFirstByFechaOrderByCreadoEnDesc(LocalDate.now())
            .map(c -> ResponseEntity.ok(toResponse(c)))
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/cargar")
    public CotizacionResponse cargar() {
        return toResponse(cotizacionService.obtenerCotizacionDelDia());
    }

    @PostMapping("/manual")
    public CotizacionResponse manual(@Valid @RequestBody CotizacionManualRequest request) {
        return toResponse(cotizacionService.registrarManual(request.getValorVenta()));
    }

    private CotizacionResponse toResponse(CotizacionDolar c) {
        return new CotizacionResponse(c.getValorVenta(), c.getFecha(), c.getFuente(), c.isManual());
    }
}
