package com.thiago.escenasFX.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.thiago.escenasFX.dto.CambiarEstadoTrabajoRequest;
import com.thiago.escenasFX.dto.MiTrabajoResponse;
import com.thiago.escenasFX.dto.NotaTrabajoRequest;
import com.thiago.escenasFX.dto.NotaTrabajoResponse;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.NotaTrabajo;
import com.thiago.escenasFX.model.Venta;
import com.thiago.escenasFX.repository.EmpleadoRepository;
import com.thiago.escenasFX.service.MisTrabajosService;

import jakarta.validation.Valid;

/**
 * "Mis trabajos" del técnico — espejo chico de VentaController/trabajo-domicilio, pero siempre
 * acotado al propio técnico (ver MisTrabajosService). Nunca recibe un idEmpleado del body: quién
 * opera sale siempre del JWT, igual que en RolAutenticado.
 */
@RestController
@RequestMapping("/api/mis-trabajos")
public class MisTrabajosController {

    private final MisTrabajosService misTrabajosService;
    private final EmpleadoRepository empleadoRepo;

    public MisTrabajosController(MisTrabajosService misTrabajosService, EmpleadoRepository empleadoRepo) {
        this.misTrabajosService = misTrabajosService;
        this.empleadoRepo = empleadoRepo;
    }

    @GetMapping
    public List<MiTrabajoResponse> listar(Authentication authentication) {
        Empleado tecnico = RolAutenticado.empleado(authentication, empleadoRepo);
        return misTrabajosService.listar(tecnico).stream().map(v -> toResponse(v, tecnico)).toList();
    }

    @GetMapping("/{id}")
    public MiTrabajoResponse obtener(@PathVariable Integer id, Authentication authentication) {
        Empleado tecnico = RolAutenticado.empleado(authentication, empleadoRepo);
        return toResponse(misTrabajosService.obtener(id, tecnico), tecnico);
    }

    @PostMapping("/{id}/estado")
    public MiTrabajoResponse cambiarEstado(@PathVariable Integer id,
            @Valid @RequestBody CambiarEstadoTrabajoRequest request, Authentication authentication) {
        Empleado tecnico = RolAutenticado.empleado(authentication, empleadoRepo);
        return toResponse(misTrabajosService.cambiarEstado(id, request.getEstado(), tecnico), tecnico);
    }

    @PostMapping("/{id}/notas")
    public ResponseEntity<NotaTrabajoResponse> agregarNota(@PathVariable Integer id,
            @Valid @RequestBody NotaTrabajoRequest request, Authentication authentication) {
        Empleado tecnico = RolAutenticado.empleado(authentication, empleadoRepo);
        NotaTrabajo nota = misTrabajosService.agregarNota(id, request.getTexto(), tecnico);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(nota));
    }

    private MiTrabajoResponse toResponse(Venta venta, Empleado tecnico) {
        List<NotaTrabajoResponse> notas = misTrabajosService.listarNotas(venta.getIdVenta(), tecnico)
            .stream().map(this::toResponse).toList();
        return new MiTrabajoResponse(VentaMapper.toResponse(venta), notas);
    }

    private NotaTrabajoResponse toResponse(NotaTrabajo nota) {
        return new NotaTrabajoResponse(nota.getIdNota(), nota.getTexto(), nota.getEmpleado().getNombre(),
            nota.getCreadoEn());
    }
}
