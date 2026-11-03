package com.thiago.escenasFX.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.thiago.escenasFX.dto.DetalleVentaRequest;
import com.thiago.escenasFX.dto.EnviarComprobanteRequest;
import com.thiago.escenasFX.dto.TrabajoDomicilioRequest;
import com.thiago.escenasFX.dto.VentaRequest;
import com.thiago.escenasFX.dto.VentaResponse;
import com.thiago.escenasFX.model.Cliente;
import com.thiago.escenasFX.model.DetalleVenta;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.Producto;
import com.thiago.escenasFX.model.Venta;
import com.thiago.escenasFX.repository.ClienteRepository;
import com.thiago.escenasFX.repository.EmpleadoRepository;
import com.thiago.escenasFX.repository.ProductoRepository;
import com.thiago.escenasFX.repository.VentaRepository;
import com.thiago.escenasFX.service.PushService;
import com.thiago.escenasFX.service.VentaService;
import com.thiago.escenasFX.service.ZonaNegocio;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/ventas")
public class VentaController {

    private final VentaService ventaService;
    private final VentaRepository ventaRepo;
    private final EmpleadoRepository empleadoRepo;
    private final ProductoRepository productoRepo;
    private final ClienteRepository clienteRepo;
    private final PushService pushService;

    public VentaController(VentaService ventaService, VentaRepository ventaRepo,
            EmpleadoRepository empleadoRepo, ProductoRepository productoRepo, ClienteRepository clienteRepo,
            PushService pushService) {
        this.pushService = pushService;
        this.ventaService = ventaService;
        this.ventaRepo = ventaRepo;
        this.empleadoRepo = empleadoRepo;
        this.productoRepo = productoRepo;
        this.clienteRepo = clienteRepo;
    }

    @PostMapping
    public VentaResponse registrar(@Valid @RequestBody VentaRequest request, Authentication authentication) {
        Empleado empleado = RolAutenticado.empleadoQueOpera(authentication, request.getIdEmpleado(), empleadoRepo);

        Venta venta = new Venta();
        venta.setEmpleado(empleado);
        venta.setMedioPago(request.getMedioPago());
        venta.setTipoComprobante(request.getTipoComprobante());
        venta.setDescuento(request.getDescuento());
        venta.setMotivoDescuento(request.getMotivoDescuento());
        if (request.getIdCliente() != null) {
            // Referencia liviana, igual patrón que Producto: VentaService hace el findById/lock
            // real cuando corresponde (al aplicar saldo), esto solo evita un SELECT de más.
            Cliente clienteRef = clienteRepo.getReferenceById(request.getIdCliente());
            venta.setCliente(clienteRef);
        }
        venta.setMontoSaldoAplicado(request.getMontoSaldoAplicado());

        for (DetalleVentaRequest detalleReq : request.getDetalles()) {
            DetalleVenta detalle = new DetalleVenta();
            detalle.setCantidad(detalleReq.getCantidad());
            detalle.setPrecioUnitario(detalleReq.getPrecioUnitario());
            detalle.setTipo(detalleReq.getTipo() != null ? detalleReq.getTipo() : "ARTICULO");

            if (detalleReq.getIdProducto() != null) {
                // Referencia liviana (sin SELECT): VentaService ya hace el findById real para
                // validar existencia y stock.
                Producto productoRef = productoRepo.getReferenceById(detalleReq.getIdProducto());
                detalle.setProducto(productoRef);
            } else {
                if (detalleReq.getDescripcion() == null || detalleReq.getDescripcion().isBlank()) {
                    throw new IllegalArgumentException(
                        "Cada línea de la venta necesita un producto o una descripción manual");
                }
                detalle.setDescripcion(detalleReq.getDescripcion());
            }

            venta.getDetalles().add(detalle);
        }

        Venta guardada = ventaService.registrarVenta(venta, RolAutenticado.esAdmin(authentication));
        if ("PENDIENTE_AUTORIZACION".equals(guardada.getEstado())) {
            pushService.avisarPeticionNuevaEnSegundoPlano();
        }
        return VentaMapper.toResponse(guardada);
    }

    @GetMapping
    public List<VentaResponse> listarPorRango(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        List<Venta> ventas = ventaRepo.findByFechaBetween(ZonaNegocio.inicioDelDia(desde), ZonaNegocio.finDelDia(hasta));
        return ventas.stream().map(VentaMapper::toResponse).toList();
    }

    @PostMapping("/{id}/enviar-comprobante")
    public VentaResponse enviarComprobante(@PathVariable Integer id,
            @Valid @RequestBody EnviarComprobanteRequest request) {
        Venta venta = ventaService.enviarComprobantePorEmail(id, request.getEmail());
        return VentaMapper.toResponse(venta);
    }

    @GetMapping("/{id}")
    public VentaResponse obtener(@PathVariable Integer id) {
        return VentaMapper.toResponse(ventaService.obtenerPorId(id));
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> descargarPdf(@PathVariable Integer id, Authentication authentication) {
        // El VENDEDOR imprime lo que acaba de cobrar, pero nada más: si pudiera pedir cualquier id,
        // este endpoint le abriría el historial de ventas que no debe ver (ver SecurityConfig).
        if (!RolAutenticado.esAdmin(authentication)) {
            Venta venta = ventaService.obtenerPorId(id);
            boolean esPropia = venta.getEmpleado() != null
                && authentication.getName().equals(venta.getEmpleado().getUsuario());
            if (!esPropia || !"CONFIRMADA".equals(venta.getEstado())) {
                throw new AccessDeniedException("Solo podés ver el comprobante de tus ventas confirmadas");
            }
        }

        byte[] pdf = ventaService.generarPdf(id);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename("comprobante-" + id + ".pdf").build().toString())
            .body(pdf);
    }

    @GetMapping("/trabajo-domicilio")
    public List<VentaResponse> agendaDeTrabajos(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ventaRepo.findByTipoVentaAndEstadoNotAndFechaProgramadaBetweenOrderByFechaProgramadaAscIdVentaAsc(
                "DOMICILIO", "ANULADA", desde, hasta)
            .stream().map(VentaMapper::toResponse).toList();
    }

    @PostMapping("/trabajo-domicilio/{id}/anular")
    public VentaResponse anularTrabajoDomicilio(@PathVariable Integer id, Authentication authentication) {
        return VentaMapper.toResponse(
            ventaService.anularTrabajoDomicilio(id, RolAutenticado.empleado(authentication, empleadoRepo)));
    }

    @PostMapping("/trabajo-domicilio")
    public VentaResponse crearTrabajoDomicilio(@Valid @RequestBody TrabajoDomicilioRequest request) {
        return guardarTrabajoDomicilio(request);
    }

    @PutMapping("/trabajo-domicilio/{id}")
    public VentaResponse actualizarTrabajoDomicilio(@PathVariable Integer id,
            @Valid @RequestBody TrabajoDomicilioRequest request) {
        request.setIdVenta(id);
        return guardarTrabajoDomicilio(request);
    }

    private VentaResponse guardarTrabajoDomicilio(TrabajoDomicilioRequest request) {
        Empleado empleado = empleadoRepo.findById(request.getIdEmpleado())
            .orElseThrow(() -> new IllegalArgumentException("Empleado no existe: " + request.getIdEmpleado()));
        Venta venta = ventaService.guardarTrabajoDomicilio(request, empleado);
        return VentaMapper.toResponse(venta);
    }
}
