package com.thiago.escenasFX.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.thiago.escenasFX.dto.EmpleadoRequest;
import com.thiago.escenasFX.dto.EmpleadoResponse;
import com.thiago.escenasFX.dto.EmpleadoUpdateRequest;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.service.EmpleadoService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/empleados")
public class EmpleadoController {

    private final EmpleadoService empleadoService;

    public EmpleadoController(EmpleadoService empleadoService) {
        this.empleadoService = empleadoService;
    }

    @GetMapping
    public List<EmpleadoResponse> listar() {
        return empleadoService.listar().stream().map(this::toResponse).toList();
    }

    @PostMapping
    public ResponseEntity<EmpleadoResponse> crear(@Valid @RequestBody EmpleadoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(empleadoService.crear(request)));
    }

    @PutMapping("/{id}")
    public EmpleadoResponse actualizar(@PathVariable Integer id, @Valid @RequestBody EmpleadoUpdateRequest request) {
        return toResponse(empleadoService.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable Integer id) {
        empleadoService.desactivar(id);
        return ResponseEntity.noContent().build();
    }

    private EmpleadoResponse toResponse(Empleado e) {
        return new EmpleadoResponse(e.getIdEmpleado(), e.getNombre(), e.getUsuario(), e.getRol(), e.getEmail(),
            e.getComision(), e.isActivo());
    }
}
