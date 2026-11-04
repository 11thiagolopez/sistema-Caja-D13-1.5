package com.thiago.escenasFX.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.thiago.escenasFX.dto.ClienteRequest;
import com.thiago.escenasFX.dto.ClienteResponse;
import com.thiago.escenasFX.dto.CuentaCorrienteClienteResponse;
import com.thiago.escenasFX.dto.MovimientoCuentaCorrienteClienteResponse;
import com.thiago.escenasFX.dto.PagoClienteRequest;
import com.thiago.escenasFX.dto.VentaResponse;
import com.thiago.escenasFX.model.Cliente;
import com.thiago.escenasFX.model.MovimientoCuentaCorrienteCliente;
import com.thiago.escenasFX.repository.EmpleadoRepository;
import com.thiago.escenasFX.service.AfipPadronService;
import com.thiago.escenasFX.service.ClienteService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

    private final ClienteService clienteService;
    private final AfipPadronService afipPadronService;
    private final VentaConFacturacion ventaConFacturacion;
    private final EmpleadoRepository empleadoRepo;

    public ClienteController(ClienteService clienteService, AfipPadronService afipPadronService,
            VentaConFacturacion ventaConFacturacion, EmpleadoRepository empleadoRepo) {
        this.clienteService = clienteService;
        this.afipPadronService = afipPadronService;
        this.ventaConFacturacion = ventaConFacturacion;
        this.empleadoRepo = empleadoRepo;
    }

    @GetMapping("/padron/{cuit}")
    public AfipPadronService.Contribuyente consultarPadron(@PathVariable String cuit) {
        return afipPadronService.consultar(cuit);
    }

    @GetMapping
    public List<ClienteResponse> listar() {
        Map<Integer, BigDecimal> saldos = clienteService.saldosPorCliente();
        return clienteService.listar().stream()
            .map(c -> toResponse(c, saldos.getOrDefault(c.getIdCliente(), BigDecimal.ZERO)))
            .toList();
    }

    @GetMapping("/buscar")
    public List<ClienteResponse> buscar(@RequestParam String q) {
        return clienteService.buscar(q).stream().map(c -> toResponse(c, null)).toList();
    }

    @GetMapping("/{id}")
    public ClienteResponse obtener(@PathVariable Integer id) {
        return toResponse(clienteService.obtenerPorId(id), clienteService.calcularSaldo(id));
    }

    @GetMapping("/{id}/saldo")
    public Map<String, BigDecimal> saldo(@PathVariable Integer id) {
        clienteService.obtenerPorId(id);
        return Map.of("saldo", clienteService.calcularSaldo(id));
    }

    @GetMapping("/{id}/ventas")
    public List<VentaResponse> ventas(@PathVariable Integer id) {
        clienteService.obtenerPorId(id);
        return ventaConFacturacion.responder(clienteService.ventas(id));
    }

    @PostMapping
    public ResponseEntity<ClienteResponse> crear(@Valid @RequestBody ClienteRequest request) {
        Cliente cliente = clienteService.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(cliente, null));
    }

    @PutMapping("/{id}")
    public ClienteResponse actualizar(@PathVariable Integer id, @Valid @RequestBody ClienteRequest request) {
        return toResponse(clienteService.actualizar(id, request), clienteService.calcularSaldo(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        clienteService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/cuenta-corriente")
    public CuentaCorrienteClienteResponse cuentaCorriente(@PathVariable Integer id) {
        Cliente cliente = clienteService.obtenerPorId(id);
        List<MovimientoCuentaCorrienteClienteResponse> movimientos = clienteService.movimientos(id).stream()
            .map(this::toMovimientoResponse)
            .toList();
        return new CuentaCorrienteClienteResponse(cliente.getIdCliente(), cliente.getNombre(),
            clienteService.calcularSaldo(id), movimientos);
    }

    @PostMapping("/{id}/pagos")
    public ResponseEntity<MovimientoCuentaCorrienteClienteResponse> registrarPago(@PathVariable Integer id,
            @Valid @RequestBody PagoClienteRequest request, Authentication authentication) {
        MovimientoCuentaCorrienteCliente pago = clienteService.registrarPago(id, request.getMonto(),
            request.getMedioPago(), request.getDescripcion(), RolAutenticado.empleado(authentication, empleadoRepo));
        return ResponseEntity.status(HttpStatus.CREATED).body(toMovimientoResponse(pago));
    }

    private ClienteResponse toResponse(Cliente c, BigDecimal saldo) {
        return new ClienteResponse(c.getIdCliente(), c.getNombre(), c.getDocTipo(), c.getDocNro(),
            c.getCondicionIva(), c.getTelefono(), c.getEmail(), c.getDireccion(), c.isActivo(), saldo);
    }

    private MovimientoCuentaCorrienteClienteResponse toMovimientoResponse(MovimientoCuentaCorrienteCliente m) {
        return new MovimientoCuentaCorrienteClienteResponse(
            m.getIdMovimiento(),
            m.getFecha(),
            m.getTipo(),
            m.getMonto(),
            m.getDescripcion(),
            m.getNotaCredito() != null ? m.getNotaCredito().getIdNotaCredito() : null,
            m.getVentaAplicacion() != null ? m.getVentaAplicacion().getIdVenta() : null,
            m.getMedioPago());
    }
}
