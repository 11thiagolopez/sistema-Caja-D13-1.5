package com.thiago.escenasFX.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.thiago.escenasFX.dto.CargarStockRequest;
import com.thiago.escenasFX.dto.ProductoRequest;
import com.thiago.escenasFX.dto.ProductoResponse;
import com.thiago.escenasFX.dto.ProductoUpdateRequest;
import com.thiago.escenasFX.service.ProductoService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/productos")
public class ProductoController {

    private final ProductoService productoService;

    public ProductoController(ProductoService productoService) {
        this.productoService = productoService;
    }

    @GetMapping
    public List<ProductoResponse> listar() {
        return productoService.listarTodos().stream().map(ProductoMapper::toResponse).toList();
    }

    @GetMapping("/buscar-por-codigo")
    public ProductoResponse buscarPorCodigo(@RequestParam String codigo) {
        return ProductoMapper.toResponse(productoService.buscarPorCodigo(codigo));
    }

    @GetMapping("/{id}")
    public ProductoResponse obtener(@PathVariable Integer id) {
        return ProductoMapper.toResponse(productoService.obtenerPorId(id));
    }

    @PostMapping
    public ResponseEntity<ProductoResponse> crear(@Valid @RequestBody ProductoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductoMapper.toResponse(productoService.crear(request)));
    }

    @PostMapping("/cargar-stock")
    public ProductoResponse cargarStock(@Valid @RequestBody CargarStockRequest request) {
        return ProductoMapper.toResponse(productoService.cargarStock(request.getCodigo(), request.getCantidad()));
    }

    @PatchMapping("/{id}")
    public ProductoResponse actualizar(@PathVariable Integer id, @Valid @RequestBody ProductoUpdateRequest request) {
        return ProductoMapper.toResponse(productoService.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        productoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
