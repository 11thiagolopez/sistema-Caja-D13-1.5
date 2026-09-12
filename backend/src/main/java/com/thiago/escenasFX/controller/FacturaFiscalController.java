package com.thiago.escenasFX.controller;
import com.thiago.escenasFX.service.EmailService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.thiago.escenasFX.dto.EnviarComprobanteRequest;
import com.thiago.escenasFX.dto.FacturaFiscalResponse;
import com.thiago.escenasFX.dto.FacturarVentaRequest;
import com.thiago.escenasFX.model.FacturaFiscal;
import com.thiago.escenasFX.service.FacturaFiscalService;
import com.thiago.escenasFX.service.FacturaPdfService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/ventas/{idVenta}/factura")
public class FacturaFiscalController {

    private final FacturaFiscalService facturaFiscalService;
    private final FacturaPdfService facturaPdfService;
    private final EmailService emailService; 

    public FacturaFiscalController(FacturaFiscalService facturaFiscalService, FacturaPdfService facturaPdfService, EmailService emailService) {
        this.facturaFiscalService = facturaFiscalService;
        this.facturaPdfService = facturaPdfService;
        this.emailService = emailService;
    }

    @PostMapping
    public FacturaFiscalResponse facturar(@PathVariable Integer idVenta, @Valid @RequestBody FacturarVentaRequest request) {
        FacturaFiscal factura = facturaFiscalService.facturar(idVenta, request.getClienteDocTipo(),
            request.getClienteDocNro(), request.getClienteNombre());
        return toResponse(factura);
    }

    @GetMapping
    public ResponseEntity<FacturaFiscalResponse> obtener(@PathVariable Integer idVenta) {
        return facturaFiscalService.obtenerPorVenta(idVenta)
            .map(f -> ResponseEntity.ok(toResponse(f)))
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    // Sin try/catch propio: "Factura no encontrada" es un IllegalArgumentException (400, como el
    // resto de la API) y cualquier falla generando el PDF la atrapa el handler genérico de
    // GlobalExceptionHandler — antes esto devolvía un 500 vacío sin loguear nada útil.
    @GetMapping("/pdf")
    public ResponseEntity<byte[]> descargarPdf(@PathVariable Integer idVenta) throws Exception {
        FacturaFiscal factura = facturaFiscalService.obtenerPorVenta(idVenta)
            .orElseThrow(() -> new IllegalArgumentException("Factura no encontrada para la venta: " + idVenta));

        // Generamos el PDF (el servicio ya carga el logo automático y usa pdfService.generarPdf)
        byte[] pdfBytes = facturaPdfService.generarPdf(factura);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("inline", "factura_D13_" + factura.getNumero() + ".pdf");

        return ResponseEntity.ok().headers(headers).body(pdfBytes);
    }
    
    private FacturaFiscalResponse toResponse(FacturaFiscal f) {
        return new FacturaFiscalResponse(f.getIdFactura(), f.getVenta().getIdVenta(), f.getPuntoVenta(),
            f.getTipoComprobante(), f.getNumero(), f.getClienteDocTipo(), f.getClienteDocNro(), f.getClienteNombre(),
            f.getCae(), f.getCaeVencimiento(), f.getImporte(), f.getEstado(), f.getErrorDetalle());
    }
    // Antes tomaba el email como @RequestParam sin validar (a diferencia de
    // VentaController.enviarComprobante, la operación equivalente para el comprobante interno,
    // que ya usaba este mismo DTO con @Email) — una dirección mal formada llegaba directo a
    // Resend. Mismo motivo que en descargarPdf para sacar el try/catch propio.
    @PostMapping("/enviar-email")
    public ResponseEntity<Void> enviarFacturaPorEmail(@PathVariable Integer idVenta,
            @Valid @RequestBody EnviarComprobanteRequest request) throws Exception {
        FacturaFiscal factura = facturaFiscalService.obtenerPorVenta(idVenta)
            .orElseThrow(() -> new IllegalArgumentException("Factura no encontrada para la venta: " + idVenta));

        byte[] pdfBytes = facturaPdfService.generarPdf(factura);

        String asunto = "Factura Electrónica Nro: " + factura.getPuntoVenta() + "-" + factura.getNumero();
        String cuerpo = "Adjuntamos la factura electrónica correspondiente a su compra. Gracias por elegir Distribuidora D13.";
        String nombreArchivo = "Factura_D13_" + factura.getNumero() + ".pdf";

        emailService.enviarConAdjuntoPdf(request.getEmail(), asunto, cuerpo, nombreArchivo, pdfBytes);

        return ResponseEntity.noContent().build();
    }
}