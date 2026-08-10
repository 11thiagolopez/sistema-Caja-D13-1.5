package com.thiago.escenasFX.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.thiago.escenasFX.dto.PresupuestoRequest;
import com.thiago.escenasFX.dto.PresupuestoResponse;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.Presupuesto;
import com.thiago.escenasFX.repository.EmpleadoRepository;
import com.thiago.escenasFX.service.PresupuestoService;

import jakarta.validation.Valid;

/**
 * A diferencia de /api/reportes, /api/compras, etc. (exclusivos de ADMIN), este endpoint es
 * hasAnyRole("ADMIN", "VENDEDOR") en SecurityConfig: es una herramienta de venta del día a día
 * (mismo espíritu que Cobros) que no afecta stock ni caja, no un reporte administrativo.
 */
@RestController
@RequestMapping("/api/presupuestos")
public class PresupuestoController {

    private final PresupuestoService presupuestoService;
    private final EmpleadoRepository empleadoRepo;

    public PresupuestoController(PresupuestoService presupuestoService, EmpleadoRepository empleadoRepo) {
        this.presupuestoService = presupuestoService;
        this.empleadoRepo = empleadoRepo;
    }

    @PostMapping
    public PresupuestoResponse crear(@Valid @RequestBody PresupuestoRequest request) {
        Empleado empleado = empleadoRepo.findById(request.getIdEmpleado())
            .orElseThrow(() -> new IllegalArgumentException("Empleado no existe: " + request.getIdEmpleado()));
        Presupuesto presupuesto = presupuestoService.crear(request, empleado);
        return PresupuestoMapper.toResponse(presupuesto);
    }

    @GetMapping
    public List<PresupuestoResponse> listar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return presupuestoService.listarPorRango(desde, hasta).stream()
            .map(PresupuestoMapper::toResponse)
            .toList();
    }

    @GetMapping("/{id}")
    public PresupuestoResponse obtener(@PathVariable Integer id) {
        return PresupuestoMapper.toResponse(presupuestoService.obtenerPorId(id));
    }

    @PostMapping("/{id}/enviar-email")
    public PresupuestoResponse enviarEmail(@PathVariable Integer id) {
        return PresupuestoMapper.toResponse(presupuestoService.enviarPorEmail(id));
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> descargarPdf(@PathVariable Integer id) {
        byte[] pdf = presupuestoService.generarPdf(id);
        String nombreArchivo = "presupuesto-" + id + ".pdf";
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(nombreArchivo).build().toString())
            .body(pdf);
    }
}
