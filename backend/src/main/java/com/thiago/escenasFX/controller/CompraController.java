package com.thiago.escenasFX.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.thiago.escenasFX.dto.CompraRequest;
import com.thiago.escenasFX.dto.CompraResponse;
import com.thiago.escenasFX.dto.PagoProveedorDTO;
import com.thiago.escenasFX.dto.ProductoComprasRankingDTO;
import com.thiago.escenasFX.model.Compra;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.repository.EmpleadoRepository;
import com.thiago.escenasFX.service.CompraService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/compras")
public class CompraController {

    private final CompraService compraService;
    private final EmpleadoRepository empleadoRepo;

    public CompraController(CompraService compraService, EmpleadoRepository empleadoRepo) {
        this.compraService = compraService;
        this.empleadoRepo = empleadoRepo;
    }

    @PostMapping
    public CompraResponse registrar(@Valid @RequestBody CompraRequest request) {
        Empleado empleado = empleadoRepo.findById(request.getIdEmpleado())
            .orElseThrow(() -> new IllegalArgumentException("Empleado no existe: " + request.getIdEmpleado()));
        Compra compra = compraService.registrarCompra(request, empleado);
        return CompraMapper.toResponse(compra);
    }

    @GetMapping
    public List<CompraResponse> listar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return compraService.listarPorRango(desde, hasta).stream().map(CompraMapper::toResponse).toList();
    }

    @GetMapping("/pagos-proveedor")
    public List<PagoProveedorDTO> pagosPorProveedor(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return compraService.pagosPorProveedor(desde, hasta);
    }

    @GetMapping("/productos-mas-comprados")
    public List<ProductoComprasRankingDTO> productosMasComprados(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "10") int limit) {
        return compraService.productosMasComprados(desde, hasta, limit);
    }
}
