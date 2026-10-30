package com.thiago.escenasFX.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.thiago.escenasFX.dto.DetalleNotaCreditoResponse;
import com.thiago.escenasFX.dto.NotaCreditoRequest;
import com.thiago.escenasFX.dto.NotaCreditoResponse;
import com.thiago.escenasFX.model.DetalleNotaCredito;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.FacturaFiscal;
import com.thiago.escenasFX.model.NotaCredito;
import com.thiago.escenasFX.repository.EmpleadoRepository;
import com.thiago.escenasFX.repository.FacturaFiscalRepository;
import com.thiago.escenasFX.service.FacturaPdfService;
import com.thiago.escenasFX.service.NotaCreditoFiscalService;
import com.thiago.escenasFX.service.NotaCreditoService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/notas-credito")
public class NotaCreditoController {

    private final NotaCreditoService notaCreditoService;
    private final NotaCreditoFiscalService notaCreditoFiscalService;
    private final EmpleadoRepository empleadoRepo;
    private final FacturaFiscalRepository facturaFiscalRepo;
    private final FacturaPdfService facturaPdfService;

    public NotaCreditoController(NotaCreditoService notaCreditoService,
            NotaCreditoFiscalService notaCreditoFiscalService, EmpleadoRepository empleadoRepo,
            FacturaFiscalRepository facturaFiscalRepo, FacturaPdfService facturaPdfService) {
        this.notaCreditoService = notaCreditoService;
        this.notaCreditoFiscalService = notaCreditoFiscalService;
        this.empleadoRepo = empleadoRepo;
        this.facturaFiscalRepo = facturaFiscalRepo;
        this.facturaPdfService = facturaPdfService;
    }

    @PostMapping
    public ResponseEntity<NotaCreditoResponse> crear(@Valid @RequestBody NotaCreditoRequest request) {
        Empleado empleado = empleadoRepo.findById(request.getIdEmpleado())
            .orElseThrow(() -> new IllegalArgumentException("Empleado no existe: " + request.getIdEmpleado()));
        NotaCredito nc = notaCreditoService.crear(request, empleado);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(nc));
    }

    @GetMapping
    public List<NotaCreditoResponse> listar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return notaCreditoService.listarPorRango(desde.atStartOfDay(), hasta.atTime(23, 59, 59)).stream()
            .map(this::toResponse)
            .toList();
    }

    @GetMapping("/{id}")
    public NotaCreditoResponse obtener(@PathVariable Integer id) {
        return toResponse(notaCreditoService.obtenerPorId(id));
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> descargarPdf(@PathVariable Integer id) {
        byte[] pdf = notaCreditoService.generarPdf(id);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename("nota-credito-" + id + ".pdf").build().toString())
            .body(pdf);
    }

    @PostMapping("/{id}/emitir-fiscal")
    public NotaCreditoResponse emitirFiscal(@PathVariable Integer id) {
        return toResponse(notaCreditoFiscalService.emitir(id));
    }

    // Sin try/catch propio: cualquier falla generando el PDF la atrapa el handler genérico de
    // GlobalExceptionHandler, mismo criterio que FacturaFiscalController.descargarPdf.
    @GetMapping("/{id}/factura-pdf")
    public ResponseEntity<byte[]> descargarFacturaPdf(@PathVariable Integer id) throws Exception {
        NotaCredito nc = notaCreditoService.obtenerPorId(id);
        FacturaFiscal facturaOriginal = facturaFiscalRepo.findByVentaIdVenta(nc.getVentaOriginal().getIdVenta())
            .orElseThrow(() -> new IllegalArgumentException(
                "La venta original no tiene factura fiscal — esta nota de crédito es interna"));

        byte[] pdf = facturaPdfService.generarPdfNotaCredito(nc, facturaOriginal);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename("nota-credito-fiscal-" + id + ".pdf").build().toString())
            .body(pdf);
    }

    private NotaCreditoResponse toResponse(NotaCredito nc) {
        List<DetalleNotaCreditoResponse> detalles = nc.getDetalles().stream()
            .map(this::toDetalleResponse)
            .toList();
        return new NotaCreditoResponse(
            nc.getIdNotaCredito(),
            nc.getVentaOriginal().getIdVenta(),
            nc.getCliente().getIdCliente(),
            nc.getCliente().getNombre(),
            nc.getFecha(),
            nc.getMotivo(),
            nc.getAlcance(),
            nc.getMontoTotal(),
            nc.isEsFiscal(),
            nc.getEstado(),
            nc.getPuntoVenta(),
            nc.getTipoComprobante(),
            nc.getNumero(),
            nc.getCae(),
            nc.getCaeVencimiento(),
            nc.getErrorDetalle(),
            detalles);
    }

    private DetalleNotaCreditoResponse toDetalleResponse(DetalleNotaCredito d) {
        return new DetalleNotaCreditoResponse(
            d.getIdDetalle(),
            d.getProducto() != null ? d.getProducto().getIdProducto() : null,
            d.getDescripcion(),
            d.getCantidad(),
            d.getPrecioUnitario(),
            d.getSubtotal());
    }
}
