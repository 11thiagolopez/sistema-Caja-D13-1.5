package com.thiago.escenasFX.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;

import com.thiago.escenasFX.dto.CobrarTrabajoRequest;
import com.thiago.escenasFX.dto.DetalleVentaRequest;
import com.thiago.escenasFX.dto.EnviarComprobanteRequest;
import com.thiago.escenasFX.dto.FotoTrabajoResponse;
import com.thiago.escenasFX.dto.NotaTrabajoRequest;
import com.thiago.escenasFX.dto.NotaTrabajoResponse;
import com.thiago.escenasFX.dto.TrabajoDomicilioRequest;
import com.thiago.escenasFX.dto.UrlArchivoResponse;
import com.thiago.escenasFX.dto.VentaRequest;
import com.thiago.escenasFX.dto.VentaResponse;
import com.thiago.escenasFX.dto.ArchivoTrabajoResponse;
import com.thiago.escenasFX.model.Cliente;
import com.thiago.escenasFX.model.DetalleVenta;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.NotaTrabajo;
import com.thiago.escenasFX.model.Producto;
import com.thiago.escenasFX.model.Venta;
import com.thiago.escenasFX.repository.ClienteRepository;
import com.thiago.escenasFX.repository.EmpleadoRepository;
import com.thiago.escenasFX.repository.NotaTrabajoRepository;
import com.thiago.escenasFX.repository.ProductoRepository;
import com.thiago.escenasFX.repository.VentaRepository;
import com.thiago.escenasFX.service.FotoTrabajoService;
import com.thiago.escenasFX.service.HistorialClienteService;
import com.thiago.escenasFX.service.NotaTrabajoJefeService;
import com.thiago.escenasFX.service.PushService;
import com.thiago.escenasFX.service.VentaService;
import com.thiago.escenasFX.service.ArchivoTrabajoService;
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
    private final VentaConFacturacion ventaConFacturacion;
    private final NotaTrabajoRepository notaTrabajoRepo;
    private final FotoTrabajoService fotoTrabajoService;
    private final NotaTrabajoJefeService notaTrabajoJefeService;
    private final HistorialClienteService historialClienteService;
    private final ArchivoTrabajoService archivoTrabajoService;

    public VentaController(VentaService ventaService, VentaRepository ventaRepo,
            EmpleadoRepository empleadoRepo, ProductoRepository productoRepo, ClienteRepository clienteRepo,
            PushService pushService, VentaConFacturacion ventaConFacturacion, NotaTrabajoRepository notaTrabajoRepo,
            FotoTrabajoService fotoTrabajoService, NotaTrabajoJefeService notaTrabajoJefeService,
            HistorialClienteService historialClienteService, ArchivoTrabajoService archivoTrabajoService) {
        this.historialClienteService = historialClienteService;
        this.archivoTrabajoService = archivoTrabajoService;
        this.fotoTrabajoService = fotoTrabajoService;
        this.notaTrabajoJefeService = notaTrabajoJefeService;
        this.pushService = pushService;
        this.ventaConFacturacion = ventaConFacturacion;
        this.ventaService = ventaService;
        this.ventaRepo = ventaRepo;
        this.empleadoRepo = empleadoRepo;
        this.productoRepo = productoRepo;
        this.clienteRepo = clienteRepo;
        this.notaTrabajoRepo = notaTrabajoRepo;
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
        return ventaConFacturacion.responder(guardada);
    }

    @GetMapping
    public List<VentaResponse> listarPorRango(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String estadoFactura) {
        if (estadoFactura != null && !VentaMapper.ESTADO_FACTURADA.equals(estadoFactura)
                && !VentaMapper.ESTADO_SIN_FACTURAR.equals(estadoFactura)) {
            throw new IllegalArgumentException("Estado de facturación inválido: " + estadoFactura);
        }
        List<Venta> ventas = ventaRepo.findByFechaBetween(ZonaNegocio.inicioDelDia(desde), ZonaNegocio.finDelDia(hasta));
        return ventaConFacturacion.responder(ventas).stream()
            .filter(v -> estadoFactura == null || estadoFactura.equals(v.getEstadoFactura()))
            .toList();
    }

    @PostMapping("/{id}/enviar-comprobante")
    public VentaResponse enviarComprobante(@PathVariable Integer id,
            @Valid @RequestBody EnviarComprobanteRequest request) {
        Venta venta = ventaService.enviarComprobantePorEmail(id, request.getEmail());
        return ventaConFacturacion.responder(venta);
    }

    @GetMapping("/{id}")
    public VentaResponse obtener(@PathVariable Integer id) {
        return ventaConFacturacion.responder(ventaService.obtenerPorId(id));
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> descargarPdf(@PathVariable Integer id, Authentication authentication) {
        if (!RolAutenticado.esAdmin(authentication)) {
            RolAutenticado.exigirVentaPropiaConfirmada(ventaService.obtenerPorId(id), authentication);
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

    /** Orden de trabajo en PDF de 58mm para la Xprinter (ADMIN, por el matcher de trabajo-domicilio). */
    @GetMapping("/trabajo-domicilio/{id}/orden")
    public ResponseEntity<byte[]> descargarOrdenTrabajo(@PathVariable Integer id) {
        byte[] pdf = ventaService.generarOrdenTrabajoPdf(id);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.inline().filename("orden-trabajo-" + id + ".pdf").build().toString())
            .body(pdf);
    }

    // Trabajos anteriores del mismo teléfono o dirección, para mostrarlos al cargar uno (Etapa 3).
    @GetMapping("/trabajo-domicilio/historial-cliente")
    public List<VentaResponse> historialDelCliente(@RequestParam(required = false) String telefono,
            @RequestParam(required = false) String direccion, @RequestParam(required = false) Integer excluir) {
        return historialClienteService.buscar(telefono, direccion, excluir).stream().map(VentaMapper::toResponse).toList();
    }

    @GetMapping("/trabajo-domicilio/{id}/notas")
    public List<NotaTrabajoResponse> notasDelTrabajo(@PathVariable Integer id) {
        return notaTrabajoRepo.findByVentaIdVentaOrderByCreadoEnAsc(id).stream().map(this::toResponse).toList();
    }

    // Nota del jefe: misma lista que las del técnico; le avisa al técnico asignado.
    @PostMapping("/trabajo-domicilio/{id}/notas")
    public ResponseEntity<NotaTrabajoResponse> agregarNota(@PathVariable Integer id,
            @Valid @RequestBody NotaTrabajoRequest request, Authentication authentication) {
        NotaTrabajo nota = notaTrabajoJefeService.agregar(id, request.getTexto(),
            RolAutenticado.empleado(authentication, empleadoRepo));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(nota));
    }

    // Fotos del trabajo (ADMIN, por el matcher de trabajo-domicilio). Las mismas que ve el técnico.
    @GetMapping("/trabajo-domicilio/{id}/fotos")
    public List<FotoTrabajoResponse> fotosDelTrabajo(@PathVariable Integer id) {
        return fotoTrabajoService.listar(id);
    }

    @PostMapping("/trabajo-domicilio/{id}/fotos")
    public ResponseEntity<List<FotoTrabajoResponse>> subirFotos(@PathVariable Integer id,
            @RequestParam("fotos") List<MultipartFile> fotos, Authentication authentication) {
        Venta venta = ventaService.obtenerTrabajo(id);
        Empleado admin = RolAutenticado.empleado(authentication, empleadoRepo);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(fotoTrabajoService.subir(venta, admin, FotosHttp.leer(fotos), true, null));
    }

    @GetMapping("/trabajo-domicilio/{id}/fotos/{idFoto}")
    public ResponseEntity<byte[]> verFoto(@PathVariable Integer id, @PathVariable Integer idFoto) {
        return FotosHttp.responder(fotoTrabajoService.obtener(id, idFoto));
    }

    @DeleteMapping("/trabajo-domicilio/{id}/fotos/{idFoto}")
    public ResponseEntity<Void> borrarFoto(@PathVariable Integer id, @PathVariable Integer idFoto,
            Authentication authentication) {
        fotoTrabajoService.borrar(id, idFoto, RolAutenticado.empleado(authentication, empleadoRepo), true);
        return ResponseEntity.noContent().build();
    }

    // Videos y audios adjuntos (ADMIN): mismos permisos que las fotos; el archivo vive en el bucket.
    @GetMapping("/trabajo-domicilio/{id}/archivos")
    public List<ArchivoTrabajoResponse> archivosDelTrabajo(@PathVariable Integer id) {
        return archivoTrabajoService.listar(id);
    }

    @PostMapping("/trabajo-domicilio/{id}/archivos")
    public ResponseEntity<List<ArchivoTrabajoResponse>> subirArchivo(@PathVariable Integer id,
            @RequestParam("archivo") MultipartFile archivo, Authentication authentication) {
        Venta venta = ventaService.obtenerTrabajo(id);
        Empleado admin = RolAutenticado.empleado(authentication, empleadoRepo);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(archivoTrabajoService.subir(venta, admin, archivo, archivo.getSize(), true, null));
    }

    @GetMapping("/trabajo-domicilio/{id}/archivos/{idArchivo}/url")
    public UrlArchivoResponse urlArchivo(@PathVariable Integer id, @PathVariable Integer idArchivo) {
        return new UrlArchivoResponse(archivoTrabajoService.urlParaVer(id, idArchivo));
    }

    @DeleteMapping("/trabajo-domicilio/{id}/archivos/{idArchivo}")
    public ResponseEntity<Void> borrarArchivo(@PathVariable Integer id, @PathVariable Integer idArchivo,
            Authentication authentication) {
        archivoTrabajoService.borrar(id, idArchivo, RolAutenticado.empleado(authentication, empleadoRepo), true);
        return ResponseEntity.noContent().build();
    }

    private NotaTrabajoResponse toResponse(NotaTrabajo nota) {
        return new NotaTrabajoResponse(nota.getIdNota(), nota.getTexto(), nota.getEmpleado().getNombre(),
            nota.getCreadoEn());
    }

    // Un trabajo que el jefe marcó Terminado queda bloqueado: solo se cobra (con los ítems tal cual)
    // o se reabre. Ver VentaService.cobrarTrabajoTerminado / reabrirTrabajo.
    @PostMapping("/trabajo-domicilio/{id}/cobrar")
    public VentaResponse cobrarTrabajoTerminado(@PathVariable Integer id,
            @Valid @RequestBody CobrarTrabajoRequest request, Authentication authentication) {
        return VentaMapper.toResponse(ventaService.cobrarTrabajoTerminado(id, request.medioPago(),
            RolAutenticado.empleado(authentication, empleadoRepo)));
    }

    @PostMapping("/trabajo-domicilio/{id}/reabrir")
    public VentaResponse reabrirTrabajo(@PathVariable Integer id, Authentication authentication) {
        return VentaMapper.toResponse(
            ventaService.reabrirTrabajo(id, RolAutenticado.empleado(authentication, empleadoRepo)));
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
