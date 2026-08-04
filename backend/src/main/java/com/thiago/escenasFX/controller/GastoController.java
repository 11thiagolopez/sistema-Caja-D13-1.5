package com.thiago.escenasFX.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.thiago.escenasFX.dto.GastoRequest;
import com.thiago.escenasFX.dto.GastoResponse;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.Gasto;
import com.thiago.escenasFX.repository.EmpleadoRepository;
import com.thiago.escenasFX.service.GastoService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/gastos")
public class GastoController {

    private final GastoService gastoService;
    private final EmpleadoRepository empleadoRepo;

    public GastoController(GastoService gastoService, EmpleadoRepository empleadoRepo) {
        this.gastoService = gastoService;
        this.empleadoRepo = empleadoRepo;
    }

    @PostMapping
    public ResponseEntity<GastoResponse> crear(@Valid @RequestBody GastoRequest request) {
        Empleado empleado = empleadoRepo.findById(request.getIdEmpleado())
            .orElseThrow(() -> new IllegalArgumentException("Empleado no existe: " + request.getIdEmpleado()));
        Gasto gasto = gastoService.crear(request, empleado);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(gasto));
    }

    @GetMapping
    public List<GastoResponse> listar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return gastoService.listarPorRango(desde, hasta).stream().map(this::toResponse).toList();
    }

    private GastoResponse toResponse(Gasto g) {
        String empleadoNombre = g.getEmpleadoRegistro() != null ? g.getEmpleadoRegistro().getNombre() : null;
        return new GastoResponse(g.getIdGasto(), g.getNombre(), g.getImporte(), g.getFecha(), g.getCategoria(),
            empleadoNombre, g.getCreadoEn());
    }
}
