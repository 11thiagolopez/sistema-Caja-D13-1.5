package com.thiago.escenasFX.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Arma el XHTML branded (logo, dirección, teléfono del local) que comparten los PDF/emails de
 * Presupuestos y de comprobantes de venta. openhtmltopdf exige XHTML válido, así que todo texto
 * dinámico pasa por {@link #escapeXml(String)} antes de insertarse — un nombre de cliente o una
 * descripción con "&" o "<" rompería el parser si no se escapa.
 */
final class ComprobanteHtmlBuilder {

    private static final String NOMBRE_LOCAL = "D13 Distribuidora";
    private static final String DIRECCION_LOCAL = "Arce 790, CABA";
    private static final String TELEFONO_LOCAL = "1123752626";

    private ComprobanteHtmlBuilder() {
    }

    record Linea(String descripcion, int cantidad, BigDecimal precioUnitario, BigDecimal subtotal) {
    }

    static String construir(String titulo, List<String> infoLineas, List<Linea> items, BigDecimal total,
            String notaFinal, String logoSrc) {
        StringBuilder filas = new StringBuilder();
        for (Linea l : items) {
            filas.append("<tr>")
                .append("<td style='padding:6px;border:1px solid #ccc;'>").append(l.cantidad()).append("</td>")
                .append("<td style='padding:6px;border:1px solid #ccc;'>").append(escapeXml(l.descripcion()))
                .append("</td>")
                .append("<td style='padding:6px;border:1px solid #ccc;'>$").append(l.precioUnitario()).append("</td>")
                .append("<td style='padding:6px;border:1px solid #ccc;'>$").append(l.subtotal()).append("</td>")
                .append("</tr>");
        }

        StringBuilder info = new StringBuilder();
        for (String linea : infoLineas) {
            info.append("<p style='margin:2px 0;'>").append(escapeXml(linea)).append("</p>");
        }

        return "<html xmlns='http://www.w3.org/1999/xhtml'><head><meta charset='UTF-8'/></head>"
            + "<body style='font-family:Arial,sans-serif;'>"
            + "<div style='max-width:600px;'>"
            + "<img src='" + logoSrc + "' alt='" + escapeXml(NOMBRE_LOCAL) + "' style='height:80px;' />"
            + "<p style='margin:4px 0;color:#555;'>" + escapeXml(DIRECCION_LOCAL) + " — Tel: "
            + escapeXml(TELEFONO_LOCAL) + "</p>"
            + "<hr/>"
            + "<p><strong>" + escapeXml(titulo) + "</strong></p>"
            + info
            + "<table style='width:100%;border-collapse:collapse;margin-top:8px;'>"
            + "<thead><tr>"
            + "<th style='padding:6px;border:1px solid #ccc;'>Cantidad</th>"
            + "<th style='padding:6px;border:1px solid #ccc;'>Descripción</th>"
            + "<th style='padding:6px;border:1px solid #ccc;'>Precio unitario</th>"
            + "<th style='padding:6px;border:1px solid #ccc;'>Subtotal</th>"
            + "</tr></thead>"
            + "<tbody>" + filas + "</tbody>"
            + "</table>"
            + "<p style='margin-top:12px;'><strong>Total: $" + total + "</strong></p>"
            + "<p style='color:#777;font-size:0.9em;'>" + escapeXml(notaFinal) + "</p>"
            + "</div></body></html>";
    }

    private static String escapeXml(String texto) {
        if (texto == null) {
            return "";
        }
        return texto
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }
}
