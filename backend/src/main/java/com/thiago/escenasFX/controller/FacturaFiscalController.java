package com.thiago.escenasFX.controller;

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

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/ventas/{idVenta}/factura")
public class FacturaFiscalController {

    private final FacturaFiscalService facturaFiscalService;

    public FacturaFiscalController(FacturaFiscalService facturaFiscalService) {
        this.facturaFiscalService = facturaFiscalService;
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

    private FacturaFiscalResponse toResponse(FacturaFiscal f) {
        return new FacturaFiscalResponse(f.getIdFactura(), f.getVenta().getIdVenta(), f.getPuntoVenta(),
            f.getTipoComprobante(), f.getNumero(), f.getClienteDocTipo(), f.getClienteDocNro(), f.getCae(),
            f.getCaeVencimiento(), f.getImporte(), f.getEstado(), f.getErrorDetalle());
    }
}
