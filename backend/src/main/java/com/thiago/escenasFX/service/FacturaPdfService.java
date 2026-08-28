package com.thiago.escenasFX.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.thiago.escenasFX.model.FacturaFiscal;
import com.thiago.escenasFX.model.Venta;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class FacturaPdfService {

    private static final String CUIT_EMISOR = "30123456789"; 

    private final PdfService pdfService; // Inyectamos tu servicio existente de logos

    public FacturaPdfService(PdfService pdfService) {
        this.pdfService = pdfService;
    }

    public byte[] generarPdf(FacturaFiscal factura) throws Exception {
        if (!"EMITIDA".equals(factura.getEstado()) || factura.getCae() == null) {
            throw new IllegalStateException("La factura no está emitida o no tiene CAE.");
        }

        Venta venta = factura.getVenta();

        // 1. Mapear los detalles de la venta
        List<FacturaFiscalHtmlBuilder.Linea> items = venta.getDetalles().stream()
            .map(d -> new FacturaFiscalHtmlBuilder.Linea(
                d.getProducto().getDescripcion(), 
                d.getCantidad(),
                d.getPrecioUnitario(),
                d.getSubtotal()
            )).collect(Collectors.toList());

        // 2. Preparar los datos del cliente
        String docTipoStr = factura.getClienteDocTipo() == 99 ? "Consumidor Final" : "DNI/CUIT";
        String docNroStr = factura.getClienteDocNro() != null ? factura.getClienteDocNro() : "0";
        List<String> infoCliente = List.of(
            "Cliente: " + docTipoStr,
            "Documento: " + (docNroStr.equals("0") ? "No especifica" : docNroStr)
        );

        // 3. Generar la imagen del Código QR de ARCA
        String qrImageBase64 = generarQrBase64(factura, venta.getFecha().toLocalDate().toString(), docNroStr);

        // 4. Obtener el logo automáticamente desde tu PdfService existente
        String logoBase64 = pdfService.logoDataUri();

        // 5. Armar el título formal
        String titulo = String.format("Factura Nro: %04d-%08d", factura.getPuntoVenta(), factura.getNumero());

        // 6. Construir el HTML pasándole el logo automático
        String html = FacturaFiscalHtmlBuilder.construir(
            titulo, "C", infoCliente, items, factura.getImporte(), logoBase64, 
            factura.getCae(), factura.getCaeVencimiento().toString(), qrImageBase64
        );

        // 7. Renderizar a PDF (podés usar el método de tu PdfService o dejar el PdfRendererBuilder directo)
        return pdfService.generarPdf(html);
    }

    private String generarQrBase64(FacturaFiscal factura, String fechaStr, String docNroStr) throws Exception {
        String jsonQr = String.format(
            "{\"ver\":1,\"fecha\":\"%s\",\"cuit\":%s,\"ptoVta\":%d,\"tipoCmp\":%d,\"nroCmp\":%d,\"importe\":%s,\"moneda\":\"PES\",\"ctz\":1,\"tipoDocRec\":%d,\"nroDocRec\":%s,\"tipoCodAut\":\"E\",\"codAut\":%s}",
            fechaStr, CUIT_EMISOR, factura.getPuntoVenta(), 
            factura.getTipoComprobante(), factura.getNumero(), factura.getImporte().toString(),
            factura.getClienteDocTipo(), docNroStr, factura.getCae()
        );
        
        String qrCodificado = Base64.getEncoder().encodeToString(jsonQr.getBytes());
        String urlAfip = "https://www.afip.gob.ar/fe/qr/?p=" + qrCodificado;

        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(urlAfip, BarcodeFormat.QR_CODE, 150, 150);
        ByteArrayOutputStream pngOut = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOut);
        
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(pngOut.toByteArray());
    }
}