package com.thiago.escenasFX.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

/**
 * Renderiza el XHTML armado por {@link ComprobanteHtmlBuilder} a PDF. El logo se lee una sola vez
 * del classpath y se cachea como data URI base64 — a diferencia del email (que usa un adjunto
 * "cid:"), el renderer de PDF no entiende referencias cid, necesita el binario embebido inline.
 */
@Service
public class PdfService {

    private final String logoDataUri;

    public PdfService() {
        this.logoDataUri = cargarLogoBase64();
    }

    private String cargarLogoBase64() {
        try (InputStream in = new ClassPathResource("static/logo-d13.png").getInputStream()) {
            byte[] bytes = in.readAllBytes();
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo cargar el logo del local para el PDF", e);
        }
    }

    String logoDataUri() {
        return logoDataUri;
    }

    byte[] generarPdf(String html) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el PDF", e);
        }
    }
}
