package com.thiago.escenasFX.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thiago.escenasFX.model.DetalleNotaCredito;
import com.thiago.escenasFX.model.DetalleVenta;
import com.thiago.escenasFX.model.FacturaFiscal;
import com.thiago.escenasFX.model.NotaCredito;
import com.thiago.escenasFX.repository.FacturaFiscalRepository;
import com.thiago.escenasFX.repository.NotaCreditoRepository;

/**
 * Emite ante ARCA (WSFEv1) una nota de crédito que ya existe internamente (NotaCreditoService) y
 * cuya venta original tiene una FacturaFiscal EMITIDA — acción aparte, igual espíritu que
 * FacturaFiscalService.facturar(): si ARCA rechaza o falla la conexión, la NotaCredito queda en
 * ERROR (reintentable llamando de nuevo), nunca se toca la Venta ni la FacturaFiscal original.
 *
 * Nota de Crédito C = tipo de comprobante AFIP 13 (confirmado contra el manual oficial de
 * WSFEv1: "12: Nota de Débito C, 13: Nota de Crédito C"). El comprobante asociado (CbtesAsoc) es
 * obligatorio en la práctica para que la nota tenga sentido: "Para 12 o 13 pueden asociarse 11,
 * 12, 13 y 15" — 11 es Factura C, exactamente lo que emite FacturaFiscalService.
 */
@Service
public class NotaCreditoFiscalService {

    private static final int TIPO_NOTA_CREDITO_C = 13;

    private final NotaCreditoRepository notaCreditoRepo;
    private final FacturaFiscalRepository facturaFiscalRepo;
    private final AfipFacturacionService afipFacturacionService;
    private final int puntoVenta;

    public NotaCreditoFiscalService(NotaCreditoRepository notaCreditoRepo,
            FacturaFiscalRepository facturaFiscalRepo, AfipFacturacionService afipFacturacionService,
            @Value("${afip.punto-venta}") int puntoVenta) {
        this.notaCreditoRepo = notaCreditoRepo;
        this.facturaFiscalRepo = facturaFiscalRepo;
        this.afipFacturacionService = afipFacturacionService;
        this.puntoVenta = puntoVenta;
    }

    @Transactional
    public NotaCredito emitir(Integer idNotaCredito) {
        NotaCredito nc = notaCreditoRepo.findById(idNotaCredito)
            .orElseThrow(() -> new IllegalArgumentException("Nota de crédito no existe: " + idNotaCredito));

        if (!nc.isEsFiscal()) {
            throw new IllegalStateException(
                "La nota de crédito #" + idNotaCredito + " es interna (la venta original no tiene factura "
                    + "fiscal emitida) — no corresponde emitirla ante ARCA");
        }
        if ("EMITIDA".equals(nc.getEstado())) {
            throw new IllegalStateException("La nota de crédito #" + idNotaCredito + " ya fue emitida ante ARCA");
        }

        FacturaFiscal facturaOriginal = facturaFiscalRepo.findByVentaIdVenta(nc.getVentaOriginal().getIdVenta())
            .filter(f -> "EMITIDA".equals(f.getEstado()))
            .orElseThrow(() -> new IllegalStateException(
                "La venta original #" + nc.getVentaOriginal().getIdVenta()
                    + " no tiene una factura fiscal EMITIDA"));

        List<DetalleVenta> detallesOriginales = nc.getDetalles().stream()
            .map(DetalleNotaCredito::getDetalleVentaOriginal)
            .toList();
        int concepto = FacturaFiscalService.concepto(detallesOriginales);
        LocalDate fecha = nc.getFecha().toLocalDate();

        nc.setPuntoVenta(puntoVenta);
        nc.setTipoComprobante(TIPO_NOTA_CREDITO_C);
        nc.setErrorDetalle(null);

        Integer clienteDocTipo = facturaOriginal.getClienteDocTipo();
        String clienteDocNro = clienteDocTipo == 99 ? "0" : facturaOriginal.getClienteDocNro();

        AfipFacturacionService.DatosFactura datos = new AfipFacturacionService.DatosFactura(
            puntoVenta, TIPO_NOTA_CREDITO_C, concepto, clienteDocTipo, clienteDocNro,
            nc.getMontoTotal(), fecha,
            concepto != 1 ? fecha : null, concepto != 1 ? fecha : null, concepto != 1 ? fecha : null,
            facturaOriginal.getTipoComprobante(), facturaOriginal.getPuntoVenta(), facturaOriginal.getNumero());

        try {
            AfipFacturacionService.ResultadoCae resultado = afipFacturacionService.emitirFacturaC(datos);
            if (resultado.aprobado()) {
                nc.setNumero(resultado.numero());
                nc.setCae(resultado.cae());
                nc.setCaeVencimiento(resultado.caeVencimiento());
                nc.setEstado("EMITIDA");
            } else {
                nc.setEstado("ERROR");
                nc.setErrorDetalle(resultado.detalle());
            }
        } catch (RuntimeException e) {
            nc.setEstado("ERROR");
            nc.setErrorDetalle(e.getMessage());
        }

        return notaCreditoRepo.save(nc);
    }
}
