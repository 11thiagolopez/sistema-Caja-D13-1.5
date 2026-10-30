package com.thiago.escenasFX.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.thiago.escenasFX.model.FacturaFiscal;
import com.thiago.escenasFX.model.NotaCredito;
import com.thiago.escenasFX.model.Venta;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class FacturaPdfService {

    private final PdfService pdfService; // Inyectamos tu servicio existente de logos

    // Mismo CUIT que usa AfipFacturacionService para autenticar y pedir el CAE (afip.cuit): antes
    // este servicio tenía su propia constante hardcodeada con un CUIT distinto y equivocado, así
    // que el QR de ARCA impreso en la factura no coincidía con el emisor real ante AFIP.
    private final String cuitEmisor;

    public FacturaPdfService(PdfService pdfService, @Value("${afip.cuit}") String cuitEmisor) {
        this.pdfService = pdfService;
        this.cuitEmisor = cuitEmisor;
    }

    public byte[] generarPdf(FacturaFiscal factura) throws Exception {
        if (!"EMITIDA".equals(factura.getEstado()) || factura.getCae() == null) {
            throw new IllegalStateException("La factura no está emitida o no tiene CAE.");
        }

        Venta venta = factura.getVenta();
        List<FacturaFiscalHtmlBuilder.Linea> items = venta.getDetalles().stream()
            .map(d -> new FacturaFiscalHtmlBuilder.Linea(
                d.getProducto().getDescripcion(),
                d.getCantidad(),
                d.getPrecioUnitario(),
                d.getSubtotal()
            )).collect(Collectors.toList());

        String titulo = String.format("Factura Nro: %04d-%08d", factura.getPuntoVenta(), factura.getNumero());

        return generarPdfComun(titulo, items, factura.getImporte(), factura.getPuntoVenta(), factura.getNumero(),
            factura.getTipoComprobante(), factura.getClienteDocTipo(), factura.getClienteDocNro(),
            factura.getClienteNombre(), factura.getCae(), factura.getCaeVencimiento(),
            venta.getFecha().toLocalDate());
    }

    /**
     * Mismo armado que generarPdf(FacturaFiscal), pero para una Nota de Crédito C ya emitida ante
     * ARCA (NotaCreditoFiscalService). Los datos del cliente (CUIT/DNI/nombre) no se guardan en
     * NotaCredito — se toman de la FacturaFiscal de la venta original, que es el documento que
     * legalmente identifica al cliente ante ARCA; facturaOriginal se lo pasa quien llama
     * (NotaCreditoController), que ya la necesita para validar que la nota es emitible.
     */
    public byte[] generarPdfNotaCredito(NotaCredito notaCredito, FacturaFiscal facturaOriginal) throws Exception {
        if (!"EMITIDA".equals(notaCredito.getEstado()) || notaCredito.getCae() == null) {
            throw new IllegalStateException("La nota de crédito no está emitida ante ARCA o no tiene CAE.");
        }

        List<FacturaFiscalHtmlBuilder.Linea> items = notaCredito.getDetalles().stream()
            .map(d -> new FacturaFiscalHtmlBuilder.Linea(d.getDescripcion(), d.getCantidad(), d.getPrecioUnitario(),
                d.getSubtotal()))
            .collect(Collectors.toList());

        String titulo = String.format("Nota de Crédito Nro: %04d-%08d", notaCredito.getPuntoVenta(),
            notaCredito.getNumero());

        return generarPdfComun(titulo, items, notaCredito.getMontoTotal(), notaCredito.getPuntoVenta(),
            notaCredito.getNumero(), notaCredito.getTipoComprobante(), facturaOriginal.getClienteDocTipo(),
            facturaOriginal.getClienteDocNro(), facturaOriginal.getClienteNombre(), notaCredito.getCae(),
            notaCredito.getCaeVencimiento(), notaCredito.getFecha().toLocalDate());
    }

    private byte[] generarPdfComun(String titulo, List<FacturaFiscalHtmlBuilder.Linea> items, BigDecimal importe,
            int puntoVenta, int numero, int tipoComprobante, int clienteDocTipo, String clienteDocNro,
            String clienteNombre, String cae, LocalDate caeVencimiento, LocalDate fecha) throws Exception {
        // Con CUIT/DNI se imprime el nombre/razón social tal como cualquier factura real de ARCA
        // (WSFEv1 no lo pide ni lo devuelve, lo carga el ADMIN al facturar). Consumidor Final no
        // tiene nombre asociado.
        String docNroStr = clienteDocNro != null ? clienteDocNro : "0";
        List<String> infoCliente;
        if (clienteDocTipo == 99) {
            infoCliente = List.of("Cliente: Consumidor Final");
        } else {
            String etiquetaDoc = clienteDocTipo == 80 ? "CUIT" : "DNI";
            infoCliente = List.of(
                "Apellido y Nombre / Razón Social: " + clienteNombre,
                etiquetaDoc + ": " + docNroStr
            );
        }

        String qrImageBase64 = generarQrBase64(puntoVenta, tipoComprobante, numero, importe, clienteDocTipo,
            docNroStr, cae, fecha.toString());
        String logoBase64 = pdfService.logoDataUri();

        String html = FacturaFiscalHtmlBuilder.construir(
            titulo, "C", infoCliente, items, importe, logoBase64,
            cae, caeVencimiento.toString(), qrImageBase64, formatearCuit(cuitEmisor)
        );

        return pdfService.generarPdf(html);
    }

    private String generarQrBase64(int puntoVenta, int tipoComprobante, int numero, BigDecimal importe,
            int clienteDocTipo, String docNroStr, String cae, String fechaStr) throws Exception {
        String jsonQr = String.format(
            "{\"ver\":1,\"fecha\":\"%s\",\"cuit\":%s,\"ptoVta\":%d,\"tipoCmp\":%d,\"nroCmp\":%d,\"importe\":%s,\"moneda\":\"PES\",\"ctz\":1,\"tipoDocRec\":%d,\"nroDocRec\":%s,\"tipoCodAut\":\"E\",\"codAut\":%s}",
            fechaStr, cuitEmisor, puntoVenta, tipoComprobante, numero, importe.toString(), clienteDocTipo,
            docNroStr, cae
        );

        String qrCodificado = Base64.getEncoder().encodeToString(jsonQr.getBytes());
        String urlAfip = "https://www.afip.gob.ar/fe/qr/?p=" + qrCodificado;

        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(urlAfip, BarcodeFormat.QR_CODE, 150, 150);
        ByteArrayOutputStream pngOut = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOut);

        return "data:image/png;base64," + Base64.getEncoder().encodeToString(pngOut.toByteArray());
    }

    // "20300238379" -> "20-30023837-9", formato en el que se muestra el CUIT en el encabezado
    // impreso de la factura. Si viene con una longitud inesperada, se devuelve tal cual en vez de
    // romper la generación del PDF.
    private String formatearCuit(String cuit) {
        if (cuit == null || cuit.length() != 11) {
            return cuit;
        }
        return cuit.substring(0, 2) + "-" + cuit.substring(2, 10) + "-" + cuit.substring(10);
    }
}
