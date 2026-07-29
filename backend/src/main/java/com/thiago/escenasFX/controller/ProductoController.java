package com.thiago.escenasFX.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.thiago.escenasFX.dto.CargarStockRequest;
import com.thiago.escenasFX.dto.ProductoRequest;
import com.thiago.escenasFX.model.Producto;
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
    public List<Producto> listar() {
        return productoService.listarTodos();
    }

    @GetMapping("/buscar-por-codigo")
    public Producto buscarPorCodigo(@RequestParam String codigo) {
        return productoService.buscarPorCodigo(codigo);
    }

    @GetMapping("/{id}")
    public Producto obtener(@PathVariable Integer id) {
        return productoService.obtenerPorId(id);
    }

    @PostMapping
    public ResponseEntity<Producto> crear(@Valid @RequestBody ProductoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productoService.crear(request));
    }

    @PostMapping("/cargar-stock")
    public Producto cargarStock(@Valid @RequestBody CargarStockRequest request) {
        return productoService.cargarStock(request.getCodigo(), request.getCantidad());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        productoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
