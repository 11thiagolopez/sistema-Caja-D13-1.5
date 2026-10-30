package com.thiago.escenasFX.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.thiago.escenasFX.dto.CuentaCorrienteProveedorResponse;
import com.thiago.escenasFX.dto.DetalleDevolucionProveedorResponse;
import com.thiago.escenasFX.dto.DevolucionProveedorRequest;
import com.thiago.escenasFX.dto.DevolucionProveedorResponse;
import com.thiago.escenasFX.dto.MovimientoCuentaCorrienteProveedorResponse;
import com.thiago.escenasFX.dto.ProveedorRequest;
import com.thiago.escenasFX.dto.ProveedorResponse;
import com.thiago.escenasFX.model.DetalleDevolucionProveedor;
import com.thiago.escenasFX.model.DevolucionProveedor;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.MovimientoCuentaCorrienteProveedor;
import com.thiago.escenasFX.model.Proveedor;
import com.thiago.escenasFX.repository.EmpleadoRepository;
import com.thiago.escenasFX.service.CompraService;
import com.thiago.escenasFX.service.ProveedorService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/proveedores")
public class ProveedorController {

    private final ProveedorService proveedorService;
    private final CompraService compraService;
    private final EmpleadoRepository empleadoRepo;

    public ProveedorController(ProveedorService proveedorService, CompraService compraService,
            EmpleadoRepository empleadoRepo) {
        this.proveedorService = proveedorService;
        this.compraService = compraService;
        this.empleadoRepo = empleadoRepo;
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

    @GetMapping("/{id}/cuenta-corriente")
    public CuentaCorrienteProveedorResponse cuentaCorriente(@PathVariable Integer id) {
        Proveedor proveedor = proveedorService.obtenerPorId(id);
        List<MovimientoCuentaCorrienteProveedorResponse> movimientos = compraService.movimientosProveedor(id)
            .stream()
            .map(this::toMovimientoResponse)
            .toList();
        return new CuentaCorrienteProveedorResponse(proveedor.getIdProveedor(), proveedor.getNombre(),
            compraService.calcularSaldoProveedor(id), movimientos);
    }

    @PostMapping("/devoluciones")
    public ResponseEntity<DevolucionProveedorResponse> registrarDevolucion(
            @Valid @RequestBody DevolucionProveedorRequest request) {
        Empleado empleado = empleadoRepo.findById(request.getIdEmpleado())
            .orElseThrow(() -> new IllegalArgumentException("Empleado no existe: " + request.getIdEmpleado()));
        DevolucionProveedor devolucion = compraService.registrarDevolucion(request, empleado);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(devolucion));
    }

    @GetMapping("/devoluciones")
    public List<DevolucionProveedorResponse> listarDevoluciones(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return compraService.listarDevolucionesPorRango(desde.atStartOfDay(), hasta.atTime(23, 59, 59)).stream()
            .map(this::toResponse)
            .toList();
    }

    private ProveedorResponse toResponse(Proveedor p) {
        return new ProveedorResponse(p.getIdProveedor(), p.getNombre(), p.getContacto(), p.getTelefono(),
            p.getEmail(), p.isActivo());
    }

    private MovimientoCuentaCorrienteProveedorResponse toMovimientoResponse(MovimientoCuentaCorrienteProveedor m) {
        return new MovimientoCuentaCorrienteProveedorResponse(
            m.getIdMovimiento(),
            m.getFecha(),
            m.getTipo(),
            m.getMonto(),
            m.getMedioPago(),
            m.getDescripcion(),
            m.getCompra() != null ? m.getCompra().getIdCompra() : null,
            m.getDevolucion() != null ? m.getDevolucion().getIdDevolucion() : null);
    }

    private DevolucionProveedorResponse toResponse(DevolucionProveedor d) {
        List<DetalleDevolucionProveedorResponse> detalles = d.getDetalles().stream()
            .map(this::toResponse)
            .toList();
        return new DevolucionProveedorResponse(
            d.getIdDevolucion(),
            d.getProveedor().getIdProveedor(),
            d.getProveedor().getNombre(),
            d.getCompraOriginal() != null ? d.getCompraOriginal().getIdCompra() : null,
            d.getFecha(),
            d.getMotivo(),
            d.getMontoTotal(),
            detalles);
    }

    private DetalleDevolucionProveedorResponse toResponse(DetalleDevolucionProveedor d) {
        return new DetalleDevolucionProveedorResponse(
            d.getIdDetalle(),
            d.getProducto().getIdProducto(),
            d.getProducto().getDescripcion(),
            d.getCantidad(),
            d.getPrecioUnitario(),
            d.getSubtotal());
    }
}
