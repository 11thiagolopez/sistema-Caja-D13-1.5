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

import com.thiago.escenasFX.dto.ConfirmarDescuentoRequest;
import com.thiago.escenasFX.dto.DetalleVentaRequest;
import com.thiago.escenasFX.dto.VentaRequest;
import com.thiago.escenasFX.dto.VentaResponse;
import com.thiago.escenasFX.model.DetalleVenta;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.Producto;
import com.thiago.escenasFX.model.Venta;
import com.thiago.escenasFX.repository.EmpleadoRepository;
import com.thiago.escenasFX.repository.ProductoRepository;
import com.thiago.escenasFX.repository.VentaRepository;
import com.thiago.escenasFX.service.VentaService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/ventas")
public class VentaController {

    private final VentaService ventaService;
    private final VentaRepository ventaRepo;
    private final EmpleadoRepository empleadoRepo;
    private final ProductoRepository productoRepo;

    public VentaController(VentaService ventaService, VentaRepository ventaRepo,
            EmpleadoRepository empleadoRepo, ProductoRepository productoRepo) {
        this.ventaService = ventaService;
        this.ventaRepo = ventaRepo;
        this.empleadoRepo = empleadoRepo;
        this.productoRepo = productoRepo;
    }

    @PostMapping
    public VentaResponse registrar(@Valid @RequestBody VentaRequest request) {
        Empleado empleado = empleadoRepo.findById(request.getIdEmpleado())
            .orElseThrow(() -> new IllegalArgumentException("Empleado no existe: " + request.getIdEmpleado()));

        Venta venta = new Venta();
        venta.setEmpleado(empleado);
        venta.setMedioPago(request.getMedioPago());
        venta.setTipoComprobante(request.getTipoComprobante());
        venta.setDescuento(request.getDescuento());
        venta.setMotivoDescuento(request.getMotivoDescuento());

        for (DetalleVentaRequest detalleReq : request.getDetalles()) {
            // Referencia liviana (sin SELECT): VentaService ya hace el findById real para
            // validar existencia y stock.
            Producto productoRef = productoRepo.getReferenceById(detalleReq.getIdProducto());

            DetalleVenta detalle = new DetalleVenta();
            detalle.setProducto(productoRef);
            detalle.setCantidad(detalleReq.getCantidad());
            detalle.setPrecioUnitario(detalleReq.getPrecioUnitario());
            venta.getDetalles().add(detalle);
        }

        Venta guardada = ventaService.registrarVenta(venta);
        return VentaMapper.toResponse(guardada);
    }

    @PostMapping("/descuento/confirmar")
    public VentaResponse confirmarDescuento(@Valid @RequestBody ConfirmarDescuentoRequest request) {
        Venta venta = ventaService.confirmarDescuento(request.getIdVenta(), request.getCodigo());
        return VentaMapper.toResponse(venta);
    }

    @GetMapping
    public List<VentaResponse> listarPorRango(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        List<Venta> ventas = ventaRepo.findByFechaBetween(desde.atStartOfDay(), hasta.atTime(23, 59, 59));
        return ventas.stream().map(VentaMapper::toResponse).toList();
    }
}
