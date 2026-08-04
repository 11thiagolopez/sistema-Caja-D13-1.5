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

import com.thiago.escenasFX.dto.ProveedorRequest;
import com.thiago.escenasFX.dto.ProveedorResponse;
import com.thiago.escenasFX.model.Proveedor;
import com.thiago.escenasFX.service.ProveedorService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/proveedores")
public class ProveedorController {

    private final ProveedorService proveedorService;

    public ProveedorController(ProveedorService proveedorService) {
        this.proveedorService = proveedorService;
    }

    @GetMapping
    public List<ProveedorResponse> listar() {
        return proveedorService.listar().stream().map(this::toResponse).toList();
    }

    @PostMapping
    public ResponseEntity<ProveedorResponse> crear(@Valid @RequestBody ProveedorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(proveedorService.crear(request)));
    }

    @PutMapping("/{id}")
    public ProveedorResponse actualizar(@PathVariable Integer id, @Valid @RequestBody ProveedorRequest request) {
        return toResponse(proveedorService.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        proveedorService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    private ProveedorResponse toResponse(Proveedor p) {
        return new ProveedorResponse(p.getIdProveedor(), p.getNombre(), p.getContacto(), p.getTelefono(),
            p.getEmail(), p.isActivo());
    }
}
