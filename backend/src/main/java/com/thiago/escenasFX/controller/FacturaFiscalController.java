package com.thiago.escenasFX.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    public FacturaFiscalController(FacturaFiscalService facturaFiscalService, FacturaPdfService facturaPdfService) {
        this.facturaFiscalService = facturaFiscalService;
        this.facturaPdfService = facturaPdfService;
    }

    @PostMapping
    public FacturaFiscalResponse facturar(@PathVariable Integer idVenta, @Valid @RequestBody FacturarVentaRequest request) {
        FacturaFiscal factura = facturaFiscalService.facturar(idVenta, request.getClienteDocTipo(), request.getClienteDocNro());
        return toResponse(factura);
    }

    @GetMapping
    public ResponseEntity<FacturaFiscalResponse> obtener(@PathVariable Integer idVenta) {
        return facturaFiscalService.obtenerPorVenta(idVenta)
            .map(f -> ResponseEntity.ok(toResponse(f)))
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/pdf")
    public ResponseEntity<byte[]> descargarPdf(@PathVariable Integer idVenta) {
        try {
            FacturaFiscal factura = facturaFiscalService.obtenerPorVenta(idVenta)
                .orElseThrow(() -> new RuntimeException("Factura no encontrada para la venta: " + idVenta));

            // Generamos el PDF (el servicio ya carga el logo automático y usa pdfService.generarPdf)
            byte[] pdfBytes = facturaPdfService.generarPdf(factura);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("inline", "factura_D13_" + factura.getNumero() + ".pdf");
            
            return ResponseEntity.ok().headers(headers).body(pdfBytes);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
    
    private FacturaFiscalResponse toResponse(FacturaFiscal f) {
        return new FacturaFiscalResponse(f.getIdFactura(), f.getVenta().getIdVenta(), f.getPuntoVenta(),
            f.getTipoComprobante(), f.getNumero(), f.getClienteDocTipo(), f.getClienteDocNro(), f.getCae(),
            f.getCaeVencimiento(), f.getImporte(), f.getEstado(), f.getErrorDetalle());
    }
}